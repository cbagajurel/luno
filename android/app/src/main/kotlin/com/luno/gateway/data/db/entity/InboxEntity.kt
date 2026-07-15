package com.luno.gateway.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.luno.gateway.model.InboxStatus

@Entity(
    tableName = "inbox",
    indices = [Index(value = ["status"])],
)
data class InboxEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val body: String,
    val subscriptionId: Int?,
    val receivedAt: Long,
    val parts: Int,
    val status: InboxStatus,
    val createdAt: Long,
)
