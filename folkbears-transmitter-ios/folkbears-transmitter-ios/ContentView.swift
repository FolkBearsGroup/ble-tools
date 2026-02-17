//
//  ContentView.swift
//  folkbears-transmitter-ios
//
//  Created by masuda on 2026/02/15.
//

import SwiftUI
import CoreLocation
import CoreBluetooth

struct ContentView: View {
    var body: some View {
        TabView {
            BeaconTabView()
                .tabItem {
                    Label("Beacon", systemImage: "antenna.radiowaves.left.and.right")
                }

            ENTabView()
                .tabItem {
                    Label("EN API", systemImage: "wave.3.right")
                }

            FolkBearsTabView()
                .tabItem {
                    Label("FolkBears", systemImage: "bear")
                }

            MfDTabView()
                .tabItem {
                    Label("MfD", systemImage: "shippingbox")
                }
        }
    }
}

struct BeaconTabView: View {
    @StateObject private var transmitter = BeaconTransmitter()

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("ステータス")) {
                    HStack {
                        Text("Bluetooth")
                        Spacer()
                        Text(transmitter.bluetoothState)
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("送信状態")
                        Spacer()
                        Text(transmitter.transmissionStatus)
                            .foregroundStyle(transmitter.isTransmitting ? .green : .secondary)
                    }
                }

                Section(header: Text("Temp User ID")) {
                    TextField("例: ABCD-1234-5678", text: $transmitter.tempUserId)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                }

                Section(header: Text("オプション")) {
                    Toggle("raw iBeacon manufacturer を使う", isOn: $transmitter.useRawIBeaconAdvertising)
                }

                Section(header: Text("操作")) {
                    HStack {
                        Button {
                            transmitter.startTransmitting()
                        } label: {
                            Label("発信開始", systemImage: "play.fill")
                        }
                        .disabled(transmitter.isTransmitting || transmitter.bluetoothState != "Powered On")

                        Button {
                            transmitter.stopTransmitting()
                        } label: {
                            Label("停止", systemImage: "stop.fill")
                        }
                        .disabled(!transmitter.isTransmitting)
                    }
                }
            }
            .navigationTitle("Beacon")
        }
    }
}

struct ENTabView: View {
    @StateObject private var transmitter = ENSimTransmitter()

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("ステータス")) {
                    HStack {
                        Text("Bluetooth")
                        Spacer()
                        Text(transmitter.bluetoothState)
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("送信状態")
                        Spacer()
                        Text(transmitter.transmissionStatus)
                            .foregroundStyle(transmitter.isTransmitting ? .green : .secondary)
                    }
                }

                Section(header: Text("設定")) {
                    TextField("ローカル名", text: $transmitter.localName)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                    Toggle("代替サービス UUID を使う", isOn: $transmitter.useAltService)
                }

                Section(header: Text("操作")) {
                    HStack {
                        Button {
                            transmitter.startTransmitting()
                        } label: {
                            Label("発信開始", systemImage: "play.fill")
                        }
                        .disabled(transmitter.isTransmitting || transmitter.bluetoothState != "Powered On")

                        Button {
                            transmitter.stopTransmitting()
                        } label: {
                            Label("停止", systemImage: "stop.fill")
                        }
                        .disabled(!transmitter.isTransmitting)
                    }
                }
            }
            .navigationTitle("EN API")
        }
    }
}

struct FolkBearsTabView: View {
    @StateObject private var advertiser = GattAdvertise()

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("ステータス")) {
                    HStack {
                        Text("Bluetooth")
                        Spacer()
                        Text(advertiser.bluetoothState)
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("アドバタイズ" )
                        Spacer()
                        Text(advertiser.advertisingStatus)
                            .foregroundStyle(advertiser.isAdvertising ? .green : .secondary)
                    }
                    HStack {
                        Text("接続中")
                        Spacer()
                        Text("\(advertiser.connectedCentrals.count) 台")
                            .foregroundStyle(.secondary)
                    }
                }

                Section(header: Text("状態サマリ")) {
                    Text(advertiser.getConnectionSummary())
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundStyle(.secondary)
                }

                Section(header: Text("操作")) {
                    HStack {
                        Button {
                            advertiser.startAdvertising()
                        } label: {
                            Label("発信開始", systemImage: "antenna.radiowaves.left.and.right")
                        }
                        .disabled(advertiser.isAdvertising || advertiser.bluetoothState != "Powered On")

                        Button {
                            advertiser.stopAdvertising()
                        } label: {
                            Label("停止", systemImage: "stop.fill")
                        }
                        .disabled(!advertiser.isAdvertising)
                    }
                }
            }
            .navigationTitle("FolkBears")
        }
    }
}

struct MfDTabView: View {
    @StateObject private var transmitter = ManufacturerDataTransmitter()
    @State private var companyIdText: String = "FFFF"
    @State private var tempIdHex: String = {
        // 16 random bytes as 32 hex characters
        let bytes = (0..<16).map { _ in UInt8.random(in: 0...255) }
        return bytes.map { String(format: "%02X", $0) }.joined()
    }()

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("ステータス")) {
                    HStack {
                        Text("Bluetooth")
                        Spacer()
                        Text(transmitter.bluetoothState)
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("送信状態")
                        Spacer()
                        Text(transmitter.transmissionStatus)
                            .foregroundStyle(transmitter.isTransmitting ? .green : .secondary)
                    }
                }

                Section(header: Text("ペイロード")) {
                    TextField("Company ID (hex)", text: $companyIdText)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                        .onSubmit(applyCompanyId)

                    TextField("Local Name", text: $transmitter.localName)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))

                    TextField("Temp ID (32 hex)", text: $tempIdHex)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                        .onSubmit(applyTempId)
                }

                Section(header: Text("操作")) {
                    HStack {
                        Button {
                            applyCompanyId()
                            applyTempId()
                            transmitter.startTransmitting()
                        } label: {
                            Label("発信開始", systemImage: "antenna.radiowaves.left.and.right")
                        }
                        .disabled(transmitter.isTransmitting || transmitter.bluetoothState != "Powered On")

                        Button {
                            transmitter.stopTransmitting()
                        } label: {
                            Label("停止", systemImage: "stop.fill")
                        }
                        .disabled(!transmitter.isTransmitting)
                    }
                }
            }
            .navigationTitle("MfD")
            .onAppear(perform: syncFieldsFromModel)
        }
    }

    private func syncFieldsFromModel() {
        companyIdText = String(format: "%04X", transmitter.companyId)
        tempIdHex = transmitter.tempIdBytes.map { String(format: "%02X", $0) }.joined()
    }

    private func applyCompanyId() {
        let cleaned = companyIdText.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "0x", with: "").uppercased()
        if let value = UInt16(cleaned, radix: 16) {
            transmitter.companyId = value
            companyIdText = String(format: "%04X", value)
        }
    }

    private func applyTempId() {
        let cleaned = tempIdHex.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: " ", with: "").uppercased()
        guard let data = Data(hexString: cleaned, expectedLength: 16) else { return }
        transmitter.tempIdBytes = data
        tempIdHex = cleaned
    }
}

private extension Data {
    init?(hexString: String, expectedLength: Int) {
        let chars = Array(hexString)
        guard chars.count == expectedLength * 2 else { return nil }
        var bytes = [UInt8]()
        bytes.reserveCapacity(expectedLength)
        for i in stride(from: 0, to: chars.count, by: 2) {
            let hi = chars[i]
            let lo = chars[i + 1]
            let pair = String([hi, lo])
            guard let b = UInt8(pair, radix: 16) else { return nil }
            bytes.append(b)
        }
        self.init(bytes)
    }
}

#Preview {
    ContentView()
}
