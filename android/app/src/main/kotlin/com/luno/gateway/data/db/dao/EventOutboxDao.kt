package com.luno.gateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luno.gateway.data.db.entity.EventOutboxEntity

@Dao
interface EventOutboxDao {
    /** Upsert: re-persisting the same stable id (a resend attempt) just refreshes the row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: EventOutboxEntity)

    @Query("SELECT * FROM event_outbox WHERE id = :id")
    suspend fun findById(id: String): EventOutboxEntity?

    @Query("SELECT * FROM event_outbox ORDER BY createdAt ASC")
    suspend fun getAllOrdered(): List<EventOutboxEntity>

    @Query("DELETE FROM event_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM event_outbox")
    suspend fun count(): Int
}
