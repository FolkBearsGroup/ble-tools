using System;
using System.Collections.Generic;
using System.Linq;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Storage.Streams;

// BLEのスキャナ
BluetoothLEAdvertisementWatcher watcher;

Main(args);

void Main(string[] args)
{
    Console.WriteLine("Folkbears iBeaconCheck");

    watcher = new BluetoothLEAdvertisementWatcher()
    {
        // Allow extended (non-legacy) advertisements so we can see BLE5 advertising sets
        AllowExtendedAdvertisements = true,
        // UseCodedPhy = true, // Coded PHY も受信する
        
        // Active の方がドライバによっては Extended 受信が安定する場合がある
        ScanningMode = BluetoothLEScanningMode.Active
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
    var rssi = args.RawSignalStrengthInDBm;
    var time = args.Timestamp.ToString("yyyy/MM/dd HH:mm:ss.fff");
    var advType = args.AdvertisementType;

    bool printed = true;

    if (args.Advertisement.ManufacturerData.Count > 0)
    {
        foreach (var data in args.Advertisement.ManufacturerData)
        {
            if (data.CompanyId != 0x004c || data.Data.Length < 23)
                continue;

            byte[] ibeacon = new byte[data.Data.Length];
            DataReader.FromBuffer(data.Data).ReadBytes(ibeacon);
            if (ibeacon[0] != 0x02 || ibeacon[1] != 0x15)
                continue; // not iBeacon

            byte[] uuid = ibeacon[2..18];
            byte[] major = ibeacon[18..20];
            byte[] minor = ibeacon[20..22];
            byte txpower = ibeacon[22];

            int majorvalue = major[0] * 256 + major[1];
            int minorvalue = minor[0] * 256 + minor[1];
            Console.WriteLine($"{time} [{tohex(uuid)}] {rssi} dBm {mac} "
                + string.Format("{0:x04}", majorvalue) + " "
                + string.Format("{0:x04}", minorvalue) + " "
                + $"type={advType} len={data.Data.Length}");
            printed = true;
        }
    }

    // デバッグ用: ManufacturerDataが無い拡張広告も記録して、受信有無を確認
    if (!printed)
    {
        var sectionInfo = args.Advertisement.DataSections
            .Select(s => $"0x{s.DataType:X2}/{s.Data.Length}");
        Console.WriteLine($"{time} ADV type={advType} rssi={rssi} mac={mac} sections=[{string.Join(',', sectionInfo)}]");
    }
    string tohex( byte[] data )
    {
        return BitConverter.ToString(data).Replace("-", "").ToLower();
    }
}
