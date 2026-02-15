//
//  BeaconScan.swift
//  folkbears.mini
//
//  Created by masuda on 2025/07/24.
//

import Foundation
import CoreLocation
import Combine

class BeaconScan: NSObject, ObservableObject {
    private var locationManager: CLLocationManager
    private var beaconRegion: CLBeaconRegion?
    private var beaconConstraint: CLBeaconIdentityConstraint?

    /// iBeacon検出時に呼ばれるコールバック（UIで集計するため）
    var onIBeacon: ((CLBeacon, Date) -> Void)?
    
    @Published var discoveredBeacons: [CLBeacon] = []
    @Published var isScanning = false
    @Published var scanningStatus = "停止中"
    
    // デフォルトのiBeacon設定
    private let defaultUUID = UUID(uuidString: "90FA7ABE-FAB6-485E-B700-1A17804CAA13")!
    private let defaultIdentifier = "FolkBearsBeacon"
    
    override init() {
        self.locationManager = CLLocationManager()
        super.init()
        setupLocationManager()
    }
    
    private func setupLocationManager() {
        locationManager.delegate = self
        // iBeaconレンジングには「このAppの使用中」以上が必要。バックグラウンド受信する場合は Always も要求する。
        if locationManager.authorizationStatus == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        }
    }
    
    func startScanning() {
        guard !isScanning else { return }
        
        // ビーコンリージョンを作成
        beaconRegion = CLBeaconRegion(
            uuid: defaultUUID,
            identifier: defaultIdentifier
        )
        beaconConstraint = CLBeaconIdentityConstraint(uuid: defaultUUID)
        
        guard let region = beaconRegion else { return }
        
        // リージョンモニタリング開始
        locationManager.startMonitoring(for: region)

        // すぐにレンジング開始（既にリージョン内にいる場合 didEnterRegion が来ないことがあるため）
        if let constraint = beaconConstraint {
            locationManager.startRangingBeacons(satisfying: constraint)
        }
        
        isScanning = true
        scanningStatus = "スキャン中..."
        print("iBeacon スキャン開始")
    }
    
    func stopScanning() {
        guard isScanning else { return }
        
        if let region = beaconRegion {
            locationManager.stopMonitoring(for: region)
        }

        if let constraint = beaconConstraint {
            locationManager.stopRangingBeacons(satisfying: constraint)
        }
        
        isScanning = false
        scanningStatus = "停止中"
        discoveredBeacons.removeAll()
        print("iBeacon スキャン停止")
    }
}

// MARK: - CLLocationManagerDelegate
extension BeaconScan: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        guard let beaconRegion = region as? CLBeaconRegion else { return }
        print("ビーコンリージョンに入りました: \(beaconRegion.identifier)")
        
        // レンジング開始
        let constraint = beaconConstraint ?? CLBeaconIdentityConstraint(uuid: beaconRegion.uuid)
        beaconConstraint = constraint
        locationManager.startRangingBeacons(satisfying: constraint)
    }
    
    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        guard let beaconRegion = region as? CLBeaconRegion else { return }
        print("ビーコンリージョンから出ました: \(beaconRegion.identifier)")
        
        // レンジング停止
        let constraint = beaconConstraint ?? CLBeaconIdentityConstraint(uuid: beaconRegion.uuid)
        locationManager.stopRangingBeacons(satisfying: constraint)
    }
    
    func locationManager(_ manager: CLLocationManager, didRange beacons: [CLBeacon], satisfying beaconConstraint: CLBeaconIdentityConstraint) {
        let now = Date()
        let validBeacons = beacons.filter { $0.proximity != .unknown }

        DispatchQueue.main.async {
            self.discoveredBeacons = validBeacons
            self.scanningStatus = "検出: \(validBeacons.count)個"
        }

        for beacon in validBeacons {
            let majorHex = String(format: "%04X", beacon.major.uint16Value)
            let minorHex = String(format: "%04X", beacon.minor.uint16Value)
            print("ビーコン検出 - UUID: \(beacon.uuid), Major: 0x\(majorHex), Minor: 0x\(minorHex), RSSI: \(beacon.rssi), Distance: \(String(format: "%.2f", beacon.accuracy))m")
            DispatchQueue.main.async {
                self.onIBeacon?(beacon, now)
            }
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location Manager エラー: \(error.localizedDescription)")
        DispatchQueue.main.async {
            self.scanningStatus = "エラー"
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        switch status {
        case .authorizedWhenInUse, .authorizedAlways:
            print("位置情報の使用が許可されました")
            // 権限取得後にスキャン指示が出ていた場合、レンジングを開始しておく
            if isScanning {
                let constraint = beaconConstraint ?? CLBeaconIdentityConstraint(uuid: defaultUUID)
                beaconConstraint = constraint
                locationManager.startRangingBeacons(satisfying: constraint)
            }
        case .denied, .restricted:
            print("位置情報の使用が拒否されました")
            DispatchQueue.main.async {
                self.scanningStatus = "位置情報権限が必要"
            }
        case .notDetermined:
            print("位置情報の権限が未確定")
        @unknown default:
            break
        }
    }
}
