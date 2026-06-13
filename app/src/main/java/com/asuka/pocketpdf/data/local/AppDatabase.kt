package com.asuka.pocketpdf.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.asuka.pocketpdf.data.local.converter.VectorTypeConverter
import com.asuka.pocketpdf.data.local.dao.AnnotationDao
import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.dao.ChunkDao
import com.asuka.pocketpdf.data.local.dao.ConversationDao
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.local.entity.AnnotationEntity
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.data.local.entity.ChunkEntity
import com.asuka.pocketpdf.data.local.entity.ConversationEntity
import com.asuka.pocketpdf.data.local.entity.DocumentEntity

/**
 * App 主数据库。
 *
 * 版本策略：
 * - W1 仅 [DocumentEntity]，version = 1
 * - W2 起追加 ChunkEntity → version = 2 + AutoMigration
 * - W4 追加 ChatMessageEntity → version = 3 + AutoMigration
 * - W5 追加 AnnotationEntity → version = 4 + AutoMigration
 * - T7 追加 extractorVersion → version = 5 + AutoMigration
 * - T8 追加 indexError → version = 6 + AutoMigration
 * - v1.3.0 追加 ConversationEntity，chat_messages 增加 conversationId → version = 7 + 手写 [MIGRATION_6_7]
 *
 * `exportSchema = true` 让 KSP 把每个版本的 schema 落到 `app/schemas/<dbname>/<version>.json`，
 * 这些 json 进 Git 后续 Code Review 能直观看到表结构 diff。
 */
@Database(
    entities = [
        DocumentEntity::class,
        ChunkEntity::class,
        ChatMessageEntity::class,
        ConversationEntity::class,
        AnnotationEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ]
)
@TypeConverters(VectorTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val DATABASE_NAME = "pocketpdf.db"

        /**
         * v6 → v7：引入多会话。
         *
         * AutoMigration 无法在新增 `conversationId` 外键列的同时回填历史数据，
         * 因此手写迁移：
         * 1. 建 `conversations` 表（含 documentId 索引）。
         * 2. 为每个已有聊天历史的文档建一条默认会话（"对话 1"），保留旧消息。
         * 3. 重建 `chat_messages` 表，新增 `conversationId` 外键列并从默认会话回填，
         *    SQLite 无法直接给已有表加外键，必须建新表 → 拷贝 → 替换。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`documentId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversations_documentId` " +
                        "ON `conversations` (`documentId`)"
                )

                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `conversations` (`documentId`, `title`, `createdAt`, `updatedAt`) " +
                        "SELECT DISTINCT `documentId`, '对话 1', $now, $now FROM `chat_messages`"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`documentId` INTEGER NOT NULL, " +
                        "`conversationId` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `chat_messages_new` " +
                        "(`id`, `documentId`, `conversationId`, `role`, `content`, `createdAt`) " +
                        "SELECT m.`id`, m.`documentId`, c.`id`, m.`role`, m.`content`, m.`createdAt` " +
                        "FROM `chat_messages` m " +
                        "JOIN `conversations` c ON c.`documentId` = m.`documentId`"
                )
                db.execSQL("DROP TABLE `chat_messages`")
                db.execSQL("ALTER TABLE `chat_messages_new` RENAME TO `chat_messages`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_documentId` " +
                        "ON `chat_messages` (`documentId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` " +
                        "ON `chat_messages` (`conversationId`)"
                )
            }
        }
    }
}
