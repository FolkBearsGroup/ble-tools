package net.moonmile.folkbears.monitor.data

/**
 * iBeacon の受信結果を UI へ渡すシンプルなモデル。
 */
data class IBeaconAdvertisement(
    val serviceUuid: String,
    val major: Int,
    val minor: Int,
    val timestamp: Long,
    val rssi: Int,
    val txPower: Int
)
