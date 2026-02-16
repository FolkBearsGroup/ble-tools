//
//  GattAdvertise.swift
//  folkbears.mini
//
//  Created by masuda on 2025/07/24.
//

import Foundation
import CoreBluetooth
import Combine

class GattAdvertise: NSObject, ObservableObject {
    private var peripheralManager: CBPeripheralManager?
    private var customService: CBMutableService?
    
    @Published var isAdvertising = false
    @Published var advertisingStatus = "停止中"
    @Published var bluetoothState = "Unknown"
    @Published var connectedCentrals: [CBCentral] = []
    
    // カスタムサービスとキャラクタリスティックのUUID
    private let serviceUUID = CBUUID(string: "90FA7ABE-FAB6-485E-B700-1A17804CAA13")
    private let characteristicUUID = CBUUID(string: "90FA7ABE-FAB6-485E-B700-1A17804CAA14")
    private let deviceName = "FolkBears-GATT"
    
    private var customCharacteristic: CBMutableCharacteristic?
    private var characteristicValue = "Hello GATT World!"
    
    override init() {
        super.init()
        setupPeripheralManager()
    }
    
    private func setupPeripheralManager() {
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }
    
    func startAdvertising() {
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn,
              !isAdvertising else {
            print("Bluetooth が利用できないか、既にアドバタイズ中です")
            return
        }
        
        let advertisementData: [String: Any] = [
            CBAdvertisementDataServiceUUIDsKey: [serviceUUID],
            CBAdvertisementDataLocalNameKey: deviceName
        ]
        
        peripheralManager.startAdvertising(advertisementData)
        
        isAdvertising = true
        advertisingStatus = "アドバタイズ中..."
        print("GATT アドバタイズ開始 - サービス: \(serviceUUID)")
    }
    
    func stopAdvertising() {
        guard let peripheralManager = peripheralManager,
              isAdvertising else { return }
        
        peripheralManager.stopAdvertising()
        peripheralManager.removeAllServices()
        
        isAdvertising = false
        advertisingStatus = "停止中"
        connectedCentrals.removeAll()
        print("GATT アドバタイズ停止")
    }
    
    func getConnectionSummary() -> String {
        return """
        アドバタイズ状態: \(advertisingStatus)
        接続中デバイス数: \(connectedCentrals.count)個
        サービスUUID: \(serviceUUID)
        デバイス名: \(deviceName)
        """
    }
}

// MARK: - CBPeripheralManagerDelegate
extension GattAdvertise: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        DispatchQueue.main.async {
            switch peripheral.state {
            case .poweredOn:
                self.bluetoothState = "Powered On"
                print("Bluetooth が有効になりました")
            case .poweredOff:
                self.bluetoothState = "Powered Off"
                self.stopAdvertising()
                print("Bluetooth が無効です")
            case .resetting:
                self.bluetoothState = "Resetting"
                print("Bluetooth リセット中")
            case .unauthorized:
                self.bluetoothState = "Unauthorized"
                print("Bluetooth 使用権限がありません")
            case .unsupported:
                self.bluetoothState = "Unsupported"
                print("Bluetooth がサポートされていません")
            case .unknown:
                self.bluetoothState = "Unknown"
                print("Bluetooth 状態不明")
            @unknown default:
                self.bluetoothState = "Unknown"
                break
            }
        }
    }
    
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        DispatchQueue.main.async {
            if let error = error {
                print("アドバタイズ開始エラー: \(error.localizedDescription)")
                self.advertisingStatus = "エラー"
                self.isAdvertising = false
            } else {
                print("アドバタイズ開始成功")
                self.advertisingStatus = "アドバタイズ中"
            }
        }
    }
}
