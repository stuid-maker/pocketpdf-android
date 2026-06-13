package com.asuka.pocketpdf.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        AppDatabase::class.java,
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

    @Test
    @Throws(IOException::class)
    fun migrate6To7BackfillsDefaultConversation() {
        var db = helper.createDatabase(TEST_DB, 6)

        // v6 数据：一个文档 + 两条聊天消息（旧 schema，无 conversationId）
        db.execSQL(
            "INSERT INTO documents (title, uri, pageCount, indexStatus, importedAt, extractorVersion) " +
                "VALUES ('Doc', '/path', 3, 'INDEXED', 1, 0)"
        )
        db.execSQL("INSERT INTO chat_messages (documentId, role, content, createdAt) VALUES (1, 'user', 'hi', 10)")
        db.execSQL("INSERT INTO chat_messages (documentId, role, content, createdAt) VALUES (1, 'assistant', 'hello', 20)")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)

        // 为该文档回填了一条默认会话
        var defaultConversationId = -1L
        db.query("SELECT id, documentId FROM conversations").use { cursor ->
            assertTrue("a default conversation should exist", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("documentId")))
            defaultConversationId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        }

        // 旧消息被保留，且都指向默认会话
        db.query("SELECT conversationId, content FROM chat_messages ORDER BY id").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(defaultConversationId, cursor.getLong(cursor.getColumnIndexOrThrow("conversationId")))
            assertEquals("hi", cursor.getString(cursor.getColumnIndexOrThrow("content")))
            cursor.moveToNext()
            assertEquals(defaultConversationId, cursor.getLong(cursor.getColumnIndexOrThrow("conversationId")))
            assertEquals("hello", cursor.getString(cursor.getColumnIndexOrThrow("content")))
        }
    }
}
