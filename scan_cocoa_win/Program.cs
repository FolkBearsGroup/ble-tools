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
    Console.WriteLine("Folkbears ProveCheck");
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
    var uuids = args.Advertisement.ServiceUuids;
    var mac = string.Join(":",
                BitConverter.GetBytes(args.BluetoothAddress).Reverse()
                .Select(b => b.ToString("X2"))).Substring(6);
    var name = args.Advertisement.LocalName;
    var rssi = args.RawSignalStrengthInDBm;
    var time = args.Timestamp.ToString("yyyy/MM/dd HH:mm:ss.fff");
    
    if (uuids.Count == 0) return;
    // 0xFD6F は Exposure Notificadtion のサービスUUID
    if (uuids.FirstOrDefault(t => t.ToString() == "0000fd6f-0000-1000-8000-00805f9b34fb") == Guid.Empty) return;

    foreach (var it in args.Advertisement.DataSections)
    {
        if ( it.DataType == 0x16 && it.Data.Length >= 2 + 16)
        {
            byte[] data = new byte[it.Data.Length];
            DataReader.FromBuffer(it.Data).ReadBytes(data);
            if ( data[0] == 0x6f && data[1] == 0xfd)
            {
                byte[] rpi = data[2..18];
                Console.WriteLine($"{time} [{tohex(rpi)}] {rssi} dBm {mac}");
            }
        }
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
