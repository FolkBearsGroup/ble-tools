//
//  GattClient.swift
//  folkbears.mini
//
//  Created by masuda on 2025/07/24.
//

import Foundation
import CoreBluetooth
import Combine
import CoreData

class GattClient: NSObject, ObservableObject {
    private var centralManager: CBCentralManager?
    private var discoveredPeripherals: [CBPeripheral] = []
    private var connectedPeripheral: CBPeripheral?
    private var targetService: CBService?
    private var targetCharacteristic: CBCharacteristic?

    /// ペリフェラル検出イベントを通知（UI側で集計するため）
    var onDiscover: ((DiscoveredPeripheral, Date) -> Void)?
    
    // CoreData Manager
    // private let coreDataManager = CoreDataManager.shared
    
    @Published var isScanning = false
    @Published var scanningStatus = "停止中"
    @Published var bluetoothState = "Unknown"
    @Published var peripherals: [DiscoveredPeripheral] = []
    @Published var connectionStatus = "未接続"
    @Published var receivedData = ""
    @Published var currentMTU: Int = 23
    
    // MTU設定
    private let requestedMTU: Int = 185
    
    // ターゲットサービスとキャラクタリスティックのUUID
    private let targetServiceUUID = CBUUID(string: "90FA7ABE-FAB6-485E-B700-1A17804CAA13")
    private let targetCharacteristicUUID = CBUUID(string: "90FA7ABE-FAB6-485E-B700-1A17804CAA14")
    
    struct DiscoveredPeripheral: Identifiable {
        let id = UUID()
        let peripheral: CBPeripheral
        let name: String
        let rssi: NSNumber
        let advertisementData: [String: Any]

        var identifierString: String { peripheral.identifier.uuidString }
        
        var displayName: String {
            return name.isEmpty ? "Unknown Device" : name
        }
    }
    
    override init() {
        super.init()
        setupCentralManager()
    }
    
    private func setupCentralManager() {
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    func startScanning() {
        guard let centralManager = centralManager,
              centralManager.state == .poweredOn,
              !isScanning else {
            print("Bluetooth が利用できないか、既にスキャン中です")
            return
        }
        
        peripherals.removeAll()
        discoveredPeripherals.removeAll()
        
        // 特定のサービスUUIDでスキャン（nilで全デバイス）
        centralManager.scanForPeripherals(withServices: [targetServiceUUID], options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: false
        ])
        
        isScanning = true
        scanningStatus = "スキャン中..."
        print("GATT クライアント スキャン開始")
    }
    
    func stopScanning() {
        guard let centralManager = centralManager,
              isScanning else { return }
        
        centralManager.stopScan()
        
        isScanning = false
        scanningStatus = "停止中"
        print("GATT クライアント スキャン停止")
    }
    
    func connectToPeripheral(_ discoveredPeripheral: DiscoveredPeripheral) {
        guard let centralManager = centralManager else { return }
        
        let peripheral = discoveredPeripheral.peripheral
        peripheral.delegate = self
        
        centralManager.connect(peripheral, options: nil)
        connectionStatus = "接続中..."
        
        print("ペリフェラルに接続試行: \(discoveredPeripheral.displayName)")
    }
    
    func disconnectFromPeripheral() {
        guard let centralManager = centralManager,
              let peripheral = connectedPeripheral else { return }
        
        centralManager.cancelPeripheralConnection(peripheral)
        print("ペリフェラルから切断")
    }
    
    func writeData(_ data: String) {
        guard let peripheral = connectedPeripheral,
              let characteristic = targetCharacteristic,
              characteristic.properties.contains(.write) else {
            print("書き込み不可能：接続またはキャラクタリスティックが無効")
            return
        }
        
        let writeData = data.data(using: .utf8) ?? Data()
        peripheral.writeValue(writeData, for: characteristic, type: .withResponse)
        
        print("データ送信: \(data)")
    }
    
    // MTU要求メソッド
    func requestMTU(_ size: Int = 185) {
        guard let peripheral = connectedPeripheral else {
            print("MTU要求失敗: ペリフェラルが接続されていません")
            return
        }
        
        // iOSではMTUサイズは自動的にネゴシエーションされる
        // 手動でのMTUサイズ設定はCBPeripheralでは直接サポートされていない
        print("MTU \(size) バイト要求中...")
        print("注意: iOSではMTUは自動的にネゴシエーションされます")
    }
    
    // MTU情報を取得
    func getCurrentMTU() -> Int {
        guard let peripheral = connectedPeripheral else { return 23 }
        
        if #available(iOS 9.0, *) {
            // MTUサイズはcanSendWriteWithoutResponseから推測
            return peripheral.canSendWriteWithoutResponse ? 185 : 23
        }
        return 23
    }
    
    // データ読み取りメソッド
    func readData() {
        guard let peripheral = connectedPeripheral,
              let characteristic = targetCharacteristic,
              characteristic.properties.contains(.read) else {
            print("読み取り不可能：接続またはキャラクタリスティックが無効")
            return
        }
        
        peripheral.readValue(for: characteristic)
        print("データ読み取り要求")
    }
    
    func getClientSummary() -> String {
        return """
        スキャン状態: \(scanningStatus)
        接続状態: \(connectionStatus)
        発見デバイス数: \(peripherals.count)個
        受信データ: \(receivedData.isEmpty ? "なし" : receivedData)
        """
    }
}

// MARK: - CBCentralManagerDelegate
extension GattClient: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        DispatchQueue.main.async {
            switch central.state {
            case .poweredOn:
                self.bluetoothState = "Powered On"
                print("Bluetooth が有効になりました")
            case .poweredOff:
                self.bluetoothState = "Powered Off"
                self.stopScanning()
                self.disconnectFromPeripheral()
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
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        
        // 重複チェック
        if !discoveredPeripherals.contains(where: { $0.identifier == peripheral.identifier }) {
            discoveredPeripherals.append(peripheral)
            
            let name = peripheral.name ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? ""
            
            let discoveredPeripheral = DiscoveredPeripheral(
                peripheral: peripheral,
                name: name,
                rssi: RSSI,
                advertisementData: advertisementData
            )
            
            DispatchQueue.main.async {
                self.peripherals.append(discoveredPeripheral)
                self.scanningStatus = "発見: \(self.peripherals.count)個"
                self.onDiscover?(discoveredPeripheral, Date())
            }
            
            print("ペリフェラル発見: \(discoveredPeripheral.displayName), RSSI: \(RSSI)")
        }
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        DispatchQueue.main.async {
            self.connectedPeripheral = peripheral
            self.connectionStatus = "接続済み"
        }
        
        print("ペリフェラル接続成功: \(peripheral.name ?? "Unknown")")
        
        // MTU要求を実行
        requestMTU(requestedMTU)
        
        // サービス探索開始
        peripheral.discoverServices([targetServiceUUID])
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        DispatchQueue.main.async {
            self.connectionStatus = "接続失敗"
        }
        
        print("ペリフェラル接続失敗: \(error?.localizedDescription ?? "Unknown error")")
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        DispatchQueue.main.async {
            self.connectedPeripheral = nil
            self.connectionStatus = "切断済み"
            self.targetService = nil
            self.targetCharacteristic = nil
        }
        
        print("ペリフェラル切断: \(peripheral.name ?? "Unknown")")
    }
}

// MARK: - CBPeripheralDelegate
extension GattClient: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            print("サービス探索エラー: \(error.localizedDescription)")
            return
        }
        
        guard let services = peripheral.services else { return }
        
        for service in services {
            if service.uuid == targetServiceUUID {
                targetService = service
                print("ターゲットサービス発見: \(service.uuid)")
                peripheral.discoverCharacteristics([targetCharacteristicUUID], for: service)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error = error {
            print("キャラクタリスティック探索エラー: \(error.localizedDescription)")
            return
        }
        
        guard let characteristics = service.characteristics else { return }
        
        for characteristic in characteristics {
            if characteristic.uuid == targetCharacteristicUUID {
                targetCharacteristic = characteristic
                print("ターゲットキャラクタリスティック発見: \(characteristic.uuid)")
                
                // 現在のMTUを更新
                DispatchQueue.main.async {
                    self.currentMTU = self.getCurrentMTU()
                    print("現在のMTU: \(self.currentMTU) バイト")
                }
                
                // 通知を有効にする
                if characteristic.properties.contains(.notify) {
                    peripheral.setNotifyValue(true, for: characteristic)
                    print("通知を有効にしました")
                }
                
                // 初期値を読み取り
                if characteristic.properties.contains(.read) {
                    peripheral.readValue(for: characteristic)
                }
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("値更新エラー: \(error.localizedDescription)")
            return
        }
        
        guard let data = characteristic.value,
              let receivedString = String(data: data, encoding: .utf8) else { return }
        
        // CoreDataにTraceDataを保存（既存のエンティティ構造に合わせて）
        /*
        saveGattDataToCoreData(
            peripheral: peripheral,
            characteristic: characteristic,
            data: data,
            receivedString: receivedString
        )
        */
        DispatchQueue.main.async {
            self.receivedData = receivedString
        }
        
        print("受信データ: \(receivedString)")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("書き込みエラー: \(error.localizedDescription)")
        } else {
            print("書き込み成功")
        }
    }
    
    // MARK: - CoreData保存メソッド
    /*
    private func saveGattDataToCoreData(
        peripheral: CBPeripheral,
        characteristic: CBCharacteristic,
        data: Data,
        receivedString: String
    ) {
        let deviceName = peripheral.name ?? "Unknown Device"
        let deviceId = peripheral.identifier.uuidString
        
        // 既存のTraceDataエンティティの構造に合わせて保存
        // tempIdにデバイス情報とデータを組み合わせて保存
        let tempId = "\(deviceName)_\(deviceId.prefix(8))_\(receivedString)"
        
        // RSSIは接続時の情報から取得（ここでは0として保存）
        let rssi = 0.0
        let txPower = Double(currentMTU)  // MTU情報をtxPowerフィールドに保存
        
        coreDataManager.saveGattTraceData(
            timestamp: Date(),
            tempId: tempId,
            rssi: rssi,
            txPower: txPower
        )
        print("GattTraceData保存完了: \(deviceName) - \(receivedString)")
    }
    
    // 保存されたトレースデータを取得
    func getStoredGattTraceData() -> [TraceDataEntity] {
        return coreDataManager.fetchRecentGattTraceData(limit: 100)
    }
     */
}
