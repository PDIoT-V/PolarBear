package pdiot.v.polarbear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "ActHistory")
data class ActHistoryItem @Ignore constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id", typeAffinity = ColumnInfo.INTEGER)
    var actId: Int? = null,

    @ColumnInfo(name = "flag", typeAffinity = ColumnInfo.INTEGER)
    var actFlag: Int = 0,

    @ColumnInfo(name = "name", typeAffinity = ColumnInfo.TEXT)
    var actName: String = "",

    @ColumnInfo(name = "start", typeAffinity = ColumnInfo.INTEGER)
    var actStartTime: Long = 0,

    @ColumnInfo(name = "end", typeAffinity = ColumnInfo.INTEGER)
    var actEndTime: Long = 0,

    @ColumnInfo(name = "interval", typeAffinity = ColumnInfo.INTEGER)
    var actInterval: Long = 0
){
    constructor(): this(0, 0, "", 0, 0, 0)
}