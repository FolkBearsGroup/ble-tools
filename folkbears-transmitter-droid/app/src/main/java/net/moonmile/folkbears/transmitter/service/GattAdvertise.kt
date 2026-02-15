package net.moonmile.folkbears.transmitter.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

class GattAdvertise(
    private val context: Context
)
{
    companion object {
        const val TAG = "GattAdvertise"
        val SERVICE_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA13")        // FolkBears サービス
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    @Volatile
    var isAdvertising = false
    private var lastStopTime = 0L
    private var backgroundRetryRunnable: Runnable? = null


    private var currentCallback: AdvertiseCallback? = null

    private fun createAdvertiseCallback(): AdvertiseCallback {
        return object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d(TAG, "Advertising onStartSuccess")
                isAdvertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                val reason: String

                when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> {
                        Log.w(TAG, "Advertising already started on Android ${Build.VERSION.SDK_INT}, forcing stop and retry")
                        return
                    }
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> {
                        reason = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED"
                        isAdvertising = false
                    }
                    ADVERTISE_FAILED_INTERNAL_ERROR -> {
                        reason = "ADVERTISE_FAILED_INTERNAL_ERROR"
                        isAdvertising = false
                    }
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> {
                        reason = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS"
                        isAdvertising = false
                    }
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                        reason = "ADVERTISE_FAILED_DATA_TOO_LARGE"
                        isAdvertising = false
                    }
                    else -> {
                        reason = "UNDOCUMENTED"
                        isAdvertising = false
                    }
                }
                Log.d(TAG, "Advertising onStartFailure: $errorCode - $reason")
            }
        }
    }

    private val settings = AdvertiseSettings.Builder()
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
        .setConnectable(true)
        // .setTimeout(0)
        .build()

    private var data: AdvertiseData? = null

    fun startAdvertising() {

        if (  advertiser == null ) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            advertiser = adapter.bluetoothLeAdvertiser
        }   

        if (isAdvertising) {
            Log.d(TAG, "Already advertising or starting: advertising=$isAdvertising")
            return
        }

        data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        currentCallback = createAdvertiseCallback()
        advertiser?.startAdvertising(settings, data, currentCallback)
    }

    fun stopAdvertising() {
        if ( isAdvertising == false ) {
            Log.d(TAG, "Not currently advertising, skipping stop")
            return
        }
        currentCallback?.let { advertiser?.stopAdvertising(it) }
        isAdvertising = false
    }
}