package com.asuka.pocketpdf.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.asuka.pocketpdf.data.local.converter.VectorTypeConverter
import com.asuka.pocketpdf.data.local.dao.AnnotationDao
import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.dao.ChunkDao
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.local.entity.AnnotationEntity
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.data.local.entity.ChunkEntity
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
 *
 * `exportSchema = true` 让 KSP 把每个版本的 schema 落到 `app/schemas/<dbname>/<version>.json`，
 * 这些 json 进 Git 后续 Code Review 能直观看到表结构 diff。
 */
@Database(
    entities = [
        DocumentEntity::class,
        ChunkEntity::class,
        ChatMessageEntity::class,
        AnnotationEntity::class,
    ],
    version = 6,
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
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val DATABASE_NAME = "pocketpdf.db"
    }
}
