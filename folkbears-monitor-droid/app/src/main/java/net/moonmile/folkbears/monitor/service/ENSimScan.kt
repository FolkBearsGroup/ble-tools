package net.moonmile.folkbears.monitor.service

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import net.moonmile.folkbears.monitor.data.TraceDataEntity
import java.util.UUID

/**
 * Exposure Notification Simulator のスキャン処理。
 * iBeacon スキャンと同様に、重複検知を避けつつ受信結果を上位へ渡す。
 */
class ENSimScan(
    private val context: Context
) {

    companion object {
        const val TAG = "ENSimScan"
        val SERVICE_UUID: UUID = UUID.fromString("0000FD6F-0000-1000-8000-00805F9B34FB")
        val SERVICE_UUID_ALT: UUID = UUID.fromString("0000FF00-0000-1000-8000-00805F9B34FB")
        val SERVICE_DATA_UUID_ALT: UUID = UUID.fromString("00000001-0000-1000-8000-00805F9B34FB")
    }

    private val traceDeviceRepository = TraceDeviceRepository()
    private var scanMode: Int = ScanSettings.SCAN_MODE_LOW_POWER
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    // ENSim スキャン結果を受け取るコールバック
    var onReadTraceData: (TraceDataEntity) -> Unit = {}

    private fun setupScan() {
        Log.d(TAG, "setupScan")
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        scanner = adapter.bluetoothLeScanner

        // 16 bit UUID
        val serviceUuid = ParcelUuid(SERVICE_UUID)
        val serviceUuidAlt = ParcelUuid(SERVICE_UUID_ALT)

        var scanFilters = listOf(
            ScanFilter.Builder().setServiceUuid(serviceUuid).build(),
            ScanFilter.Builder().setServiceUuid(serviceUuidAlt).build()
        )
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02X".format(eachByte) }

                result?.let {
                    if ( it.scanRecord?.serviceUuids?.contains(serviceUuid) == true ) {
                        val serviceData = it.scanRecord?.getServiceData(serviceUuid)
                        if (serviceData != null && serviceData.isNotEmpty()) {
                            val tempId = serviceData.toHex()
                            val deviceAddress = result.device?.address ?: ""
                            val timestamp = System.currentTimeMillis()
                            val rssi = result.rssi
                            val txPower = result.txPower

                            Log.d(TAG, "ENSim 検出: $deviceAddress tempId:$tempId rssi:$rssi tx:$txPower")

                            val dataEntity = TraceDataEntity(
                                tempId = tempId,
                                timestamp = timestamp,
                                rssi = rssi,
                                txPower = txPower
                            )
                            onReadTraceData(dataEntity)
                            return 
                        }
                    }
                    if ( it.scanRecord?.serviceUuids?.contains(serviceUuidAlt) == true ) {
                        val serviceData = it.scanRecord?.getServiceData(ParcelUuid(SERVICE_DATA_UUID_ALT))
                        if (serviceData != null && serviceData.isNotEmpty()) {
                            val tempId = serviceData.toHex()
                            val deviceAddress = result.device?.address ?: ""
                            val timestamp = System.currentTimeMillis()
                            val rssi = result.rssi
                            val txPower = result.txPower

                            Log.d(TAG, "ENSim Alt 検出: $deviceAddress tempId:$tempId rssi:$rssi tx:$txPower")

                            val dataEntity = TraceDataEntity(
                                tempId = tempId,
                                timestamp = timestamp,
                                rssi = rssi,
                                txPower = txPower
                            )
                            onReadTraceData(dataEntity)
                            return 
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "onScanResult: error $errorCode")
                super.onScanFailed(errorCode)
            }
        }

        Log.d(TAG, "ENSim スキャン開始")
        scanner?.startScan(scanFilters, scanSettings, scanCallback)
    }

    ///
    /// ENSim スキャンサービスを開始する
    ///
    fun startScan(scanMode: Int = this.scanMode) {
        this.scanMode = scanMode
        Log.d(TAG, "startScan mode=$scanMode")
        setupScan()
    }

    ///
    /// ENSim スキャンサービスを停止する
    ///
    fun stopScan() {
        Log.d(TAG, "stopScan")
        scanner?.stopScan(this.scanCallback)
        scanner = null
    }
}
