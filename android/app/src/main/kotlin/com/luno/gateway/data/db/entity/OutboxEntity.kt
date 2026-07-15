package com.luno.gateway.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.luno.gateway.model.OutboxStatus

@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["commandId"], unique = true),
        Index(value = ["status"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey val id: String,
    val commandId: String?,
    val recipient: String,
    val body: String,
    val subscriptionId: Int?,
    val requestDeliveryReport: Boolean,
    val ref: String?,
    val status: OutboxStatus,
    val attempt: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
