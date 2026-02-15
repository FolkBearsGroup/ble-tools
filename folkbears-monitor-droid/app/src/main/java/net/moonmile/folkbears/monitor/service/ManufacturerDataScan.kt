package net.moonmile.folkbears.monitor.service

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Scans for BLE manufacturer data and forwards matches.
 * Optionally filters by manufacturer payload using a mask.
 */
class ManufacturerDataScan(
    private val context: Context,
    private val manufacturerId: Int,
    private val matchData: ByteArray? = null,
    private val matchMask: ByteArray? = null
) {

    companion object {
        private const val TAG = "ManufacturerDataScan"
    }

    data class ManufacturerRecord(
        val manufacturerId: Int,
        val payload: ByteArray,
        val rssi: Int,
        val txPower: Int,
        val deviceAddress: String,
        val timestamp: Long,
        val tempid: ByteArray
    )

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    // Callback invoked on each matching manufacturer record
    var onReadManufacturerData: (ManufacturerRecord) -> Unit = {}

    fun startScan() {
        Log.d(TAG, "startScan")
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return
        scanner = adapter.bluetoothLeScanner ?: return

        val filters = buildFilters()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    val payload = it.scanRecord?.manufacturerSpecificData?.get(manufacturerId)
                    if (payload != null && payload.isNotEmpty()) {
                        val beacon_type = payload[0]
                        val beacon_length = payload[1]
                        val tempid = payload.sliceArray(2 until 18)

                        if (beacon_type == 0x02.toByte() && beacon_length == 0x10.toByte()) {
                            val record = ManufacturerRecord(
                                manufacturerId = manufacturerId,
                                payload = payload,
                                rssi = it.rssi,
                                txPower = it.txPower,
                                deviceAddress = it.device.address,
                                timestamp = System.currentTimeMillis(),
                                tempid = tempid
                            )
                            Log.d(TAG, "manufacturer data detected: ${record.deviceAddress} id=${record.manufacturerId} payload=${payload.toHex()}")
                            onReadManufacturerData(record)
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "onScanFailed: error $errorCode")
                super.onScanFailed(errorCode)
            }
        }

        scanner?.startScan(filters, scanSettings, scanCallback)
    }

    fun stopScan() {
        Log.d(TAG, "stopScan")
        scanner?.stopScan(this.scanCallback)
        scanner = null
        scanCallback = null
    }

    private fun buildFilters(): List<ScanFilter> {
        val builder = ScanFilter.Builder()
        val data = matchData
        if (data != null) {
            val mask = matchMask ?: ByteArray(data.size) { 0xFF.toByte() }
            builder.setManufacturerData(manufacturerId, data, mask)
            return listOf(builder.build())
        }
        return emptyList()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte ->
        String.format(Locale.US, "%02X", eachByte)
    }
}
