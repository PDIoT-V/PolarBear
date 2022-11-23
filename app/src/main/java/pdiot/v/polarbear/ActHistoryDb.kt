package pdiot.v.polarbear

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ActHistoryItem::class, ActDataItemR::class, ActDataItemT::class], version = 1, exportSchema = false)
abstract class ActHistoryDb : RoomDatabase() {
    abstract fun getHistoryDao(): ActHistoryItemDAO
    abstract fun getDataRDao(): ActDataRDAO
    abstract fun getDataTDao(): ActDataTDAO
}