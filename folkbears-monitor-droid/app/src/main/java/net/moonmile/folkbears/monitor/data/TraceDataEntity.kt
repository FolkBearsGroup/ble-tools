package net.moonmile.folkbears.monitor.data


import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trace_data_table", primaryKeys = ["tempId", "timestamp"])
data class TraceDataEntity(
    // @PrimaryKey(autoGenerate = true) 
    // val id: Int = 0,
    @ColumnInfo(name = "tempId")
    var tempId: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "rssi")
    val rssi: Int = 0,
    @ColumnInfo(name = "txPower")
    val txPower: Int = 0,
    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false
) {
    override fun toString(): String {
        return "tempId: $tempId, " +
                "timestamp: $timestamp, " +
                "rssi: $rssi, " +
                "txPower: $txPower"
    }
}
