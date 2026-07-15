package com.luno.gateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.model.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    /** Returns the new rowId, or -1 when the id/commandId already exists (idempotent). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun findById(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE commandId = :commandId LIMIT 1")
    suspend fun findByCommandId(commandId: String): OutboxEntity?

    @Query("UPDATE outbox SET status = :status, attempt = :attempt, lastError = :lastError, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: OutboxStatus,
        attempt: Int,
        lastError: String?,
        updatedAt: Long,
    )

    @Query("SELECT * FROM outbox WHERE status = :status ORDER BY createdAt ASC")
    suspend fun findByStatus(status: OutboxStatus): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE status IN (:statuses)")
    fun observeDepth(statuses: List<OutboxStatus>): Flow<Int>

    @Query("SELECT id FROM outbox WHERE status IN (:statuses) ORDER BY updatedAt ASC")
    suspend fun terminalIdsOldestFirst(statuses: List<OutboxStatus>): List<String>

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
