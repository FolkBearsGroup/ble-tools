//
//  ManufacturerDataTransmitter.swift
//  folkbears-transmitter-ios
//
//  Created by GitHub Copilot on 2026/02/15.
//

import Foundation
import CoreBluetooth
import Combine

/// Advertises custom manufacturer data (often consumed as scan response data on the scanner side).
/// フォーマット: [0]=0x02 (type), [1]=0x10 (length=16), [2..17]=TempId(16byte)

class ManufacturerDataTransmitter: NSObject, ObservableObject {
	private var peripheralManager: CBPeripheralManager?

	@Published var isTransmitting = false
	@Published var transmissionStatus = "停止中"
	@Published var bluetoothState = "Unknown"
	@Published var localName: String = "MFG"

	/// 16-bit company identifier (Little Endian in the payload). Default: 0xFFFF for testing.
	@Published var companyId: UInt16 = 0xFFFF
    let beacon_type = 0x02
    let beacon_length = 0x10

	/// Arbitrary manufacturer payload. Default 16 zero bytes for easy overriding.
	@Published var tempIdBytes: Data = Data(repeating: 0x00, count: 16)

	/// Last advertisement dictionary for debugging.
	private(set) var lastAdvertisementData: [String: Any]? = nil

	override init() {
		super.init()
		setupPeripheralManager()
	}

	private func setupPeripheralManager() {
		peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
	}

	/// Start advertising manufacturer data. Uses CBAdvertisementDataManufacturerDataKey which may appear in scan response on the scanner side depending on size and platform rules.
	func startTransmitting() {
		guard let manager = peripheralManager else {
			print("PeripheralManager が初期化されていません")
			return
		}

		guard manager.state == .poweredOn else {
			print("Bluetooth が利用できません (state: \(manager.state.rawValue))")
			return
		}

		guard !isTransmitting else {
			print("既にアドバタイズ中です")
			return
		}

		// Build manufacturer data: company ID (little endian) + payload.
		var mfgData = Data()
		mfgData.append(UInt8(companyId & 0xFF))
		mfgData.append(UInt8((companyId >> 8) & 0xFF))
        mfgData.append(UInt8(beacon_type))
		mfgData.append(UInt8(beacon_length))
        mfgData.append(tempIdBytes)
		let advertisementData: [String: Any] = [
			CBAdvertisementDataManufacturerDataKey: mfgData,
			CBAdvertisementDataLocalNameKey: localName
		]

		lastAdvertisementData = advertisementData
		manager.startAdvertising(advertisementData)

		isTransmitting = true
		transmissionStatus = "発信中..."

		print("📡 Manufacturer 発信開始")
		print(String(format: "   Company ID: 0x%04X (LE)", companyId))
		print("   tempIdBytes (hex): \(tempIdBytes.map { String(format: "%02X", $0) }.joined())")
		print("   Local Name: \(localName)")
	}

	func stopTransmitting() {
		guard let manager = peripheralManager, isTransmitting else { return }

		manager.stopAdvertising()
		isTransmitting = false
		transmissionStatus = "停止中"

		print("Manufacturer 発信停止")
	}
}

// MARK: - CBPeripheralManagerDelegate
extension ManufacturerDataTransmitter: CBPeripheralManagerDelegate {
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
				print("✅ アドバタイズ開始成功 (Manufacturer)")
				self.transmissionStatus = "発信中"
			}
		}
	}

	func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
		print("🔄 PeripheralManager の準備完了")
	}
}
