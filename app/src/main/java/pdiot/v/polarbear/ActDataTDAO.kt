package pdiot.v.polarbear

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActDataTDAO {
    @Query("SELECT * FROM ActDataT")
    fun getAllFlowT(): Flow<List<ActDataItemT>>

    @Query("SELECT * FROM ActDataT WHERE id == (:actId)")
    fun getActDataT(actId: Int): Flow<List<ActDataItemT>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertT(entity: ActDataItemT)

    @Query("DELETE FROM ActDataT")
    fun clearT()
}