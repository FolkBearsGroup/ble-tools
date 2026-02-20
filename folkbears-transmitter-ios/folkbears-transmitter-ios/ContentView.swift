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

            FolkBearsTabView()
                .tabItem {
                    Label("FolkBears", systemImage: "bear")
                }

            ENTabView()
                .tabItem {
                    Label("EN API", systemImage: "wave.3.right")
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
    @State private var majorHex: String = ""
    @State private var minorHex: String = ""

    var body: some View {
        NavigationView {
            Form {
                Section {
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
                } header: {
                    Text("ステータス")
                }

                Section {
                    TextField("Major (4 hex)", text: $majorHex)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                        .onSubmit(applyMajorMinor)

                    TextField("Minor (4 hex)", text: $minorHex)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                        .onSubmit(applyMajorMinor)
                } header: {
                    Text("Major / Minor (hex)")
                }

                Section {
                    Toggle("raw iBeacon manufacturer を使う", isOn: $transmitter.useRawIBeaconAdvertising)
                } header: {
                    Text("オプション")
                }

                Section {
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
                } header: {
                    Text("操作")
                }
            }
            .navigationTitle("Beacon")
            .onAppear(perform: syncFieldsFromModel)
        }
    }

    private func syncFieldsFromModel() {
        let newMajor = UInt16.random(in: 0...UInt16.max)
        let newMinor = UInt16.random(in: 0...UInt16.max)
        transmitter.major = newMajor
        transmitter.minor = newMinor
        majorHex = String(format: "%04X", newMajor)
        minorHex = String(format: "%04X", newMinor)
    }

    private func applyMajorMinor() {
        let cleanedMajor = majorHex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "0x", with: "")
            .uppercased()
        if let value = UInt16(cleanedMajor, radix: 16) {
            transmitter.major = value
            majorHex = String(format: "%04X", value)
        }

        let cleanedMinor = minorHex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "0x", with: "")
            .uppercased()
        if let value = UInt16(cleanedMinor, radix: 16) {
            transmitter.minor = value
            minorHex = String(format: "%04X", value)
        }
    }
}

struct ENTabView: View {
    @StateObject private var transmitter = ENSimTransmitter()
    @State private var rpiHex: String = ""

    var body: some View {
        NavigationView {
            Form {
                Section {
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
                } header: {
                    Text("ステータス")
                }

                Section {
                    TextField("ローカル名", text: $transmitter.localName)
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                    Toggle("代替サービス UUID を使う", isOn: $transmitter.useAltService)

                    TextField("RPI (32 hex)", text: $rpiHex )
                        .textInputAutocapitalization(.none)
                        .autocorrectionDisabled(true)
                        .font(.system(.body, design: .monospaced))
                        .onSubmit(applyRpi)
                } header: {
                    Text("設定")
                }

                Section {
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
                    HStack {
                        Text ("EN API が 動作しないことを確認")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text("操作")
                }
            }
            .navigationTitle("EN API")
            .onAppear(perform: syncFieldsFromModel)
        }
    }
    private func syncFieldsFromModel() {
        rpiHex = transmitter.rpi.map { String(format: "%02X", $0) }.joined()
    }
    private func applyRpi() {
        let cleaned = rpiHex.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: " ", with: "").uppercased()
        guard let data = Data(hexString: cleaned, expectedLength: 16) else { return }
        transmitter.rpi = data
        rpiHex = cleaned
    }

}

struct FolkBearsTabView: View {
    @StateObject private var advertiser = GattAdvertise()

    var body: some View {
        NavigationView {
            Form {
                Section {
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
                } header: {
                    Text("ステータス")
                }

                Section {
                    Text(advertiser.getConnectionSummary())
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundStyle(.secondary)
                } header: {
                    Text("状態サマリ")
                }

                Section {
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
                } header: {
                    Text("操作")
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
                Section {
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
                } header: {
                    Text("ステータス")
                }

                Section {
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
                } header: {
                    Text("ペイロード")
                }

                Section {
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
                    HStack {
                        Text ("Manufacturer Data が 動作しないことを確認")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text("操作")
                }
            }
            .navigationTitle("MfD")
            .onAppear(perform: syncFieldsFromModel)
        }
    }

    private func syncFieldsFromModel() {
        companyIdText = String(format: "%04X", transmitter.companyId)

        let bytes = (0..<16).map { _ in UInt8.random(in: 0...255) }
        transmitter.tempIdBytes = Data(bytes)
        tempIdHex = bytes.map { String(format: "%02X", $0) }.joined()
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
