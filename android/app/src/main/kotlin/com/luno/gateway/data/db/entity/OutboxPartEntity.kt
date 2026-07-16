package com.luno.gateway.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.luno.gateway.model.PartDeliveryStatus
import com.luno.gateway.model.PartSentStatus

/**
 * One part of a multipart outbound message. The message-level status in
 * [OutboxEntity] rolls up from these rows (§5): SENT when all parts sent,
 * DELIVERED only when all parts delivered, worst-case otherwise. Keyed on
 * (messageId, partIndex) so a replayed report is idempotent.
 */
@Entity(
    tableName = "outbox_part",
    primaryKeys = ["messageId", "partIndex"],
    foreignKeys = [
        ForeignKey(
            entity = OutboxEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId"])],
)
data class OutboxPartEntity(
    val messageId: String,
    val partIndex: Int,
    val transportRef: String,
    val sentStatus: PartSentStatus,
    val deliveryStatus: PartDeliveryStatus,
    val updatedAt: Long,
)
