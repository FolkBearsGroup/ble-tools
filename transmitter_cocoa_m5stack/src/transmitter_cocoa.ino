#include <M5StickCPlus.h>   // m5stack/M5StickCPlus
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>

#if 1
#define SERVICE_UUID        "FD6F"  // EN API
#define SERVICE_DATA_UUID   "FD6F"  // EN API
#else
#define SERVICE_UUID        "FF00"  // EN API ALT
#define SERVICE_DATA_UUID   "0001"  // EN API ALT
#endif


// BLE関連
BLEAdvertising *advertising;
// アドバタイズデータ作成
void set_advertising_data();

/**
 * @brief BLE初期化
 */
void ble_init() {
  // BLEの初期化
  BLEDevice::init("cocoa");
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
    // Complete 16-bit Service UUID
    // advertisementData.setCompleteServices(BLEUUID("FD6F"));
    advertisementData.setCompleteServices(BLEUUID("FF00")); // EN API ALT
    // Service Data(Service Data 16-bit Service UUID)
    std::string strServiceData = "";
    // Append RPI and AEM
    for (int i = 0; i < sizeof(rpi); i++) {
        strServiceData += rpi[i];
    }
    for (int i = 0; i < sizeof(aem); i++) {
        strServiceData += aem[i];
    } 
    // Service Data 16-bit Service UUID
    // advertisementData.setServiceData(BLEUUID("FD6F"), strServiceData);
    advertisementData.setServiceData(BLEUUID("0001"), strServiceData);  // EN API ALT

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
  Serial.println("M5StickC Plus BLE Transmitter Start");
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
  M5.Lcd.printf("COCOA Transmitter\n");
  M5.Lcd.printf("Count: %d\n", count++);
  sleep(1);
}
