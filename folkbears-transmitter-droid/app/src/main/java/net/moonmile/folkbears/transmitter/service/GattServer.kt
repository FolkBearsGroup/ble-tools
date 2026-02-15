package net.moonmile.folkbears.transmitter.service

import android.Manifest
import android.bluetooth.*
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GattServer(
    private val context: Context,
    private val tempIdBytes: ByteArray = ByteArray(16)

) {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var gattServer: BluetoothGattServer? = null
    
    private var tempUserId = ""

    companion object {
        const val TAG = "GattServer"
        val SERVICE_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA13")        // FolkBears サービス
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("90FA7ABE-FAB6-485E-B700-1A17804CAA14") // FolkBears キャラクタリスティック
    }

    private fun initGattServer() {
        Log.d(TAG, "initGattServer")
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(characteristic)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gattServer?.addService(service)
    }

    ///
    /// @brief GATT サーバーを開始する
    ///
    fun startGattServer() {
        Log.d(TAG, "startServer")
        if (gattServer == null) {
            initGattServer()
        }
    }

    ///
    /// @brief GATT サーバーを停止する
    ///
    fun stopGattServer() {
        Log.d(TAG, "stopServer")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gattServer?.close()
        gattServer = null
    }


    private val gattServerCallback = object : BluetoothGattServerCallback() {

        // ByteArray を UUID 形式で表示するための拡張関数
        private fun ByteArray.toUuidString(): String {
            require(size == 16) { "UUIDは16バイト必要です" }
            val hex = joinToString("") { "%02x".format(it) }
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}".uppercase()
        }    
        var tempUserId = tempIdBytes.toUuidString()
        
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "接続: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "切断: ${device.address}")
            }
        }


        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            // super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (characteristic?.uuid == CHARACTERISTIC_UUID) {
                // 送信中に、日付が変わった場合も考慮する
                val json = "{\"i\":\"$tempUserId\"}"
                val payload = json.toByteArray()
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, payload )
                Log.d(TAG, "Response TempID: $json")
            }
        }
    }
  	private fun String.toByteArrayFromHex(): ByteArray {
		if (length % 2 != 0) return ByteArray(0)
		return chunked(2)
			.mapNotNull { it.toIntOrNull(16)?.toByte() }
			.toByteArray()
	}

}
