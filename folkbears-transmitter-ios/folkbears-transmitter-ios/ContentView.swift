//
//  ContentView.swift
//  folkbears-transmitter-ios
//
//  Created by masuda on 2026/02/15.
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            BeaconTabView()
                .tabItem {
                    Label("Beacon", systemImage: "antenna.radiowaves.left.and.right")
                }

            VStack(spacing: 16) {
                Image(systemName: "wave.3.right")
                    .imageScale(.large)
                    .foregroundStyle(.tint)
                Text("EN API")
                    .font(.title3)
                Text("Exposure Notification シミュレータ")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .tabItem {
                Label("EN API", systemImage: "wave.3.right")
            }

            VStack(spacing: 16) {
                Image(systemName: "bear")
                    .imageScale(.large)
                    .foregroundStyle(.tint)
                Text("FolkBears")
                    .font(.title3)
                Text("アプリ連携用タブ")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .tabItem {
                Label("FolkBears", systemImage: "bear")
            }

            VStack(spacing: 16) {
                Image(systemName: "shippingbox")
                    .imageScale(.large)
                    .foregroundStyle(.tint)
                Text("MfD")
                    .font(.title3)
                Text("Manufacturer Data 発信用")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .tabItem {
                Label("MfD", systemImage: "shippingbox")
            }
        }
    }
}

struct BeaconTabView: View {
    @StateObject private var transmitter = BeaconTransmitter()

    var body: some View {
        NavigationStack {
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

#Preview {
    ContentView()
}
