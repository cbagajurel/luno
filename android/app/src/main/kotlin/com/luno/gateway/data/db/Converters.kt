package com.luno.gateway.data.db

import androidx.room.TypeConverter
import com.luno.gateway.model.InboxStatus
import com.luno.gateway.model.OutboxStatus

class Converters {
    @TypeConverter
    fun outboxStatusToString(status: OutboxStatus): String = status.name

    @TypeConverter
    fun outboxStatusFromString(value: String): OutboxStatus = OutboxStatus.valueOf(value)

    @TypeConverter
    fun inboxStatusToString(status: InboxStatus): String = status.name

    @TypeConverter
    fun inboxStatusFromString(value: String): InboxStatus = InboxStatus.valueOf(value)
}
