using System;
using System.Collections.Generic;
using System.Linq;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Storage.Streams;

// BLEのスキャナ
BluetoothLEAdvertisementWatcher watcher;
// MACアドレスの保持（ランダムなので意味はない）
List<ulong> maclist = new List<ulong>();

Main(args);

void Main(string[] args)
{
    Console.WriteLine("BLE manufacturer data check");
    watcher = new BluetoothLEAdvertisementWatcher()
    {
        ScanningMode = BluetoothLEScanningMode.Passive
    };
    // スキャンしたときのコールバックを設定
    watcher.Received += Watcher_Received;
    // スキャン開始
    watcher.Start();
    // キーが押されるまで待つ
    Console.WriteLine("Press any key to continue");
    Console.ReadLine();
}

void Watcher_Received(
    BluetoothLEAdvertisementWatcher sender,
    BluetoothLEAdvertisementReceivedEventArgs args)
{
    var mac = string.Join(":",
                BitConverter.GetBytes(args.BluetoothAddress).Reverse()
                .Select(b => b.ToString("X2"))).Substring(6);
    var name = args.Advertisement.LocalName;
    var rssi = args.RawSignalStrengthInDBm;
    var time = args.Timestamp.ToString("yyyy/MM/dd HH:mm:ss.fff");
    
    foreach (var it in args.Advertisement.DataSections)
    {
        // AD type 0xFF: Manufacturer Specific Data
        if (it.DataType != 0xFF) continue;
        byte[] data = new byte[it.Data.Length];
        DataReader.FromBuffer(it.Data).ReadBytes(data);
        // Require Company ID (2 bytes) + RPI (16 bytes)
        if (data.Length < 2 + 16) continue;

        ushort companyId = BitConverter.ToUInt16(data, 0); // little endian
        if (companyId != 0xFFFF) continue; // 実験用 Company ID に一致したものだけ表示

        byte beacon_type = data[2];
        byte beacon_length = data[3];
        byte[] rpi = data[4..20];
        Console.WriteLine($"{time} [CID=0x{companyId:X4} RPI={tohex(rpi)}] {rssi} dBm {mac}");
    }
    // できるだけ raw data を全部表示する場合
    /*
    foreach (var section in args.Advertisement.DataSections)
    {
        byte[] buf = new byte[section.Data.Length];
        DataReader.FromBuffer(section.Data).ReadBytes(buf);
        Console.WriteLine($"AD 0x{section.DataType:X2} len={buf.Length} data={BitConverter.ToString(buf)}");
    }
    */
    string tohex( byte[] data )
    {
        return BitConverter.ToString(data).Replace("-", "").ToLower();
    }
}
