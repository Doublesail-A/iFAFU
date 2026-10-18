package cn.ifafu.ifafu.db.dao

import androidx.room.*
import cn.ifafu.ifafu.entity.AgendaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaDao {
    @Query("SELECT * FROM agenda_item WHERE account=:account ORDER BY date, startMinute, id")
    suspend fun all(account: String): List<AgendaItem>
    @Query("SELECT * FROM agenda_item WHERE account=:account ORDER BY date, startMinute, id")
    fun observe(account: String): Flow<List<AgendaItem>>
    @Query("SELECT * FROM agenda_item WHERE id=:id AND account=:account")
    suspend fun find(id: String, account: String): AgendaItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: AgendaItem)
    @Query("DELETE FROM agenda_item WHERE id=:id AND account=:account")
    suspend fun delete(id: String, account: String)
}
