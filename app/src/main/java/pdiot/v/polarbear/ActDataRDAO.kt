package pdiot.v.polarbear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActDataRDAO {
    @Query("SELECT * FROM ActDataR")
    fun getAllFlowR(): Flow<List<ActDataItemR>>

    @Query("SELECT * FROM ActDataR WHERE id == (:actId)")
    fun getActDataR(actId: Int): Flow<List<ActDataItemR>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertR(entity: ActDataItemR)

    @Query("DELETE FROM ActDataR")
    fun clearR()
}