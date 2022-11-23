package pdiot.v.polarbear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "ActDataT")
data class ActDataItemT @Ignore constructor(
    @PrimaryKey(autoGenerate = true)
    var actTimeStamp: Long = 0L,

    @ColumnInfo(name = "id", typeAffinity = ColumnInfo.INTEGER)
    var actId: Int? = null,

    @ColumnInfo(name = "ThingyAccX")
    var actThingyAccX: Float = 0F,

    @ColumnInfo(name = "ThingyAccY")
    var actThingyAccY: Float = 0F,

    @ColumnInfo(name = "ThingyAccZ")
    var actThingyAccZ: Float = 0F,

    @ColumnInfo(name = "ThingyGyrX")
    var actThingyGyrX: Float = 0F,

    @ColumnInfo(name = "ThingyGyrY")
    var actThingyGyrY: Float = 0F,

    @ColumnInfo(name = "ThingyGyrZ")
    var actThingyGyrZ: Float = 0F,

    @ColumnInfo(name = "ThingyMagX")
    var actThingyMagX: Float = 0F,

    @ColumnInfo(name = "ThingyMagY")
    var actThingyMagY: Float = 0F,

    @ColumnInfo(name = "ThingyMagZ")
    var actThingyMagZ: Float = 0F,

    ){
    constructor(): this(0L, 0,
        0F, 0F,0F,
        0F,0F,0F,
        0F,0F,0F)
}
