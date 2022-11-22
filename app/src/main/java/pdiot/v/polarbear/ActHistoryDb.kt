package pdiot.v.polarbear

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ActHistoryItem::class], version = 1, exportSchema = false)
abstract class ActHistoryDb : RoomDatabase() {
    abstract fun getHistoryDao(): ActHistoryItemDAO
}