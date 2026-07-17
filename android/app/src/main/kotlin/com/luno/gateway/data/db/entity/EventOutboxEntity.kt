package com.luno.gateway.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A node→backend event awaiting the backend's ack, held durably so it survives
 * process death (§7.4). [id] is the stable idempotency key (so a resend reuses it
 * and the backend dedupes); [payload] is the serialized wire [Event]; on ack the
 * row is deleted and [correlationId] drives the one known follow-up (marking an
 * inbox row ACKED for `sms_received`).
 */
@Entity(tableName = "event_outbox")
data class EventOutboxEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payload: String,
    val correlationId: String?,
    val createdAt: Long,
)
