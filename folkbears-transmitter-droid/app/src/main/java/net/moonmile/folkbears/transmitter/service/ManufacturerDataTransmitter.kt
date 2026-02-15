package net.moonmile.folkbears.transmitter.service

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manufacturer Specific Data で TempId をブロードキャストするトランスミッター。
 * フォーマット: [0]=0x02 (type), [1]=0x10 (length=16), [2..17]=TempId(16byte)
 */
class ManufacturerDataTransmitter(
	private val context: Context,
	private val manufacturerId: Int = 0xFFFF,
    private val tempIdBytes: ByteArray = ByteArray(16)
) {

	companion object {
		const val TAG = "ManufacturerDataTx"
	}

	private var advertiser: BluetoothLeAdvertiser? = null
	private var advertiseCallback: AdvertiseCallback? = null
	@Volatile
	private var isAdvertising = false
	@Volatile
	private var payload: ByteArray = ByteArray(0)

	init {
		// TempId を事前にロード
		CoroutineScope(Dispatchers.IO).launch {
			payload = buildPayload()
		}
	}

	///
	/// Manufacturer Data 発信開始
	///
	fun startTransmitter() {
		Log.d(TAG, "startTransmitter")
		if (isAdvertising) return
        startAdvertisingInternal()
	}

	///
	/// Manufacturer Data 発信停止
	///
	fun stopTransmitter() {
		Log.d(TAG, "stopTransmitter")
		advertiser?.stopAdvertising(advertiseCallback)
		advertiseCallback = null
		isAdvertising = false
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

		val data = AdvertiseData.Builder()
			.setIncludeDeviceName(false)
			.setIncludeTxPowerLevel(true)
			.addManufacturerData(manufacturerId, payload)
			.build()

		advertiseCallback = object : AdvertiseCallback() {
			override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
				super.onStartSuccess(settingsInEffect)
				isAdvertising = true
				Log.d(TAG, "Manufacturer advertise start (id=0x${manufacturerId.toString(16)})")
			}

			override fun onStartFailure(errorCode: Int) {
				super.onStartFailure(errorCode)
				isAdvertising = false
				Log.e(TAG, "Manufacturer advertise failed: $errorCode")
			}
		}

		try {
			adv.startAdvertising(settings, data, advertiseCallback)
		} catch (e: Exception) {
			isAdvertising = false
			Log.e(TAG, "startAdvertising exception: ${e.message}")
		}
	}

	private suspend fun buildPayload(): ByteArray {
		val currentTime = System.currentTimeMillis()
		if (tempIdBytes.size < 16) return ByteArray(0)

		// 0x02(type), 0x10(length=16), then 16-byte tempId
		val payload = ByteArray(2 + 16)
		payload[0] = 0x02
		payload[1] = 0x10
		tempIdBytes.copyInto(destination = payload, destinationOffset = 2, endIndex = 16)
		return payload
	}

	private fun String.toByteArrayFromHex(): ByteArray {
		if (length % 2 != 0) return ByteArray(0)
		return chunked(2)
			.mapNotNull { it.toIntOrNull(16)?.toByte() }
			.toByteArray()
	}
}
