package net.moonmile.folkbears.monitor.service

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.moonmile.folkbears.monitor.data.IBeaconAdvertisement
import java.util.UUID

/**
 * BLE5 拡張広告の iBeacon をスキャンするクラス。
 * setLegacy(false) + PHY_LE_ALL_SUPPORTED で拡張広告も受信する。
 */
class Ble5IBeaconScan(private val context: Context) {

    companion object {
        const val TAG = "Ble5IBeaconScan"
        private const val REQUEST_PERMISSIONS_CODE = 2001
        val SERVICE_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA13") // FolkBears サービス
    }

    private var scanMode: Int = ScanSettings.SCAN_MODE_LOW_POWER
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    var onIBeacon: (IBeaconAdvertisement) -> Unit = {}

    private fun hasScanPermission(): Boolean {
        val scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val legacy = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        return scan || legacy
    }

    private fun requestScanPermission() {
        val activity = context as? Activity ?: run {
            Log.w(TAG, "Context is not Activity; cannot show permission dialog")
            return
        }
        val needs = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            needs += Manifest.permission.BLUETOOTH_SCAN
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            needs += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            needs += Manifest.permission.BLUETOOTH
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needs += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (needs.isEmpty()) return

        ActivityCompat.requestPermissions(activity, needs.toTypedArray(), REQUEST_PERMISSIONS_CODE)
    }

    private fun parseIBeacon(result: ScanResult): IBeaconAdvertisement? {
        val record = result.scanRecord ?: return null
        val payload = record.getManufacturerSpecificData(0x004C) ?: return null
        if (payload.size < 23) return null
        if (payload[0] != 0x02.toByte() || payload[1] != 0x15.toByte()) return null

        fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02X".format(eachByte) }

        val uuidBytes = payload.sliceArray(2 until 18)
        val serviceUuid = uuidBytes.toHex()
        val major = (payload[18].toInt() and 0xFF) * 256 + (payload[19].toInt() and 0xFF)
        val minor = (payload[20].toInt() and 0xFF) * 256 + (payload[21].toInt() and 0xFF)
        val txPower = payload[22].toInt()
        val rssi = result.rssi

        return IBeaconAdvertisement(
            serviceUuid = serviceUuid,
            major = major,
            minor = minor,
            timestamp = System.currentTimeMillis(),
            rssi = rssi,
            txPower = txPower
        )
    }

    fun startScan(scanMode: Int = this.scanMode) {
        this.scanMode = scanMode
        Log.d(TAG, "startScan mode=$scanMode")
        if (!hasScanPermission()) {
            requestScanPermission()
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is not available")
            return
        }

        val scanFilter = ScanFilter.Builder()
            .setManufacturerData(0x004C, byteArrayOf(0x02, 0x15))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setLegacy(false) // 拡張広告を受信
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    val parsed = parseIBeacon(scanResult) ?: return
                    onIBeacon(parsed)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "onScanFailed: error $errorCode")
                super.onScanFailed(errorCode)
            }
        }

        scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    fun stopScan() {
        Log.d(TAG, "stopScan")
        scanner?.stopScan(this.scanCallback)
        scanner = null
        scanCallback = null
    }
}
