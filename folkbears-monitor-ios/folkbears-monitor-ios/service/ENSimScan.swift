import Foundation
import Combine
import CoreBluetooth

/// EN API形式（FD6Fサービス）のアドバタイズをスキャンするクラス。
/// Android 実装（ENSimScan.kt）に合わせ、サービスUUIDでフィルタし、重複スキャンを抑制。
final class ENSimScan: NSObject, ObservableObject {
    
    /// 受信時のコールバック（UI側で集計する想定）
    var onReadTraceData: ((TraceData) -> Void)?

    @Published var isScanning = false
    @Published var scanningStatus = "停止中"

    private var centralManager: CBCentralManager!

    // ENSim (Exposure Notification Simulator) サービス UUID
    private let serviceUUID = CBUUID(string: "0000FD6F-0000-1000-8000-00805F9B34FB")
    private let serviceDataUUID = CBUUID(string: "0000FD6F-0000-1000-8000-00805F9B34FB")
    private let serviceUUIDalt = CBUUID(string: "0000FF00-0000-1000-8000-00805F9B34FB")
    private let serviceDataUUIDalt = CBUUID(string: "00000001-0000-1000-8000-00805F9B34FB")

    override init() {
        super.init()
        setupCentralManager()
    }
    private func setupCentralManager() {
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func startScan() {
        guard centralManager.state == .poweredOn else {
            print("ENSimScan: Bluetooth未準備のため開始できません state=\(centralManager.state.rawValue)")
            return
        }
        guard !isScanning else { return }

        centralManager.scanForPeripherals(
            // withServices: [serviceUUID, serviceUUIDalt],
            // FD6F を入れるとガードが掛かるので、外す
            withServices: [serviceUUIDalt],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )

        isScanning = true
        scanningStatus = "スキャン中..."
        print("ENSimScan: スキャン開始")
    }

    func stopScan() {
        guard isScanning else { return }
        centralManager.stopScan()
        isScanning = false
        scanningStatus = "停止中"
        print("ENSimScan: スキャン停止")
    }

    private func handleScanResult(peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        // サービスデータから tempId を取得（FD6F優先、FF00や派生UUIDも許容）
        guard let serviceData = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data] else { return }

        let data = serviceData[serviceDataUUID]
            ?? serviceData[serviceUUID]          // 一部デバイスはサービスUUIDでそのまま入る場合がある
            ?? serviceData[serviceUUIDalt]       // 代替サービスUUID
            ?? serviceData[serviceDataUUIDalt]   // 代替サービスデータUUID

        guard let payload = data, !payload.isEmpty else { return }

        let tempId = payload.map { String(format: "%02X", $0) }.joined()
        let trace = TraceData(
            timestamp: Date(),
            tempId: tempId,
            rssi: rssi.doubleValue,
            txPower: (advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber)?.doubleValue
        )

        print("ENSim 検出: \(peripheral.identifier.uuidString) tempId: \(tempId) rssi: \(rssi)")
        onReadTraceData?(trace)
    }
}

// MARK: - CBCentralManagerDelegate
extension ENSimScan: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("ENSimScan: Bluetooth On")
        case .unauthorized:
            print("ENSimScan: Bluetooth unauthorized")
        case .unsupported:
            print("ENSimScan: Bluetooth unsupported")
        case .poweredOff:
            print("ENSimScan: Bluetooth Off")
        default:
            break
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        handleScanResult(peripheral: peripheral, advertisementData: advertisementData, rssi: RSSI)
    }
}
