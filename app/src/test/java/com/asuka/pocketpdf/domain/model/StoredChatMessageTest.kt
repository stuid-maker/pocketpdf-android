package com.asuka.pocketpdf.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * StoredChatMessage 数据类测试：验证 companion 常量和构造函数。
 */
class StoredChatMessageTest {

    @Test
    fun `companion constants have expected values`() {
        assertEquals("user", StoredChatMessage.ROLE_USER)
        assertEquals("assistant", StoredChatMessage.ROLE_ASSISTANT)
    }

    @Test
    fun `constructs with all fields`() {
        val msg = StoredChatMessage(id = 42L, role = "user", content = "hello")
        assertEquals(42L, msg.id)
        assertEquals("user", msg.role)
        assertEquals("hello", msg.content)
    }

    @Test
    fun `constructs with assistant role`() {
        val msg = StoredChatMessage(id = 7L, role = StoredChatMessage.ROLE_ASSISTANT, content = "AI response")
        assertEquals(7L, msg.id)
        assertEquals("assistant", msg.role)
        assertEquals("AI response", msg.content)
    }

    @Test
    fun `constructs with zero id for unsaved messages`() {
        val msg = StoredChatMessage(id = 0L, role = "user", content = "unsaved")
        assertEquals(0L, msg.id)
    }

    @Test
    fun `data class equality works`() {
        val a = StoredChatMessage(id = 1L, role = "user", content = "test")
        val b = StoredChatMessage(id = 1L, role = "user", content = "test")
        assertEquals(a, b)
    }

    @Test
    fun `data class equality differs on id`() {
        val a = StoredChatMessage(id = 1L, role = "user", content = "test")
        val b = StoredChatMessage(id = 2L, role = "user", content = "test")
        assertEquals(false, a == b)
    }

    @Test
    fun `toString contains id role and content`() {
        val msg = StoredChatMessage(id = 3L, role = "user", content = "message text")
        val str = msg.toString()
        assertEquals(true, str.contains("3"))
        assertEquals(true, str.contains("user"))
        assertEquals(true, str.contains("message text"))
    }
}
