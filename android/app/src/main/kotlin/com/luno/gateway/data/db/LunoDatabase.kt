package com.luno.gateway.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luno.gateway.data.db.dao.EventOutboxDao
import com.luno.gateway.data.db.dao.InboxDao
import com.luno.gateway.data.db.dao.OutboxDao
import com.luno.gateway.data.db.dao.OutboxPartDao
import com.luno.gateway.data.db.entity.EventOutboxEntity
import com.luno.gateway.data.db.entity.InboxEntity
import com.luno.gateway.data.db.entity.OutboxEntity
import com.luno.gateway.data.db.entity.OutboxPartEntity

@Database(
    entities = [OutboxEntity::class, InboxEntity::class, OutboxPartEntity::class, EventOutboxEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LunoDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun inboxDao(): InboxDao
    abstract fun outboxPartDao(): OutboxPartDao
    abstract fun eventOutboxDao(): EventOutboxDao

    companion object {
        // v2 (M10): per-part delivery tracking for multipart messages.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox_part` (" +
                        "`messageId` TEXT NOT NULL, `partIndex` INTEGER NOT NULL, " +
                        "`transportRef` TEXT NOT NULL, `sentStatus` TEXT NOT NULL, " +
                        "`deliveryStatus` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`messageId`, `partIndex`), " +
                        "FOREIGN KEY(`messageId`) REFERENCES `outbox`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_outbox_part_messageId` " +
                        "ON `outbox_part` (`messageId`)",
                )
            }
        }

        // v3 (M15): durable node→backend event outbox for lossless resync (§7.4).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_outbox` (" +
                        "`id` TEXT NOT NULL, `type` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                        "`correlationId` TEXT, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        fun build(context: Context): LunoDatabase =
            Room.databaseBuilder(context, LunoDatabase::class.java, "luno.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
