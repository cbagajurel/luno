package com.luno.gateway.data.db

import androidx.room.TypeConverter
import com.luno.gateway.model.InboxStatus
import com.luno.gateway.model.OutboxStatus
import com.luno.gateway.model.PartDeliveryStatus
import com.luno.gateway.model.PartSentStatus

class Converters {
    @TypeConverter
    fun outboxStatusToString(status: OutboxStatus): String = status.name

    @TypeConverter
    fun outboxStatusFromString(value: String): OutboxStatus = OutboxStatus.valueOf(value)

    @TypeConverter
    fun inboxStatusToString(status: InboxStatus): String = status.name

    @TypeConverter
    fun inboxStatusFromString(value: String): InboxStatus = InboxStatus.valueOf(value)

    @TypeConverter
    fun partSentStatusToString(status: PartSentStatus): String = status.name

    @TypeConverter
    fun partSentStatusFromString(value: String): PartSentStatus = PartSentStatus.valueOf(value)

    @TypeConverter
    fun partDeliveryStatusToString(status: PartDeliveryStatus): String = status.name

    @TypeConverter
    fun partDeliveryStatusFromString(value: String): PartDeliveryStatus = PartDeliveryStatus.valueOf(value)
}
