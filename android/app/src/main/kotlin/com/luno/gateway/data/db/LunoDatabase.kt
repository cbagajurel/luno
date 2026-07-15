package com.luno.gateway.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luno.gateway.data.db.dao.InboxDao
import com.luno.gateway.data.db.dao.OutboxDao
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.data.db.entity.OutboxEntity

@Database(
    entities = [OutboxEntity::class, InboxEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LunoDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun inboxDao(): InboxDao

    companion object {
        fun build(context: Context): LunoDatabase =
            Room.databaseBuilder(context, LunoDatabase::class.java, "luno.db").build()
    }
}
