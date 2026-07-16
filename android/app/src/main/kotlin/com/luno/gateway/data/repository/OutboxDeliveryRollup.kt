package com.luno.gateway.data.repository

import com.luno.gateway.data.db.entity.OutboxPartEntity
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.PartDeliveryStatus

/**
 * Rolls per-part delivery state up to a message-level terminal status (§5): the
 * message is DELIVERED only when every tracked part is DELIVERED, UNDELIVERED if
 * any tracked part failed or timed out. Returns null while any tracked part is
 * still PENDING (keep waiting), or when no part is delivery-tracked. Pure.
 */
object OutboxDeliveryRollup {
    fun status(parts: List<OutboxPartEntity>): OutboxStatus? {
        val tracked = parts.filter { it.deliveryStatus != PartDeliveryStatus.NONE }
        if (tracked.isEmpty()) return null
        if (tracked.any { it.deliveryStatus == PartDeliveryStatus.PENDING }) return null
        return if (tracked.all { it.deliveryStatus == PartDeliveryStatus.DELIVERED }) {
            OutboxStatus.DELIVERED
        } else {
            OutboxStatus.UNDELIVERED
        }
    }
}
