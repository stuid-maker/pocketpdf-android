package com.asuka.pocketpdf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.local.entity.DocumentEntity

/**
 * App 主数据库。
 *
 * 版本策略：
 * - W1 仅 [DocumentEntity]，version = 1
 * - W2 起追加 ChunkEntity → version = 2 + AutoMigration
 * - W4 追加 ChatMessageEntity → version = 3 + AutoMigration
 *
 * `exportSchema = true` 让 KSP 把每个版本的 schema 落到 `app/schemas/<dbname>/<version>.json`，
 * 这些 json 进 Git 后续 Code Review 能直观看到表结构 diff。
 */
@Database(
    entities = [DocumentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        const val DATABASE_NAME = "pocketpdf.db"
    }
}
