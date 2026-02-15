package net.moonmile.folkbears.monitor.service

data class TraceDeviceEntity(
    val mac : String,
    val tempId: String? = null,
    val timestamp: Long,
    val rssi: Int = 0,
    val status : Int = TraceDeviceEntity.STATUS_NONE,
) {
    companion object {
        const val STATUS_NONE = 0
        const val STATUS_CONNECT = 1
        const val STATUS_READ = 2
    }
}

class TraceDeviceRepository {

    companion object {
        const val TAG = "TraceDeviceRepository"
        const val DEVICE_INTERVAL = 10000 // デバイスの有効期間 (10秒)
    }

    private val deviceList = mutableListOf<TraceDeviceEntity>()

    ///
    /// @brief デバイスの数を取得
    /// @return デバイスの数
    ///
    fun count(): Int {
        return deviceList.size
    }   

    ///
    /// @brief MAC アドレスを持つデバイスを探す
    /// @param mac MAC アドレス
    /// @return デバイスが見つかった場合は true, 見つからなかった場合は false
    ///
    fun checkMacAddress(mac: String): Boolean {
        return deviceList.any { it.mac == mac }
    }

    ///
    /// @brief デバイスに接続する
    /// @param mac MAC アドレス
    /// @param timestamp 接続時刻
    /// @param status 接続状態
    /// @return なし
    ///
    fun connectDevice(mac: String, timestamp: Long, rssi: Int = 0) {
        if (!checkMacAddress(mac)) {
            deviceList.add(
                TraceDeviceEntity(
                    mac = mac, 
                    timestamp = timestamp, 
                    rssi = rssi,
                    status = TraceDeviceEntity.STATUS_CONNECT))
        }
    }

    ///
    /// @brief デバイスから TempID を読み取って追加
    /// @param mac MAC アドレス
    /// @param tempId 読み取った TempID
    /// @param timestamp 読み取り時刻
    /// @return なし   
    /// 
    fun readTempId(
        mac: String,
        tempId: String,
        timestamp: Long,
    ) {
        val device = deviceList.find { it.mac == mac }
        if (device != null) {
            // GATT 接続の場合は、書き換え
            deviceList.remove(device)
            deviceList.add(
                TraceDeviceEntity(
                    mac = mac,
                    tempId = tempId,
                    timestamp = timestamp,
                    status = TraceDeviceEntity.STATUS_READ
                )
            )
        } else {
            // Beacon の場合は新規追加
            deviceList.add(
                TraceDeviceEntity(
                    mac = mac,
                    tempId = tempId,
                    timestamp = timestamp,
                    status = TraceDeviceEntity.STATUS_READ
                )
            )
        }
    }

    ///
    /// @brief タイムスタンプを設定し、10秒前のデバイスを削除する
    /// @param timestamp タイムスタンプ
    /// @return なし    
    ///
    fun setTimestamp(timestamp: Long) {
        // 10秒前のデバイスを削除
        deviceList.removeIf { it.timestamp <= timestamp - DEVICE_INTERVAL }
    }

    ///
    /// @brief MAC アドレスからデバイスを取得
    /// @param mac MAC アドレス
    /// @return デバイスが見つかった場合は TraceDeviceEntity, 見つからなかった場合は null
    ///
    fun findDevice(mac: String): TraceDeviceEntity? {
        return deviceList.find { it.mac == mac }
    }

}
