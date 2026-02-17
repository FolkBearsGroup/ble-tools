//
//  ENSimTransmitter.swift
//  folkbears-transmitter-ios
//
//  Created by GitHub Copilot on 2026/02/15.
//

import Foundation
import CoreBluetooth
import Combine

/// Advertises a 16-bit Exposure Notification service UUID for simulation purposes.
class ENSimTransmitter: NSObject, ObservableObject {
	private var peripheralManager: CBPeripheralManager?
	private let serviceUUID = CBUUID(string: "FD6F") // Exposure Notification 16-bit UUID
    private let altServiceUUID = CBUUID(string: "FF00") // Alternative UUID for testing

	@Published var isTransmitting = false
	@Published var transmissionStatus = "停止中"
	@Published var bluetoothState = "Unknown"
	@Published var localName = "ENSim"
    @Published var useAltService: Bool = false
	@Published var rpi: Data = ENSimTransmitter.generateRandomRpi()

	override init() {
		super.init()
		setupPeripheralManager()
	}

	private func setupPeripheralManager() {
		peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
	}

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

        let selectedService = useAltService ? altServiceUUID : serviceUUID
        let serviceData: [CBUUID: Data] = [selectedService: rpi]

		let advertisementData: [String: Any] = [
			CBAdvertisementDataServiceUUIDsKey: [selectedService],
			CBAdvertisementDataLocalNameKey: localName,
			// CBAdvertisementDataServiceDataKey: serviceData
		]

		manager.startAdvertising(advertisementData)

		isTransmitting = true
		transmissionStatus = "発信中..."

		print("📡 EN シミュレーション発信開始")
		print("   Service UUID (16-bit): \(useAltService ? altServiceUUID.uuidString : serviceUUID.uuidString)")
		print("   Local Name: \(localName)")
		print("   RPI (hex): \(rpi.map { String(format: "%02X", $0) }.joined())")
	}

	func stopTransmitting() {
		guard let manager = peripheralManager, isTransmitting else { return }

		manager.stopAdvertising()
		isTransmitting = false
		transmissionStatus = "停止中"

		print("EN シミュレーション発信停止")
	}

	private static func generateRandomRpi() -> Data {
		
		// let bytes = (0..<16).map { _ in UInt8.random(in: 0...255) }

		// ランダムな uuid を生成して RPI として使用（デバッグ用）
		// 送信は成功するが、Service Data の内容はランダム値になってしまうので、
		// 実質利用ができない。
		let uuid = UUID()
		let uuidBytes = withUnsafeBytes(of: uuid.uuid) { Array($0) }
		let bytes = Array(uuidBytes.prefix(16))
		return Data(bytes)
	}
}

// MARK: - CBPeripheralManagerDelegate
extension ENSimTransmitter: CBPeripheralManagerDelegate {
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
				print("✅ アドバタイズ開始成功 (Service UUID: \(self.useAltService ? self.altServiceUUID.uuidString : self.serviceUUID.uuidString))")
				self.transmissionStatus = "発信中"
			}
		}
	}

	func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
		print("🔄 PeripheralManager の準備完了")
	}
}
