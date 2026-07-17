package com.luno.gateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.model.InboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
    /** Returns the new rowId, or -1 when the id already exists (dedupe). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: InboxEntity): Long

    @Query("SELECT * FROM inbox WHERE id = :id")
    suspend fun findById(id: String): InboxEntity?

    @Query("UPDATE inbox SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: InboxStatus)

    @Query("SELECT COUNT(*) FROM inbox")
    suspend fun count(): Int

    @Query("SELECT * FROM inbox WHERE status = :status ORDER BY receivedAt ASC")
    fun observeByStatus(status: InboxStatus): Flow<List<InboxEntity>>

    @Query("SELECT * FROM inbox ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<InboxEntity>>

    @Query("SELECT * FROM inbox ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<InboxEntity>

    @Query("SELECT * FROM inbox ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InboxEntity>>
}
