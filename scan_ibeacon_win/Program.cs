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
    var rssi = args.RawSignalStrengthInDBm;
    var time = args.Timestamp.ToString("yyyy/MM/dd HH:mm:ss.fff");
    

    if ( args.Advertisement.ManufacturerData.Count > 0)
    {
        var data = args.Advertisement.ManufacturerData[0];
        if ( data.CompanyId == 0x004c && data.Data.Length >= 23)
        {
            byte[] ibeacon = new byte[data.Data.Length];
            DataReader.FromBuffer(data.Data).ReadBytes(ibeacon);
            if (ibeacon[0] == 0x02 && ibeacon[1] == 0x15)
            {
                byte[] uuid = ibeacon[2..18];
                byte[] major = ibeacon[18..20];
                byte[] minor = ibeacon[20..22];
                byte txpower = ibeacon[22];

                int majorvalue = major[0] * 256 + major[1];
                int minorvalue = minor[0] * 256 + minor[1];
                Console.WriteLine($"{time} [{tohex(uuid)}] {rssi} dBm {mac} "
                    + string.Format("{0:x04}", majorvalue) + " "
                    + string.Format("{0:x04}", minorvalue) + " "
                    );
            }
        }
    }
    string tohex( byte[] data )
    {
        return BitConverter.ToString(data).Replace("-", "").ToLower();
    }
}
