package com.luno.gateway.data

import com.luno.gateway.data.db.entity.OutboxPartEntity
import com.luno.gateway.data.repository.OutboxDeliveryRollup
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.PartDeliveryStatus
import com.luno.gateway.model.PartSentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutboxDeliveryRollupTest {
    private fun part(index: Int, delivery: PartDeliveryStatus) = OutboxPartEntity(
        messageId = "m1",
        partIndex = index,
        transportRef = "r$index",
        sentStatus = PartSentStatus.SENT,
        deliveryStatus = delivery,
        updatedAt = 0L,
    )

    @Test
    fun `all parts delivered rolls up to DELIVERED`() {
        val parts = listOf(part(0, PartDeliveryStatus.DELIVERED), part(1, PartDeliveryStatus.DELIVERED))
        assertEquals(OutboxStatus.DELIVERED, OutboxDeliveryRollup.status(parts))
    }

    @Test
    fun `any pending part keeps waiting (null)`() {
        val parts = listOf(part(0, PartDeliveryStatus.DELIVERED), part(1, PartDeliveryStatus.PENDING))
        assertNull(OutboxDeliveryRollup.status(parts))
    }

    @Test
    fun `one undelivered part rolls up to UNDELIVERED (worst-case)`() {
        val parts = listOf(part(0, PartDeliveryStatus.DELIVERED), part(1, PartDeliveryStatus.UNDELIVERED))
        assertEquals(OutboxStatus.UNDELIVERED, OutboxDeliveryRollup.status(parts))
    }

    @Test
    fun `no delivery-tracked parts yields null (nothing to roll up)`() {
        val parts = listOf(part(0, PartDeliveryStatus.NONE), part(1, PartDeliveryStatus.NONE))
        assertNull(OutboxDeliveryRollup.status(parts))
    }
}
