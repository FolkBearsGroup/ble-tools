//
//  GattServer.swift
//  folkbears.mini
//
//  Created by masuda on 2025/07/24.
//

import Foundation
import CoreBluetooth
import Combine

class GattServer: NSObject, ObservableObject {
    private var peripheralManager: CBPeripheralManager?
    private var gattService: CBMutableService?
    private var dataCharacteristic: CBMutableCharacteristic?
    private var controlCharacteristic: CBMutableCharacteristic?
    
    @Published var isRunning = false
    @Published var serverStatus = "停止中"
    @Published var bluetoothState = "Unknown"
    @Published var connectedClients: [CBCentral] = []
    @Published var receivedCommands: [String] = []
    @Published var tempUserId: String = "User UUID"
    @Published var serverData: String = {
        let tempUserId = "User UUID"
        return "Server Data: TempUserId=\(tempUserId)"
    }()
    
    // GATTサーバー用UUID
    private let gattServiceUUID = CBUUID(string: "90FA7ABE-FAB6-485E-B700-1A17804CAA13")
    private let dataCharacteristicUUID = CBUUID(string: "90FA7ABE-FAB6-485E-B700-1A17804CAA14")
    private let serverName = "FolkBears-GATT-Server"

    override init() {
        super.init()
        setupPeripheralManager()
    }
    
    private func setupPeripheralManager() {
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }
    
    func startServer() {
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn,
              !isRunning else {
            print("Bluetooth が利用できないか、既にサーバー稼働中です")
            return
        }
        
        setupGattServer()
        startAdvertising()
        
        isRunning = true
        serverStatus = "サーバー稼働中"
        print("GATT サーバー開始")
    }
    
    func stopServer() {
        guard let peripheralManager = peripheralManager,
              isRunning else { return }
        
        peripheralManager.stopAdvertising()
        peripheralManager.removeAllServices()
        
        isRunning = false
        serverStatus = "停止中"
        connectedClients.removeAll()
        print("GATT サーバー停止")
    }
    
    private func setupGattServer() {
        guard let peripheralManager = peripheralManager else { return }
        
        // TempUserIdからデータを生成
        let jsonData = "{\"i\": \"\(tempUserId)\"}"
        
        // データキャラクタリスティック（読み取り・通知）
        dataCharacteristic = CBMutableCharacteristic(
            type: dataCharacteristicUUID,
            properties: [.read],
            value: jsonData.data(using: .utf8),
            permissions: [.readable]
        )
        
        print("📡 GATTサーバー データ設定: \(jsonData)")
        
        // GATTサービス作成
        gattService = CBMutableService(type: gattServiceUUID, primary: true)
        gattService?.characteristics = [dataCharacteristic!]
        
        // サービス追加
        peripheralManager.add(gattService!)
    }
    
    private func startAdvertising() {
        guard let peripheralManager = peripheralManager else { return }
        
        let advertisementData: [String: Any] = [
            CBAdvertisementDataServiceUUIDsKey: [gattServiceUUID],
            CBAdvertisementDataLocalNameKey: serverName
        ]
        
        peripheralManager.startAdvertising(advertisementData)
    }
    
    func updateServerData(_ newData: String) {
        serverData = newData
        broadcastDataUpdate()
        print("サーバーデータ更新: \(newData)")
    }
    
    private func broadcastDataUpdate() {
        guard let peripheralManager = peripheralManager,
              let characteristic = dataCharacteristic,
              !connectedClients.isEmpty else { return }
        
        let data = serverData.data(using: .utf8) ?? Data()
        
        for client in connectedClients {
            let success = peripheralManager.updateValue(
                data,
                for: characteristic,
                onSubscribedCentrals: [client]
            )
            
            if !success {
                print("データ送信失敗（キューが満杯）")
            }
        }
    }

    func processCommand(_ command: String) {
        receivedCommands.append("\(Date().formatted(.dateTime.hour().minute().second())): \(command)")
        
        // コマンド処理の例
        switch command.lowercased() {
        case "ping":
            updateServerData("pong")
        case "status":
            updateServerData("Server OK - Clients: \(connectedClients.count)")
        case "time":
            updateServerData("Time: \(Date().formatted())")
        case "reset":
            receivedCommands.removeAll()
            updateServerData("Commands cleared")
        default:
            updateServerData("Unknown command: \(command)")
        }
        
        print("コマンド処理: \(command)")
    }
    
    func getServerSummary() -> String {
        return """
        サーバー状態: \(serverStatus)
        接続クライアント数: \(connectedClients.count)個
        サーバーデータ: \(serverData)
        受信コマンド数: \(receivedCommands.count)個
        """
    }
    
    func clearCommandHistory() {
        receivedCommands.removeAll()
        print("コマンド履歴をクリア")
    }
}

// MARK: - CBPeripheralManagerDelegate
extension GattServer: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        DispatchQueue.main.async {
            switch peripheral.state {
            case .poweredOn:
                self.bluetoothState = "Powered On"
                print("Bluetooth が有効になりました")
            case .poweredOff:
                self.bluetoothState = "Powered Off"
                self.stopServer()
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
                self.serverStatus = "エラー"
                self.isRunning = false
            } else {
                print("サーバーアドバタイズ開始成功")
                self.serverStatus = "サーバー稼働中"
            }
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        DispatchQueue.main.async {
            if !self.connectedClients.contains(central) {
                self.connectedClients.append(central)
                self.serverStatus = "クライアント接続中(\(self.connectedClients.count))"
            }
            print("クライアント接続: \(central.identifier)")
        }
        
        // 接続時に初期データを送信
        broadcastDataUpdate()
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        DispatchQueue.main.async {
            self.connectedClients.removeAll { $0.identifier == central.identifier }
            self.serverStatus = self.connectedClients.isEmpty ? "稼働中" : "クライアント接続中(\(self.connectedClients.count))"
            print("クライアント切断: \(central.identifier)")
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        guard let characteristic = request.characteristic as? CBMutableCharacteristic else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
            return
        }
        
        if characteristic.uuid == dataCharacteristicUUID {
            // TempUserIdを含むJSON形式でデータを返す
            let jsonData = "{\"i\": \"\(tempUserId)\"}"
            let data = jsonData.data(using: .utf8) ?? Data()
            
            request.value = data
            peripheral.respond(to: request, withResult: .success)
            print("📡 読み取り要求に応答: \(jsonData)")
        } else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
        }
    }
    
    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        // 送信キューが空いた時の処理
        print("送信キューが使用可能になりました")
    }
}
