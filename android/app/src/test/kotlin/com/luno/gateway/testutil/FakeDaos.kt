package com.luno.gateway.testutil

import com.luno.gateway.data.db.dao.InboxDao
import com.luno.gateway.data.db.dao.OutboxDao
import com.luno.gateway.data.db.dao.OutboxPartDao
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.data.db.entity.OutboxPartEntity
import com.luno.gateway.model.InboxStatus
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.PartDeliveryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory OutboxDao mirroring Room's OR IGNORE + unique-commandId semantics. */
class FakeOutboxDao : OutboxDao {
    val rows = LinkedHashMap<String, OutboxEntity>()
    private var rowCounter = 0L

    override suspend fun insertIfAbsent(entity: OutboxEntity): Long {
        if (rows.containsKey(entity.id)) return -1L
        if (entity.commandId != null && rows.values.any { it.commandId == entity.commandId }) return -1L
        rows[entity.id] = entity
        return ++rowCounter
    }

    override suspend fun findById(id: String): OutboxEntity? = rows[id]

    override suspend fun findByCommandId(commandId: String): OutboxEntity? =
        rows.values.firstOrNull { it.commandId == commandId }

    override suspend fun updateStatus(
        id: String,
        status: OutboxStatus,
        attempt: Int,
        lastError: String?,
        updatedAt: Long,
    ) {
        rows[id]?.let {
            rows[id] = it.copy(status = status, attempt = attempt, lastError = lastError, updatedAt = updatedAt)
        }
    }

    override suspend fun findByStatus(status: OutboxStatus): List<OutboxEntity> =
        rows.values.filter { it.status == status }.sortedBy { it.createdAt }

    override suspend fun recent(limit: Int): List<OutboxEntity> =
        rows.values.sortedByDescending { it.createdAt }.take(limit)

    override fun observeRecent(limit: Int): Flow<List<OutboxEntity>> =
        flowOf(rows.values.sortedByDescending { it.createdAt }.take(limit))

    override fun observeDepth(statuses: List<OutboxStatus>): Flow<Int> =
        flowOf(rows.values.count { it.status in statuses })

    override suspend fun terminalIdsOldestFirst(statuses: List<OutboxStatus>): List<String> =
        rows.values.filter { it.status in statuses }.sortedBy { it.updatedAt }.map { it.id }

    override suspend fun deleteByIds(ids: List<String>) {
        ids.forEach { rows.remove(it) }
    }
}

class FakeOutboxPartDao : OutboxPartDao {
    val rows = LinkedHashMap<String, OutboxPartEntity>()

    private fun key(messageId: String, partIndex: Int) = "$messageId#$partIndex"

    override suspend fun upsertAll(parts: List<OutboxPartEntity>) {
        parts.forEach { rows[key(it.messageId, it.partIndex)] = it }
    }

    override suspend fun partsFor(messageId: String): List<OutboxPartEntity> =
        rows.values.filter { it.messageId == messageId }.sortedBy { it.partIndex }

    override suspend fun countFor(messageId: String): Int =
        rows.values.count { it.messageId == messageId }

    override suspend fun deliveredCountFor(messageId: String): Int =
        rows.values.count { it.messageId == messageId && it.deliveryStatus == PartDeliveryStatus.DELIVERED }

    override suspend fun updateDeliveryStatus(
        messageId: String,
        partIndex: Int,
        status: PartDeliveryStatus,
        updatedAt: Long,
    ) {
        val k = key(messageId, partIndex)
        rows[k]?.let { rows[k] = it.copy(deliveryStatus = status, updatedAt = updatedAt) }
    }

    override suspend fun resolvePending(
        messageId: String,
        from: PartDeliveryStatus,
        to: PartDeliveryStatus,
        updatedAt: Long,
    ) {
        rows.values.filter { it.messageId == messageId && it.deliveryStatus == from }.forEach {
            rows[key(it.messageId, it.partIndex)] = it.copy(deliveryStatus = to, updatedAt = updatedAt)
        }
    }
}

class FakeInboxDao : InboxDao {
    val rows = LinkedHashMap<String, InboxEntity>()
    private var rowCounter = 0L

    override suspend fun insertIfAbsent(entity: InboxEntity): Long {
        if (rows.containsKey(entity.id)) return -1L
        rows[entity.id] = entity
        return ++rowCounter
    }

    override suspend fun findById(id: String): InboxEntity? = rows[id]

    override suspend fun updateStatus(id: String, status: InboxStatus) {
        rows[id]?.let { rows[id] = it.copy(status = status) }
    }

    override suspend fun count(): Int = rows.size

    override fun observeByStatus(status: InboxStatus): Flow<List<InboxEntity>> =
        flowOf(rows.values.filter { it.status == status }.sortedBy { it.receivedAt })

    override fun observeAll(): Flow<List<InboxEntity>> = flowOf(rows.values.toList())

    override suspend fun recent(limit: Int): List<InboxEntity> =
        rows.values.sortedByDescending { it.receivedAt }.take(limit)

    override fun observeRecent(limit: Int): Flow<List<InboxEntity>> =
        flowOf(rows.values.sortedByDescending { it.receivedAt }.take(limit))
}
