package net.moonmile.folkbears.transmitter.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

class Ble5BeaconTransmitter(
    private val context: Context,
    major: Int = 0,
    minor: Int = 0
) {
    
    companion object {
        const val TAG = "Ble5BeaconTransmitter"
        val SERVICE_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA13")        // FolkBears サービス
    }
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertisingSetCallback: AdvertisingSetCallback? = null
    var major: Int = major
    var minor: Int = minor
    var advertiseMode: Int = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
    var advertiseTxPowerLevel: Int = AdvertiseSettings.ADVERTISE_TX_POWER_LOW

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

        if (advertiser == null) {
            advertiser = adapter.bluetoothLeAdvertiser
        }
        val adv = advertiser ?: run {
            Log.e(TAG, "BluetoothLeAdvertiser not available")
            return
        }

        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(false) // BLE5 extended advertising
            .setConnectable(false)
            .setScannable(false)
            .setInterval(modeToInterval(advertiseMode))
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .build()

        val payload = buildIBeaconPayload()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(0x004C, payload)
            .build()

        advertisingSetCallback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(advertisingSet: android.bluetooth.le.AdvertisingSet?, txPower: Int, status: Int) {
                if (status == 0 ) { // AdvertiseCallback.ADVERTISE_SUCCESS
                    Log.d(TAG, "BLE5 advertising started (txPower=$txPower)")
                } else {
                    Log.e(TAG, "BLE5 advertising start failed: status=$status")
                }
            }

            override fun onAdvertisingSetStopped(advertisingSet: android.bluetooth.le.AdvertisingSet?) {
                Log.d(TAG, "BLE5 advertising stopped")
            }
        }

        try {
            adv.startAdvertisingSet(parameters, data, /*scanResponse=*/null, /*periodicParameters=*/null, /*periodicData=*/null, advertisingSetCallback)
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
        startBeaconTransmission()
    }
    ///
    /// @brief Beacon の発信停止
    ///
    fun stopTransmitter() {
        Log.d(TAG, "stopTransmitter")
        advertisingSetCallback?.let { advertiser?.stopAdvertisingSet(it) }
        advertisingSetCallback = null
    }

    private fun modeToInterval(mode: Int): Int {
        return when (mode) {
            AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY -> AdvertisingSetParameters.INTERVAL_LOW
            AdvertiseSettings.ADVERTISE_MODE_BALANCED -> AdvertisingSetParameters.INTERVAL_MEDIUM
            else -> AdvertisingSetParameters.INTERVAL_HIGH
        }
    }

    private fun buildIBeaconPayload(): ByteArray {
        // iBeacon payload: 0x02 0x15 + UUID(16) + major(2) + minor(2) + txPower(1)
        val uuidBytes = ByteArray(16).apply {
            val bb = java.nio.ByteBuffer.wrap(this)
            val uuid = SERVICE_UUID
            bb.putLong(uuid.mostSignificantBits)
            bb.putLong(uuid.leastSignificantBits)
        }
        val payload = ByteArray(2 + 16 + 2 + 2 + 1)
        payload[0] = 0x02
        payload[1] = 0x15
        uuidBytes.copyInto(payload, destinationOffset = 2)
        payload[18] = (major shr 8 and 0xFF).toByte()
        payload[19] = (major and 0xFF).toByte()
        payload[20] = (minor shr 8 and 0xFF).toByte()
        payload[21] = (minor and 0xFF).toByte()
        payload[22] = -59 // default tx power calibration value
        return payload
    }
}

