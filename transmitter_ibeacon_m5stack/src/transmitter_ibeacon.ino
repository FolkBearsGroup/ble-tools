#if defined(TARGET_M5STACK)
#include <M5Stack.h>
#elif defined(TARGET_M5STICKCPLUS)
#include <M5StickCPlus.h>
#else
#error "Target board macro not set. Define TARGET_M5STACK or TARGET_M5STICKCPLUS via build_flags."
#endif
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>


// iBeacon payload
// "90FA7ABE-FAB6-485E-B700-1A17804CAA13"
const uint8_t IBEACON_UUID[16] = {
  0x90, 0xFA, 0x7A, 0xBE,
  0xFA, 0xB6,
  0x48, 0x5E,
  0xB7, 0x00,
  0x1A, 0x17, 0x80, 0x4C, 0xAA, 0x13
};
uint16_t ibeacon_major = 0x0001;
uint16_t ibeacon_minor = 0x0001;
const int8_t IBEACON_TX_POWER = -59; // 1m での RSSI


// BLE関連
BLEAdvertising *advertising;
// アドバタイズデータ作成
void set_advertising_data();

/**
 * @brief BLE初期化
 */
void ble_init() {
  // BLEの初期化
  BLEDevice::init("ibeacon");  // デバイス名を設定
  set_advertising_data();
}

/**
 * @brief アドバタイズ送信設定
 * 
 */

void set_advertising_data()
{
    // アドバタイズ送信パワー変更（-3dBm ~ 10dBm：デフォルト0dBm）
    esp_power_level_t dbm = ESP_PWR_LVL_N0;
    esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_ADV, dbm);


    // major, minor にランダム値を入れる
    ibeacon_major = random(0x0001, 0xFFFE);
    ibeacon_minor = random(0x0001, 0xFFFE);

    // アドバタイズデータ作成（iBeacon 仕様）
    BLEAdvertisementData advertisementData;
    // Flags
    advertisementData.setFlags(0x1A);
    // Manufacturer Specific Data: Apple ID + iBeacon frame
    std::string manufacturerData;
    manufacturerData.reserve(25);
    manufacturerData.push_back(0x4C);               // Apple Company ID (LE)
    manufacturerData.push_back(0x00);
    manufacturerData.push_back(0x02);               // iBeacon type
    manufacturerData.push_back(0x15);               // remaining length
    for (uint8_t b : IBEACON_UUID) {
      manufacturerData.push_back(static_cast<char>(b));
    }
    manufacturerData.push_back((ibeacon_major >> 8) & 0xFF);
    manufacturerData.push_back(ibeacon_major & 0xFF);
    manufacturerData.push_back((ibeacon_minor >> 8) & 0xFF);
    manufacturerData.push_back(ibeacon_minor & 0xFF);
    manufacturerData.push_back(static_cast<char>(IBEACON_TX_POWER));
    advertisementData.setManufacturerData(manufacturerData);

    // アドバタイズの設定
    advertising = BLEDevice::getAdvertising();
    advertising->setAdvertisementType(ADV_TYPE_NONCONN_IND); // iBeacon は非接続
    advertising->setScanResponse(false);
    advertising->setAdvertisementData(advertisementData);
    // アドバタイズ間隔を 100 ms に設定
    advertising->setMinInterval(0x00A0); // 約100 ms
    advertising->setMaxInterval(0x00A0); // 約100 ms
    // advertising->setMinInterval(0x00A0*10); // 約1000 ms
    // advertising->setMaxInterval(0x00A0*10); // 約1000 ms
}
/**
 * @brief 初期化
 *
 */
void setup() {
  M5.begin();
  // 動作周波数を80MHzにする（BLEが使用できる最低の周波数）
  // setCpuFrequencyMhz(80);
  // デバッグ用
  Serial.begin(115200);
  Serial.println("M5StickC Plus BLE iBeacon Transmitter Start");
  // BLE初期化
  ble_init();
  // アドバタイズ開始
  advertising->start();
}

/**
 * @brief メインループ
 *
 */
int count = 0;
void loop() {
  M5.update();
  // 画面表示
  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setTextColor(WHITE);
  M5.Lcd.setCursor(0, 0);
  M5.Lcd.printf("iBeacon Transmitter\n");
  M5.Lcd.printf("UUID: 90FA7ABE-FAB6-485E-B700-1A17804CAA13\n");
  M5.Lcd.printf("Major: %04X\n", ibeacon_major);
  M5.Lcd.printf("Minor: %04X\n", ibeacon_minor);
  M5.Lcd.printf("Count: %d\n", count++);
  sleep(1);

}
