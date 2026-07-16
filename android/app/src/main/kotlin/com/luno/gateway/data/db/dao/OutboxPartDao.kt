package com.luno.gateway.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luno.gateway.data.db.entity.OutboxPartEntity
import com.luno.gateway.model.PartDeliveryStatus

@Dao
interface OutboxPartDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(parts: List<OutboxPartEntity>)

    @Query("SELECT * FROM outbox_part WHERE messageId = :messageId ORDER BY partIndex ASC")
    suspend fun partsFor(messageId: String): List<OutboxPartEntity>

    @Query("SELECT COUNT(*) FROM outbox_part WHERE messageId = :messageId")
    suspend fun countFor(messageId: String): Int

    @Query("SELECT COUNT(*) FROM outbox_part WHERE messageId = :messageId AND deliveryStatus = 'DELIVERED'")
    suspend fun deliveredCountFor(messageId: String): Int

    @Query(
        "UPDATE outbox_part SET deliveryStatus = :status, updatedAt = :updatedAt " +
            "WHERE messageId = :messageId AND partIndex = :partIndex",
    )
    suspend fun updateDeliveryStatus(
        messageId: String,
        partIndex: Int,
        status: PartDeliveryStatus,
        updatedAt: Long,
    )

    @Query(
        "UPDATE outbox_part SET deliveryStatus = :to, updatedAt = :updatedAt " +
            "WHERE messageId = :messageId AND deliveryStatus = :from",
    )
    suspend fun resolvePending(
        messageId: String,
        from: PartDeliveryStatus,
        to: PartDeliveryStatus,
        updatedAt: Long,
    )
}
