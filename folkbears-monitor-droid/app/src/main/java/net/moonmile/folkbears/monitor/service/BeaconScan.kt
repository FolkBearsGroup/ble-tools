package net.moonmile.folkbears.monitor.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.moonmile.folkbears.monitor.data.IBeaconAdvertisement
import net.moonmile.folkbears.monitor.data.TraceDataEntity
import java.util.UUID

///
/// iBeacon のスキャンサービス
///
class BeaconScan(
    private val context: Context
) {

    companion object {
        const val TAG = "BeaconScan"
        private const val REQUEST_PERMISSIONS_CODE = 1001
        // val SERVICE_UUID: UUID = App.SERVICE_UUID
        val SERVICE_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA13")        // FolkBears サービス
    }
    private val traceDeviceRepository = TraceDeviceRepository()

    // Current scan mode (e.g., SCAN_MODE_LOW_POWER / SCAN_MODE_BALANCED / SCAN_MODE_LOW_LATENCY)
    private var scanMode: Int = ScanSettings.SCAN_MODE_LOW_POWER

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback : ScanCallback? = null

    // Beacon スキャン結果を受け取るコールバック
    var onReadTraceData: (TraceDataEntity) -> Unit = {}
    var onIBeacon: (IBeaconAdvertisement) -> Unit = {}

    private fun setupBeaconMonitoring() {
        Log.d(TAG, "setupBeaconMonitoring")
        if (!hasScanPermission()) {
            Log.w(TAG, "BLE scan permission not granted; requesting")
            requestScanPermission()
            return
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is not available")
            return
        }   
        val scanFilter = ScanFilter.Builder()
            .setManufacturerData(0x004C, byteArrayOf(0x02, 0x15)) // Apple iBeacon の識別データ
            // .setServiceUuid(ParcelUuid(SERVICE_UUID)) // フィルターが効かない
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    val parsed = parseIBeacon(scanResult) ?: return
                    val deviceAddress = scanResult.device?.address

                    val tempid = "%04x".format(parsed.major) + "%04x".format(parsed.minor)
                    val timestamp = parsed.timestamp
                    val dataEntity = TraceDataEntity(
                        tempId = tempid,
                        timestamp = timestamp,
                        rssi = parsed.rssi,
                        txPower = parsed.txPower
                    )
                    // traceDeviceRepository を使わない
                    onIBeacon(parsed)
                    /*
                    // 10秒以前を削除する
                    traceDeviceRepository.setTimestamp(timestamp = timestamp)
                    // デバイスアドレスが登録されていない場合のみ、データを読み込む
                    if (!traceDeviceRepository.checkMacAddress(deviceAddress ?: "")) {
                        traceDeviceRepository.readTempId(
                            mac = deviceAddress ?: "",
                            tempId = tempid,
                            timestamp = timestamp
                        )
                        onIBeacon(parsed)
                        // コールバックの呼び出し
                        onReadTraceData(dataEntity)
                    }
                    */
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "onScanResult: error")
                super.onScanFailed(errorCode)
            }
        }
        Log.d(TAG, "iBeacon スキャン開始")
        scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

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
        // Fallback for pre-Android 12
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
        // iBeacon payload size should be 23 bytes: 0x02 0x15 + UUID(16) + major(2) + minor(2) + tx(1)
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

    ///
    /// @brief Beacon スキャンサービスを開始する
    ///
    fun startScan(scanMode: Int = this.scanMode) {
        this.scanMode = scanMode
        Log.d(TAG, "startScan mode=$scanMode")
        setupBeaconMonitoring()
    }
    ///
    /// @brief Beacon スキャンサービスを停止する
    ///
    fun stopScan() {
        Log.d(TAG, "stopScan")
        scanner?.stopScan(this.scanCallback)
        scanner = null
    }
}