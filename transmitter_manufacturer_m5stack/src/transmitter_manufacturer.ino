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


#define COMPANY_ID        0xFFFF // 実験用


// BLE関連
BLEAdvertising *advertising;
// アドバタイズデータ作成
void set_advertising_data();

/**
 * @brief BLE初期化
 */
void ble_init() {
  // BLEの初期化
  BLEDevice::init("cocoa-manufacturer");  // デバイス名を設定
  set_advertising_data();
}

/**
 * @brief アドバタイズ送信設定
 * 
 */

// Rolling Proximity Identifier
char rpi[16] { 0x21, 0x01, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02, 
               0x03, 0x03, 0x03, 0x03, 0x04, 0x04, 0x04, 0x04 };  
// Asscociated Encrypted Metadata
char aem[4]  { 0x05, 0x05, 0x05, 0x05, };  

void set_advertising_data()
{
    // アドバタイズ送信パワー変更（-3dBm ~ 10dBm：デフォルト0dBm）
    esp_power_level_t dbm = ESP_PWR_LVL_N0;
    esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_ADV, dbm);
      // アドバタイズデータ作成
    BLEAdvertisementData advertisementData;
    // Flags
    advertisementData.setFlags(0x1A);
    // Manufacturer Specific Data: Company ID (little endian) + RPI
    std::string manufacturerData;
    manufacturerData.reserve(2 + sizeof(rpi));
    manufacturerData.push_back(COMPANY_ID & 0xFF);
    manufacturerData.push_back((COMPANY_ID >> 8) & 0xFF);
    manufacturerData.push_back( 0x02 ); // beacon type   ibeacon 風に設定しておく
    manufacturerData.push_back( 0x10 ); // data length
    for (int i = 0; i < sizeof(rpi); i++) {
      manufacturerData += rpi[i];
    }
    advertisementData.setManufacturerData(manufacturerData);

    // アドバタイズの設定
    advertising = BLEDevice::getAdvertising();
    // advertising->setAdvertisementType(ADV_TYPE_NONCONN_IND); // コネクション不要
    // advertising->setScanResponse(false);
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
  Serial.println("M5StickC Plus BLE Manufacturer Transmitter Start");
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
  M5.Lcd.printf("Manufacturer Transmitter\n");
  M5.Lcd.printf("Count: %d\n", count++);
  sleep(1);

}
