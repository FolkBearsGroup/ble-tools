#if defined(TARGET_M5STACK)
#include <M5Stack.h>
#elif defined(TARGET_M5STICKCPLUS)
#include <M5StickCPlus.h>
#else
#error "Target board macro not set. Define TARGET_M5STACK or TARGET_M5STICKCPLUS via build_flags."
#endif
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <cstdio>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <cstring>

#ifndef WIFI_SSID
#define WIFI_SSID "tokumaru"
#endif
#ifndef WIFI_PASSWORD
#define WIFI_PASSWORD "marenijr0525"
#endif
#ifndef UDP_TARGET_IP
#define UDP_TARGET_IP "192.168.1.4"
#endif
#ifndef UDP_TARGET_PORT
#define UDP_TARGET_PORT 5000
#endif

// iBeacon payload
// "90FA7ABE-FAB6-485E-B700-1A17804CAA13"
const uint8_t IBEACON_UUID[16] = {
  0x90, 0xFA, 0x7A, 0xBE,
  0xFA, 0xB6,
  0x48, 0x5E,
  0xB7, 0x00,
  0x1A, 0x17, 0x80, 0x4C, 0xAA, 0x13
};


struct IBeaconInfo {
  bool seen = false;
  std::string uuid;
  uint16_t major = 0;
  uint16_t minor = 0;
  int8_t txPower = 0;
  int rssi = 0;
  unsigned long lastSeenMs = 0;
};

IBeaconInfo lastBeacon;
BLEScan *scanner;
bool wifiConnected = false;
String wifiIp;
WiFiUDP udp;
unsigned long lastUdpSentMs = 0;
const unsigned long udpIntervalMs = 1000;

std::string format_uuid(const uint8_t *u) {
  char buf[37];
  std::snprintf(buf, sizeof(buf),
                "%02X%02X%02X%02X-%02X%02X-%02X%02X-%02X%02X-%02X%02X%02X%02X%02X%02X",
                u[0], u[1], u[2], u[3],
                u[4], u[5],
                u[6], u[7],
                u[8], u[9],
                u[10], u[11], u[12], u[13], u[14], u[15]);
  return std::string(buf);
}

bool parse_ibeacon(const std::string &md, IBeaconInfo &out) {
  if (md.size() < 25) {
    return false;
  }
  const uint8_t *p = reinterpret_cast<const uint8_t *>(md.data());
  if (p[0] != 0x4C || p[1] != 0x00 || p[2] != 0x02 || p[3] != 0x15) {
    return false;
  }
  out.uuid = format_uuid(p + 4);
  out.major = (p[20] << 8) | p[21];
  out.minor = (p[22] << 8) | p[23];
  out.txPower = static_cast<int8_t>(p[24]);
  out.lastSeenMs = millis();
  return true;
}

class IBeaconCallbacks : public BLEAdvertisedDeviceCallbacks {
 public:
  void onResult(BLEAdvertisedDevice advertisedDevice) override {
    if (!advertisedDevice.haveManufacturerData()) {
      return;
    }
    IBeaconInfo info;
    if (!parse_ibeacon(advertisedDevice.getManufacturerData(), info)) {
      return;
    }

    // info.uuid を IBEACON_UUID と比較して、違う UUID のビーコンは無視する
    if (info.uuid != format_uuid(IBEACON_UUID)) {
      return;
    }
    info.seen = true;
    info.rssi = advertisedDevice.getRSSI();
    lastBeacon = info;
  }
};

void sendUdpLog() {
  if (!wifiConnected || !lastBeacon.seen) {
    return;
  }

  const unsigned long now = millis();
  if (now - lastUdpSentMs < udpIntervalMs) {
    return;
  }
  lastUdpSentMs = now;

  char payload[192];
  std::snprintf(payload, sizeof(payload),
                "uuid=%s,major=%u,minor=%u,rssi=%d,tx=%d,ts=%lu",
                lastBeacon.uuid.c_str(), lastBeacon.major, lastBeacon.minor,
                lastBeacon.rssi, lastBeacon.txPower, lastBeacon.lastSeenMs);

  udp.beginPacket(UDP_TARGET_IP, UDP_TARGET_PORT);
  udp.write(reinterpret_cast<const uint8_t *>(payload), std::strlen(payload));
  udp.endPacket();
}

void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  const unsigned long timeoutMs = 15000;  // Avoid blocking forever
  const unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < timeoutMs) {
    delay(250);
  }

  wifiConnected = WiFi.status() == WL_CONNECTED;
  if (wifiConnected) {
    wifiIp = WiFi.localIP().toString();
  } else {
    wifiIp = "(failed)";
  }
}

void setup() {
  M5.begin();
  Serial.begin(115200);
  Serial.println("M5StickC Plus BLE iBeacon Scanner Start");

  Serial.println("Connecting WiFi...");
  connectWifi();
  if (wifiConnected) {
    Serial.printf("WiFi connected: %s\n", wifiIp.c_str());
    udp.begin(0);  // use ephemeral port for outbound logs
  } else {
    Serial.println("WiFi connect failed");
  }

  BLEDevice::init("");
  scanner = BLEDevice::getScan();
  scanner->setAdvertisedDeviceCallbacks(new IBeaconCallbacks());
  scanner->setActiveScan(true);
  scanner->setInterval(0x50);
  scanner->setWindow(0x30);
}

void loop() {
  M5.update();
  scanner->start(1, false);
  scanner->clearResults();

  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setTextColor(WHITE);
  M5.Lcd.setCursor(0, 0);
  M5.Lcd.printf("iBeacon Scanner\n");
  M5.Lcd.printf("WiFi: %s\n", wifiConnected ? "OK" : "NG");
  if (wifiConnected) {
    M5.Lcd.printf("IP: %s\n", wifiIp.c_str());
  }
  if (lastBeacon.seen) {
    M5.Lcd.printf("UUID: %s\n", lastBeacon.uuid.c_str());
    M5.Lcd.printf("Major: %04X\n", lastBeacon.major);
    M5.Lcd.printf("Minor: %04X\n", lastBeacon.minor);
    M5.Lcd.printf("RSSI: %d dBm\n", lastBeacon.rssi);
    M5.Lcd.printf("Tx: %d dBm\n", lastBeacon.txPower);
    sendUdpLog();
  } else {
    M5.Lcd.printf("Scanning...\n");
  }
  delay(200);
}
