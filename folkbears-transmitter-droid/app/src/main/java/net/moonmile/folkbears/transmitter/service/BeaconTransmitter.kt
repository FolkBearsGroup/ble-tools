package net.moonmile.folkbears.transmitter.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.altbeacon.beacon.Beacon
import java.util.UUID
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter

class BeaconTransmitter(
    private val context: Context,
    major: Int = 0,
    minor: Int = 0
) {
    
    companion object {
        const val TAG = "BeaconTransmitter"
        val SERVICE_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA13")        // FolkBears サービス
    }
    private var beaconTransmitter: org.altbeacon.beacon.BeaconTransmitter? = null
    var major: Int = major
    var minor: Int = minor


    init {

        CoroutineScope(Dispatchers.IO).launch {
            // TempUserId の取得
            // 先頭4バイトを16進数として変換
            // major = tempUserId.substring(0, 4).toIntOrNull(16) ?: 0
            // 次の4バイトを16進数として変換
            // minor = tempUserId.substring(4, 8).toIntOrNull(16) ?: 0
        }
    }


    private fun startBeaconTransmission() {
        // Permission check (Android 12+ requires BLUETOOTH_ADVERTISE)
        val advertiseGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_ADVERTISE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!advertiseGranted) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission not granted; cannot start advertising")
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter not available")
            return
        }
        if (!adapter.isEnabled) {
            Log.e(TAG, "BluetoothAdapter disabled; enable Bluetooth and retry")
            return
        }

        val support = org.altbeacon.beacon.BeaconTransmitter.checkTransmissionSupported(context)
        if (support != org.altbeacon.beacon.BeaconTransmitter.SUPPORTED) {
            Log.e(TAG, "Beacon transmission not supported: code=$support")
            return
        }

        val beacon = Beacon.Builder()
            .setId1(SERVICE_UUID.toString()) // UUID
            .setId2(major.toString()) // Major (10進数文字列)
            .setId3(minor.toString()) // Minor (10進数文字列)
            .setManufacturer(0x004C) // Apple iBeacon のメーカーコード
            .setTxPower(-59) // 信号強度 (dBm)は仮設定
            .build()

        // val beaconParser = BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT)
        val beaconParser = BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        beaconTransmitter = org.altbeacon.beacon.BeaconTransmitter(context, beaconParser)
        try {
            beaconTransmitter?.startAdvertising(beacon, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.d(TAG, "iBeacon 発信開始")
                }
                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "iBeacon 発信に失敗: $errorCode")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when starting advertising: ${e.message}")
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error when starting advertising: ${e.message}")
        }
    }

    ///
    /// @break Beacon の発信開始
    ///
    fun startTransmitter() {
        Log.d(TAG, "startTransmitter")
        if (beaconTransmitter == null ) {
            startBeaconTransmission()
        }
    }
    ///
    /// @brief Beacon の発信停止
    ///
    fun stopTransmitter() {
        Log.d(TAG, "stopTransmitter")
        beaconTransmitter?.stopAdvertising()
        beaconTransmitter = null
    }
}

