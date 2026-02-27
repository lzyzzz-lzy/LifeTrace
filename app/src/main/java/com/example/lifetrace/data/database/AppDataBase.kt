package com.example.lifetrace.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lifetrace.data.database.converter.Converters
import com.example.lifetrace.data.database.dao.MemoryAttachmentDao
import com.example.lifetrace.data.database.dao.MemoryNodeDao
import com.example.lifetrace.data.database.dao.TrackPointDao
import com.example.lifetrace.data.database.dao.TripDao
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.entity.AttachmentType

@Database(
    entities = [
        TripEntity::class,
        TrackPointEntity::class,
        MemoryNodeEntity::class,
        MemoryAttachmentEntity::class
    ],
    version = 6
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun memoryNodeDao(): MemoryNodeDao
    abstract fun memoryAttachmentDao(): MemoryAttachmentDao

    companion object {

        // 数据库迁移：version 4 -> version 5
        val MIGRATION_4_TO_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建新表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_attachment (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        memoryNodeId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        thumbnailUri TEXT,
                        FOREIGN KEY(memoryNodeId) REFERENCES memory_node(id) ON DELETE CASCADE
                    )
                """)

                // 为旧数据创建 attachment
                // 将旧 MemoryNode 中的 contentUrl 迁移到新表
                database.execSQL("""
                    INSERT INTO memory_attachment (memoryNodeId, type, uri, duration, orderIndex)
                    SELECT id,
                           CASE type
                               WHEN 'PHOTO' THEN 'PHOTO'
                               WHEN 'AUDIO' THEN 'AUDIO'
                               WHEN 'VIDEO' THEN 'VIDEO'
                               ELSE 'PHOTO'
                           END as type,
                           contentUrl as uri,
                           0 as duration,
                           0 as orderIndex
                    FROM memory_node
                    WHERE contentUrl IS NOT NULL AND contentUrl != ''
                """)

                // 更新 MemoryNode 表结构
                database.execSQL("ALTER TABLE memory_node ADD COLUMN coverUri TEXT")
                database.execSQL("ALTER TABLE memory_node ADD COLUMN updatedAt INTEGER")
                database.execSQL("UPDATE memory_node SET updatedAt = timestamp")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lifetrace_db"
                )
                    .addMigrations(MIGRATION_4_TO_5)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
