//
//  BeaconTransmitter.swift
//  folkbears.mini
//
//  Created by masuda on 2025/07/24.
//

import Foundation
import CoreBluetooth
import CoreLocation
import Combine

class BeaconTransmitter: NSObject, ObservableObject {
    private var peripheralManager: CBPeripheralManager?
    private var beaconRegion: CLBeaconRegion?
    // 実験用: raw iBeacon manufacturer data を使って広告するかどうか
    @Published var useRawIBeaconAdvertising = false
    // 保持しておくアドバタイズデータ（デバッグ用）
    private var lastAdvertisementData: [String: Any]?
    
    @Published var isTransmitting = false
    @Published var transmissionStatus = "停止中"
    @Published var bluetoothState = "Unknown"
    @Published var tempUserId: String = "User UUID"
    
    // デフォルトのiBeacon設定
    private let defaultUUID = UUID(uuidString: "90FA7ABE-FAB6-485E-B700-1A17804CAA13")!
    private let defaultIdentifier = "FolkBearsBeacon"
    
    // TempUserIdから生成されるMajor/Minor
    private var defaultMajor: CLBeaconMajorValue {
        return generateMajorFromTempUserId()
    }
    
    private var defaultMinor: CLBeaconMinorValue {
        return generateMinorFromTempUserId()
    }
    
    override init() {
        super.init()
        setupPeripheralManager()
    }
    
    private func setupPeripheralManager() {
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
    }
    
    func startTransmitting() {
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn,
              !isTransmitting else {
            print("Bluetooth が利用できないか、既に発信中です")
            return
        }
        
        // ビーコンリージョンを作成
        beaconRegion = CLBeaconRegion(
            uuid: defaultUUID,
            major: defaultMajor,
            minor: defaultMinor,
            identifier: defaultIdentifier
        )
        
        guard let region = beaconRegion else { return }
        
        // アドバタイズメントデータを生成
        if useRawIBeaconAdvertising {
            // raw manufacturer data を作成して startAdvertising する
            let uuid = defaultUUID
            let major = defaultMajor
            let minor = defaultMinor
            startAdvertisingRawIBeacon(uuid: uuid, major: UInt16(major), minor: UInt16(minor), txPower: -59)
        } else {
            // measuredPowerを明示的に設定（-59dBmが一般的）
            let peripheralData = region.peripheralData(withMeasuredPower: -59 as NSNumber)
            // 保持しておく（デバッグ）
            if let adv = peripheralData as? [String: Any] {
                lastAdvertisementData = adv
            }
            // アドバタイズ開始
            peripheralManager.startAdvertising(peripheralData as? [String: Any])
        }
        
        isTransmitting = true
        transmissionStatus = "発信中..."
        let majorHex = String(format: "%04X", defaultMajor)
        let minorHex = String(format: "%04X", defaultMinor)
        print("📡 iBeacon 発信開始")
        print("   UUID: \(defaultUUID)")
        print("   Major: 0x\(majorHex) (\(defaultMajor))")
        print("   Minor: 0x\(minorHex) (\(defaultMinor))")
        print("   Measured Power: -59dBm")
        
        // デバッグ用：アドバタイズメントデータを表示
        if useRawIBeaconAdvertising {
            if let adv = lastAdvertisementData {
                print("   Advertisement Data (raw manufacturer used): \(adv)")
            } else {
                print("   Advertisement Data: (raw manufacturer advertising active)")
            }
        } else {
            if let advData = lastAdvertisementData {
                print("   Advertisement Data: \(advData)")
            }
        }
    }

    // MARK: - Raw iBeacon (manufacturer data) 広告（実験用）
    /// iBeacon の manufacturer data を手作りして広告を行う（実験用）
    private func startAdvertisingRawIBeacon(uuid: UUID, major: UInt16, minor: UInt16, txPower: Int8 = -59) {
        // iBeacon フォーマット: Apple company id (0x004C little-endian), 0x02, 0x15, UUID(16), major(2), minor(2), tx(1)
        var data = Data()
        // Apple company ID (0x004C) little-endian
        data.append(0x4C)
        data.append(0x00)
        // iBeacon type and length
        data.append(0x02)
        data.append(0x15)

        // UUID bytes (big-endian order as raw bytes of UUID)
        withUnsafeBytes(of: uuid.uuid) { (bytes: UnsafeRawBufferPointer) in
            data.append(contentsOf: bytes)
        }

        // major (big endian)
        data.append(UInt8((major >> 8) & 0xFF))
        data.append(UInt8(major & 0xFF))
        // minor (big endian)
        data.append(UInt8((minor >> 8) & 0xFF))
        data.append(UInt8(minor & 0xFF))
        // tx power
        data.append(UInt8(bitPattern: txPower))

        let adv: [String: Any] = [CBAdvertisementDataManufacturerDataKey: data]
        // デバッグ用に保持と表示
        lastAdvertisementData = adv
        print("📡 iBeacon (raw) 発信データ生成: manufacturerData length=\(data.count)")

        peripheralManager?.startAdvertising(adv)
    }
    
    func stopTransmitting() {
        guard let peripheralManager = peripheralManager,
              isTransmitting else { return }
        
        peripheralManager.stopAdvertising()
        
        isTransmitting = false
        transmissionStatus = "停止中"
        print("iBeacon 発信停止")
    }
    
    func updateBeaconParameters(major: CLBeaconMajorValue? = nil, minor: CLBeaconMinorValue? = nil) {
        let newMajor = major ?? defaultMajor
        let newMinor = minor ?? defaultMinor
        
        if isTransmitting {
            stopTransmitting()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.startTransmitting()
            }
        }
        
        print("ビーコンパラメータ更新 - Major: \(newMajor), Minor: \(newMinor)")
    }
    
    // MARK: - TempUserId からの Major/Minor 生成
    
    /// TempUserIdの先頭4文字から16進数でMajor値を生成
    private func generateMajorFromTempUserId() -> CLBeaconMajorValue {
        // 先頭4文字を取得（ハイフンを除去）
        let cleanedId = tempUserId.replacingOccurrences(of: "-", with: "")
        let prefix = String(cleanedId.prefix(4))
        
        // 16進数として解析
        if let majorValue = UInt16(prefix, radix: 16) {
            print("📱 Major生成: \(prefix) -> \(majorValue)")
            return majorValue
        }
        
        // フォールバック: デフォルト値
        print("⚠️ Major生成失敗、デフォルト値1を使用")
        return 1
    }
    
    /// TempUserIdの5〜8文字目から16進数でMinor値を生成
    private func generateMinorFromTempUserId() -> CLBeaconMinorValue {
        let tempUserId = self.tempUserId
        
        // 5〜8文字目を取得（ハイフンを除去）
        let cleanedId = tempUserId.replacingOccurrences(of: "-", with: "")
        
        guard cleanedId.count >= 8 else {
            print("⚠️ Minor生成失敗（文字数不足）、デフォルト値1を使用")
            return 1
        }
        
        let startIndex = cleanedId.index(cleanedId.startIndex, offsetBy: 4)
        let endIndex = cleanedId.index(cleanedId.startIndex, offsetBy: 8)
        let substring = String(cleanedId[startIndex..<endIndex])
        
        // 16進数として解析
        if let minorValue = UInt16(substring, radix: 16) {
            print("📱 Minor生成: \(substring) -> \(minorValue)")
            return minorValue
        }
        
        // フォールバック: デフォルト値
        print("⚠️ Minor生成失敗、デフォルト値1を使用")
        return 1
    }
}

// MARK: - CBPeripheralManagerDelegate
extension BeaconTransmitter: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        DispatchQueue.main.async {
            switch peripheral.state {
            case .poweredOn:
                self.bluetoothState = "Powered On"
                print("Bluetooth が有効になりました")
            case .poweredOff:
                self.bluetoothState = "Powered Off"
                self.stopTransmitting()
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
                print("❌ アドバタイズ開始エラー: \(error.localizedDescription)")
                self.transmissionStatus = "エラー: \(error.localizedDescription)"
                self.isTransmitting = false
            } else {
                print("✅ アドバタイズ開始成功")
                print("   状態: Advertising")
                print("   確認: Android側でスキャンを開始してください")
                self.transmissionStatus = "発信中"
            }
        }
    }
    
    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        print("🔄 PeripheralManagerの準備完了")
    }
}
