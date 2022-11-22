package pdiot.v.polarbear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ActHistory")
data class ActHistoryItem {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id", typeAffinity = ColumnInfo.INTEGER)
    val actId: Int = 0

    val actFlag: Int = 0
    val actName: String = ""
    val actStartTime: Long = 0
    val actEndTime: Long = 0
    val actInterval: Long = 0
}