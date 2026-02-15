package net.moonmile.folkbears.transmitter.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Exposure Notification Simulator の発信用トランスミッター。
 * TraceRepository から TempId を取得し、EN の Service Data としてアドバタイズする。
 */
class ENSimTransmitter(
	private val context: Context,
	tempIdBytes: ByteArray = ByteArray(16),
	useAltService: Boolean = false
) {

	companion object {
		const val TAG = "ENSimTransmitter"
		val SERVICE_UUID: UUID = UUID.fromString("0000FD6F-0000-1000-8000-00805F9B34FB")
		val SERVICE_UUID_ALT: UUID = UUID.fromString("0000FF00-0000-1000-8000-00805F9B34FB")
		val SERVICE_DATA_UUID_ALT: UUID = UUID.fromString("00000001-0000-1000-8000-00805F9B34FB")
	}

    var useAltService: Boolean = useAltService
    var tempIdBytes: ByteArray = tempIdBytes

	private var advertiser: BluetoothLeAdvertiser? = null
	private var advertiseCallback: AdvertiseCallback? = null
	@Volatile
	private var isAdvertising = false

	///
	/// ENSim の発信開始
	///
	fun startTransmitter() {
		Log.d(TAG, "startTransmitter")
        startAdvertisingInternal()
	}

	///
	/// ENSim の発信停止
	///
	fun stopTransmitter() {
		Log.d(TAG, "stopTransmitter")
		advertiser?.stopAdvertising(advertiseCallback)
	}

	private fun startAdvertisingInternal() {
        if ( advertiser == null ) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            advertiser = adapter.bluetoothLeAdvertiser
        }
		val adv = advertiser ?: run {
			Log.e(TAG, "BluetoothLeAdvertiser を取得できませんでした")
			return
		}

		val settings = AdvertiseSettings.Builder()
			.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
			.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
			.setConnectable(false)
			.build()

		val targetUuid = if (useAltService) SERVICE_UUID_ALT else SERVICE_UUID
		val dataUuidForPayload = if (useAltService) SERVICE_DATA_UUID_ALT else targetUuid

		val data = AdvertiseData.Builder()
			.setIncludeDeviceName(false)
			.setIncludeTxPowerLevel(true)
			.addServiceUuid(ParcelUuid(targetUuid))
			.addServiceData(ParcelUuid(dataUuidForPayload), tempIdBytes)
			.build()

		advertiseCallback = object : AdvertiseCallback() {
			override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
				super.onStartSuccess(settingsInEffect)
				isAdvertising = true
				Log.d(TAG, "ENSim advertise start")
			}

			override fun onStartFailure(errorCode: Int) {
				super.onStartFailure(errorCode)
				isAdvertising = false
				Log.e(TAG, "ENSim advertise failed: $errorCode")
			}
		}

		try {
			adv.startAdvertising(settings, data, advertiseCallback)
		} catch (e: Exception) {
			isAdvertising = false
			Log.e(TAG, "startAdvertising exception: ${e.message}")
		}
	}

	private fun String.toByteArrayFromHex(): ByteArray {
		if (length % 2 != 0) return ByteArray(0)
		return chunked(2)
			.mapNotNull { it.toIntOrNull(16)?.toByte() }
			.toByteArray()
	}
}
