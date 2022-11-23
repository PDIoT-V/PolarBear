package pdiot.v.polarbear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "ActDataR")
data class ActDataItemR @Ignore constructor(
    @PrimaryKey(autoGenerate = true)
    var actTimeStamp: Long = 0L,

    @ColumnInfo(name = "id", typeAffinity = ColumnInfo.INTEGER)
    var actId: Int? = null,

    @ColumnInfo(name = "respeckAccX")
    var actRespeckAccX: Float = 0F,

    @ColumnInfo(name = "respeckAccY")
    var actRespeckAccY: Float = 0F,

    @ColumnInfo(name = "respeckAccZ")
    var actRespeckAccZ: Float = 0F,

    @ColumnInfo(name = "respeckGyrX")
    var actRespeckGyrX: Float = 0F,

    @ColumnInfo(name = "respeckGyrY")
    var actRespeckGyrY: Float = 0F,

    @ColumnInfo(name = "respeckGyrZ")
    var actRespeckGyrZ: Float = 0F,

    ){
    constructor(): this(0L, 0,
        0F, 0F,0F,
        0F,0F,0F)
}
