package net.moonmile.folkbears.monitor.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*
import android.content.Context
import org.json.JSONObject
import net.moonmile.folkbears.monitor.data.TraceDataEntity

class GattClient(
    private val context: Context
)  {

    companion object {
        const val TAG = "GattClient"
        // val SERVICE_UUID: UUID = App.SERVICE_UUID
        // val CHARACTERISTIC_UUID: UUID = App.CHARACTERISTIC_UUID
        val SERVICE_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA13")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA14")

    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanMode: Int = ScanSettings.SCAN_MODE_LOW_POWER
    private val traceDeviceRepository = TraceDeviceRepository()

    // スキャン結果を受け取るコールバック
    var onScanGattDevice: (String, ScanResult) -> Unit = { _, _ -> }
    var onReadTraceData: (TraceDataEntity) -> Unit = {}

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    ///
    /// @brief GATT クライアントサービスを開始する
    ///
    fun startSearchDevice(scanMode: Int = this.scanMode) 
    {
        this.scanMode = scanMode
        Log.d(TAG, "startSearchDevice mode=$scanMode")
        val scanFilter = ScanFilter.Builder()
            // TODO: SERVICE_UUID を提供しているデバイスを探す
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setReportDelay(0)
            .setScanMode(scanMode)
            .build()

        // デバイス名と時刻を保存するリスト
        this.scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if ( result.scanRecord == null ) return
                // MAC アドレスを取得
                val deviceAddress = result.device.address
                Log.d(TAG, "デバイス 検出: $deviceAddress")
                onScanGattDevice( deviceAddress, result )

                // traceDeviceRepository を使わない
                /*
                // 10秒以前を削除する
                traceDeviceRepository.setTimestamp(
                    timestamp = System.currentTimeMillis()
                )
                if (!traceDeviceRepository.checkMacAddress( deviceAddress )) {
                    // デバイスがリストに存在しない場合は接続
                    traceDeviceRepository.connectDevice(
                        deviceAddress, System.currentTimeMillis(), result.rssi)
                    connectToGattServer(deviceAddress)
                }
                */
            }
        }
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Log.d(TAG, "startSearchDevice startScan")
        bluetoothScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    ///
    /// @brief GATT クライアントサービスを停止する
    ///
    fun stopSearchDevice() {
        Log.d(TAG, "stopSearchDevice")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothScanner?.stopScan(this.scanCallback)
    }

    private fun connectToGattServer( deviceAddress: String ) {
        Log.d(TAG, "connectToGattServer: $deviceAddress")
        /*
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Bluetooth Connect permission not granted")
            return
        }
        */
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        device?.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT_ERROR($status)発生")
                gatt.close()
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                gatt.requestMtu(185) // iOS との互換性のために MTU サイズを 185 に設定
                Log.d(TAG, "接続成功")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "接続切断")
                gatt.close()
            }
        }
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged")
            gatt?.let {
                gatt.discoverServices() // サービスを探索
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    gatt.readCharacteristic(characteristic)
                }
            }
        }


        private fun onCharacteristicReadInner( gatt: BluetoothGatt, s: String ) {
            try {
                val device = traceDeviceRepository.findDevice( gatt.device.address )
                val rssi = device!!.rssi

                val json = JSONObject(s)
                val tempid = json.getString("i")
                Log.d(TAG, "TempID: $tempid")
                val timestamp = System.currentTimeMillis()
                val dataEntity = TraceDataEntity(
                    tempId = tempid,
                    timestamp = timestamp,
                    rssi = rssi,
                    txPower = 0
                )
                traceDeviceRepository.readTempId(
                    mac = gatt.device.address,
                    tempId = tempid,
                    timestamp = timestamp
                )                    
                // コールバックの呼び出し
                onReadTraceData(dataEntity)
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse error: $e")
            }
            // 切断する
            gatt.disconnect()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val size = value.size
                val s = String(value, Charsets.UTF_8)
                Log.d(TAG, "size: $size TempID: $s")
                onCharacteristicReadInner(gatt, s)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val dataBytes = characteristic.value
                val size = characteristic.value.size
                val s = String(dataBytes, Charsets.UTF_8)
                Log.d(TAG, "size: $size TempID: $s")
                onCharacteristicReadInner(gatt, s)
            }
        }
    }
}
