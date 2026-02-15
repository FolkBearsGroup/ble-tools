//
//  ContentView.swift
//  folkbears-monitor-ios
//
//  Created by masuda on 2026/02/14.
//

import SwiftUI
import CoreLocation
import CoreBluetooth

struct ContentView: View {
    var body: some View {
        TabView {
            IBeaconTabView()
                .tabItem { Label("iBeacon", systemImage: "dot.radiowaves.left.and.right") }

            FolkBearsTabView()
                .tabItem { Label("FolkBears", systemImage: "antenna.radiowaves.left.and.right") }

            EnApiTabView()
                .tabItem { Label("EN API", systemImage: "waveform.path") }

            ManufacturerDataTabView()
                .tabItem { Label("Mfr Data", systemImage: "barcode") }
        }
    }
}

// MARK: - iBeacon
private struct IBeaconTabView: View {
    @StateObject private var scanner = BeaconScan()
    @State private var detectionLog: [(id: String, beacon: CLBeacon, date: Date)] = []
    @State private var summaries: [BeaconSummary] = []

    private let windowSeconds: TimeInterval = 5 * 60

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("状態: \(scanner.scanningStatus)")
                    Spacer()
                    Button(scanner.isScanning ? "停止" : "開始") {
                        scanner.isScanning ? scanner.stopScanning() : scanner.startScanning()
                    }
                    .buttonStyle(.borderedProminent)
                }

                if summaries.isEmpty {
                    Text("受信した iBeacon がまだありません")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    List(summaries) { summary in
                        BeaconRow(summary: summary)
                    }
                    .listStyle(.plain)
                }
            }
            .padding()
            .navigationTitle("iBeacon")
            .onAppear {
                scanner.onIBeacon = { beacon, date in
                    addDetection(beacon, at: date)
                }
                if !scanner.isScanning { scanner.startScanning() }
            }
            .onDisappear {
                detectionLog.removeAll()
                summaries.removeAll()
            }
        }
    }

    private func addDetection(_ beacon: CLBeacon, at date: Date) {
        let id = "\(beacon.uuid.uuidString)-\(beacon.major.intValue)-\(beacon.minor.intValue)"
        detectionLog.append((id: id, beacon: beacon, date: date))

        // 5分より古いログを削除
        detectionLog = detectionLog.filter { date.timeIntervalSince($0.date) <= windowSeconds }

        // 集計
        let grouped = Dictionary(grouping: detectionLog, by: { $0.id })
        summaries = grouped.values.compactMap { entries in
            guard let latest = entries.max(by: { $0.date < $1.date }) else { return nil }
            return BeaconSummary(
                id: latest.id,
                uuid: latest.beacon.uuid,
                major: latest.beacon.major.uint16Value,
                minor: latest.beacon.minor.uint16Value,
                rssi: latest.beacon.rssi,
                accuracy: latest.beacon.accuracy,
                proximity: latest.beacon.proximity,
                count: entries.count
            )
        }
        .sorted { $0.count > $1.count }
    }
}

private struct BeaconRow: View {
    let summary: BeaconSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("UUID: \(summary.uuid.uuidString)")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack {
                Text("major: 0x\(String(format: "%04X", summary.major))")
                Text("minor: 0x\(String(format: "%04X", summary.minor))")
            }
            HStack {
                Text("受信回数(5分内): \(summary.count)")
                Text("RSSI: \(summary.rssi)")
                Text(String(format: "距離: %.2fm", summary.accuracy))
            }
        }
        .padding(.vertical, 4)
    }

    private func proximityLabel(_ proximity: CLProximity) -> String {
        switch proximity {
        case .immediate: return "Immediate"
        case .near: return "Near"
        case .far: return "Far"
        default: return "Unknown"
        }
    }
}

private struct BeaconSummary: Identifiable {
    let id: String
    let uuid: UUID
    let major: CLBeaconMajorValue
    let minor: CLBeaconMinorValue
    let rssi: Int
    let accuracy: CLLocationAccuracy
    let proximity: CLProximity
    let count: Int
}

// MARK: - FolkBears (GATT)
private struct FolkBearsTabView: View {
    @StateObject private var client = GattClient()
    @State private var detectionLog: [(id: String, peripheral: GattClient.DiscoveredPeripheral, date: Date)] = []
    @State private var summaries: [FolkDeviceSummary] = []

    private let windowSeconds: TimeInterval = 5 * 60

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("状態: \(client.scanningStatus)")
                    Spacer()
                    Button(client.isScanning ? "停止" : "開始") {
                        client.isScanning ? client.stopScanning() : client.startScanning()
                    }
                    .buttonStyle(.borderedProminent)
                }

                if client.isScanning {
                    HStack {
                        ProgressView()
                        Text("スキャン中...")
                            .foregroundStyle(.secondary)
                    }
                }

                if summaries.isEmpty {
                    Text("周辺の FolkBears デバイスが見つかっていません")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    List(summaries) { summary in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(summary.name)
                                .font(.headline)
                            Text("受信回数(5分内): \(summary.count)")
                            Text("RSSI: \(summary.rssi)")
                                .foregroundStyle(.secondary)
                            /*
                            if !summary.advertisementKeys.isEmpty {
                                Text("広告: \(summary.advertisementKeys.joined(separator: ", "))")
                                    .foregroundStyle(.tertiary)
                                    .font(.caption)
                            }
                            Button("接続を試す") {
                                client.connectToPeripheral(summary.latestPeripheral)
                            }
                            .buttonStyle(.bordered)
                            */
                        }
                        .padding(.vertical, 4)
                    }
                    .listStyle(.plain)
                }
            }
            .padding()
            .navigationTitle("FolkBears")
            .onAppear {
                client.onDiscover = { peripheral, date in
                    addDetection(peripheral, at: date)
                }
                if !client.isScanning {
                    client.startScanning()
                }
            }
            .onDisappear {
                detectionLog.removeAll()
                summaries.removeAll()
            }
        }
    }

    private func addDetection(_ peripheral: GattClient.DiscoveredPeripheral, at date: Date) {
        let id = peripheral.identifierString
        detectionLog.append((id: id, peripheral: peripheral, date: date))

        // 5分より古いログを削除
        detectionLog = detectionLog.filter { date.timeIntervalSince($0.date) <= windowSeconds }

        // 集計（IDごとに回数と最新RSSIを保持）
        let grouped = Dictionary(grouping: detectionLog, by: { $0.id })
        summaries = grouped.values.compactMap { entries in
            guard let latest = entries.max(by: { $0.date < $1.date }) else { return nil }
            let rssi = latest.peripheral.rssi.intValue
            let name = latest.peripheral.displayName
            let advKeys = latest.peripheral.advertisementData.keys.map { "\($0)" }.sorted()
            return FolkDeviceSummary(
                id: latest.id,
                name: name,
                rssi: rssi,
                count: entries.count,
                advertisementKeys: advKeys,
                latestPeripheral: latest.peripheral
            )
        }
        .sorted { $0.count > $1.count }
    }
}

private struct FolkDeviceSummary: Identifiable {
    let id: String
    let name: String
    let rssi: Int
    let count: Int
    let advertisementKeys: [String]
    let latestPeripheral: GattClient.DiscoveredPeripheral
}

// MARK: - EN API placeholder
private struct EnApiTabView: View {
    @StateObject private var scanner = ENSimScan()
    @State private var detectionLog: [(id: String, trace: TraceData, date: Date)] = []
    @State private var summaries: [EnApiSummary] = []

    private let windowSeconds: TimeInterval = 5 * 60

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("状態: \(scanner.scanningStatus)")
                    Spacer()
                    Button(scanner.isScanning ? "停止" : "開始") {
                        scanner.isScanning ? scanner.stopScan() : scanner.startScan()
                    }
                    .buttonStyle(.borderedProminent)
                }

                if summaries.isEmpty {
                    Text("EN API の受信結果がまだありません")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    List(summaries) { summary in
                        VStack(alignment: .leading, spacing: 6) {
                            Text("TempId: \(summary.tempId)")
                                .font(.headline)
                            HStack {
                                Text("受信回数(5分内): \(summary.count)")
                                Text("RSSI: \(summary.rssi)")
                                if let tx = summary.txPower { Text("Tx: \(String(format: "%.0f", tx))") }
                            }
                            Text(summary.lastSeen.formatted(date: .omitted, time: .standard))
                                .foregroundStyle(.secondary)
                                .font(.caption)
                        }
                        .padding(.vertical, 4)
                    }
                    .listStyle(.plain)
                }
            }
            .padding()
            .navigationTitle("EN API")
            .onAppear {
                scanner.onReadTraceData = { trace in
                    addTrace(trace)
                }
                if !scanner.isScanning { scanner.startScan() }
            }
            .onDisappear {
                scanner.stopScan()
                detectionLog.removeAll()
                summaries.removeAll()
            }
        }
    }

    private func addTrace(_ trace: TraceData) {
        let date = trace.timestamp ?? Date()
        let id = trace.tempId ?? "(unknown)"
        detectionLog.append((id: id, trace: trace, date: date))

        // 5分より古いログを削除
        detectionLog = detectionLog.filter { date.timeIntervalSince($0.date) <= windowSeconds }

        // 集計
        let grouped = Dictionary(grouping: detectionLog, by: { $0.id })
        summaries = grouped.values.compactMap { entries in
            guard let latest = entries.max(by: { $0.date < $1.date }) else { return nil }
            let latestTrace = latest.trace
            return EnApiSummary(
                id: latest.id,
                tempId: latest.id,
                rssi: Int(latestTrace.rssi ?? 0),
                txPower: latestTrace.txPower,
                lastSeen: latest.date,
                count: entries.count
            )
        }
        .sorted { $0.count > $1.count }
    }
}

private struct EnApiSummary: Identifiable {
    let id: String
    let tempId: String
    let rssi: Int
    let txPower: Double?
    let lastSeen: Date
    let count: Int
}

// MARK: - Manufacturer Data
private struct ManufacturerDataTabView: View {
    @StateObject private var scanner = ManufacturerDataScan()
    @State private var detectionLog: [(id: String, companyId: String, payload: Data, rssi: Int, peripheralName: String, date: Date)] = []
    @State private var summaries: [ManufacturerDataSummary] = []

    private let windowSeconds: TimeInterval = 5 * 60

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("状態: \(scanner.scanningStatus)")
                    Spacer()
                    Button(scanner.isScanning ? "停止" : "開始") {
                        scanner.isScanning ? scanner.stopScan() : scanner.startScan()
                    }
                    .buttonStyle(.borderedProminent)
                }

                if summaries.isEmpty {
                    Text("Manufacturer Data の受信結果がまだありません")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    List(summaries) { summary in
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Company ID: \(summary.companyId)")
                                .font(.headline)
                            Text("Payload: \(summary.payloadHex)")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            HStack {
                                Text("受信回数(5分内): \(summary.count)")
                                Text("RSSI: \(summary.rssi)")
                                Text(summary.peripheralName)
                                    .foregroundStyle(.secondary)
                            }
                            Text(summary.lastSeen.formatted(date: .omitted, time: .standard))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                    .listStyle(.plain)
                }
            }
            .padding()
            .navigationTitle("Manufacturer Data")
            .onAppear {
                scanner.onManufacturerData = { companyId, data, rssi, peripheral, tempid in
                    let payload = tempid.isEmpty ? data : tempid
                    let name = peripheral.name ?? "(unknown)"
                    addDetection(companyId: companyId, payload: payload, rssi: rssi.intValue, peripheralName: name, at: Date())
                }
                if !scanner.isScanning { scanner.startScan() }
            }
            .onDisappear {
                scanner.stopScan()
                detectionLog.removeAll()
                summaries.removeAll()
            }
        }
    }

    private func addDetection(companyId: String, payload: Data, rssi: Int, peripheralName: String, at date: Date) {
        let id = companyId + "-" + hexString(payload)
        detectionLog.append((id: id, companyId: companyId, payload: payload, rssi: rssi, peripheralName: peripheralName, date: date))

        // 5分より古いログを削除
        detectionLog = detectionLog.filter { date.timeIntervalSince($0.date) <= windowSeconds }

        // 集計（IDごとに回数と最新RSSIを保持）
        let grouped = Dictionary(grouping: detectionLog, by: { $0.id })
        summaries = grouped.values.compactMap { entries in
            guard let latest = entries.max(by: { $0.date < $1.date }) else { return nil }
            return ManufacturerDataSummary(
                id: latest.id,
                companyId: latest.companyId,
                payloadHex: hexString(latest.payload),
                rssi: latest.rssi,
                count: entries.count,
                lastSeen: latest.date,
                peripheralName: latest.peripheralName
            )
        }
        .sorted { $0.count > $1.count }
    }

    private func hexString(_ data: Data) -> String {
        data.map { String(format: "%02X", $0) }.joined()
    }
}

private struct ManufacturerDataSummary: Identifiable {
    let id: String
    let companyId: String
    let payloadHex: String
    let rssi: Int
    let count: Int
    let lastSeen: Date
    let peripheralName: String
}

#Preview {
    ContentView()
}
