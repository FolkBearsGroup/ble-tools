import Foundation
import Combine
import CoreBluetooth

/// Manufacturer Data をスキャンして上位へ通知する簡易クラス。
final class ManufacturerDataScan: NSObject, ObservableObject {
    /// 受信時のコールバック。keyはCompany ID(16bit)を0xXXXXで表記。
    var onManufacturerData: ((String, Data, NSNumber, CBPeripheral, Data) -> Void)?

    @Published var isScanning = false
    @Published var scanningStatus = "停止中"

    private var centralManager: CBCentralManager!

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func startScan() {
        guard centralManager.state == .poweredOn else {
            print("ManufacturerDataScan: Bluetooth未準備 state=\(centralManager.state.rawValue)")
            return
        }
        guard !isScanning else { return }

        centralManager.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        isScanning = true
        scanningStatus = "スキャン中..."
    }

    func stopScan() {
        guard isScanning else { return }
        centralManager.stopScan()
        isScanning = false
        scanningStatus = "停止中"
    }
}

extension ManufacturerDataScan: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("ManufacturerDataScan: Bluetooth On")
        case .unauthorized:
            print("ManufacturerDataScan: unauthorized")
        case .unsupported:
            print("ManufacturerDataScan: unsupported")
        case .poweredOff:
            print("ManufacturerDataScan: Bluetooth Off")
        default:
            break
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard let data = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data, !data.isEmpty else { return }
        // Company IDは先頭2バイトLittle Endianで格納される
        let companyId = data.prefix(2).reduce(0) { acc, byte in (acc << 8) | Int(byte) }
        let key = String(format: "0x%04X", companyId)
        let beacon_type = data[2]
        let beacon_length = data[3]


        if ( companyId == 0xFFFF ) {
            if ( beacon_type == 0x02 && beacon_length == 0x10 ) {
                let tempid = data.dropFirst(4)
                onManufacturerData?(key, data, RSSI, peripheral, tempid)
            }
        }
    }
}   
