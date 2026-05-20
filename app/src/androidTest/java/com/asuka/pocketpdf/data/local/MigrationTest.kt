package com.asuka.pocketpdf.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1)

        // 插入版本 1 的数据
        db.execSQL("INSERT INTO documents (title, uri, pageCount, indexStatus, importedAt) VALUES ('Test', '/path', 10, 'NOT_INDEXED', 123456)")

        // 准备迁移
        db.close()

        // 重新用版本 2 打开，触发 AutoMigration
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        // 验证 chunk 能够被操作
        db.execSQL("INSERT INTO chunks (documentId, pageIndex, chunkIndex, text) VALUES (1, 0, 0, 'Hello')")
    }
}
