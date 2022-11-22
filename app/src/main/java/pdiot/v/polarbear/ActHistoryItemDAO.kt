package pdiot.v.polarbear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActHistoryItemDAO {
    @Query("SELECT * FROM ActHistory")
    fun queryAll(): Flow<List<ActHistoryItem>>

    @Insert()
    fun insert(entity: ActHistoryItem)

    @Query("DELETE FROM ActHistory")
    fun delete()
}