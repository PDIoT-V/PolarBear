package pdiot.v.polarbear

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActHistoryItemDAO {
    @Query("SELECT * FROM ActHistory")
    fun getAllFlow(): Flow<List<ActHistoryItem>>

    @Query("SELECT * FROM ActHistory WHERE interval > 10000")
    fun getValidFlow(): Flow<List<ActHistoryItem>>

    @Query("SELECT * FROM ActHistory")
    fun getAll(): List<ActHistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: ActHistoryItem)

    @Query("DELETE FROM ActHistory")
    fun clear()
}