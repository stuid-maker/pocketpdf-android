package com.asuka.pocketpdf.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ChatMessage 数据类测试：验证 companion 常量和构造函数。
 */
class ChatMessageTest {

    @Test
    fun `companion constants have expected values`() {
        assertEquals("user", ChatMessage.ROLE_USER)
        assertEquals("assistant", ChatMessage.ROLE_ASSISTANT)
        assertEquals("system", ChatMessage.ROLE_SYSTEM)
    }

    @Test
    fun `constructs with role and content`() {
        val msg = ChatMessage(role = "user", content = "hello")
        assertEquals("user", msg.role)
        assertEquals("hello", msg.content)
    }

    @Test
    fun `constructs with assistant role`() {
        val msg = ChatMessage(role = ChatMessage.ROLE_ASSISTANT, content = "response")
        assertEquals("assistant", msg.role)
        assertEquals("response", msg.content)
    }

    @Test
    fun `constructs with system role`() {
        val msg = ChatMessage(role = ChatMessage.ROLE_SYSTEM, content = "You are a helpful assistant.")
        assertEquals("system", msg.role)
        assertEquals("You are a helpful assistant.", msg.content)
    }

    @Test
    fun `data class equality works`() {
        val a = ChatMessage(role = "user", content = "test")
        val b = ChatMessage(role = "user", content = "test")
        assertEquals(a, b)
    }

    @Test
    fun `data class equality fails on different role`() {
        val a = ChatMessage(role = "user", content = "test")
        val b = ChatMessage(role = "assistant", content = "test")
        assertEquals(false, a == b)
    }

    @Test
    fun `toString contains role and content`() {
        val msg = ChatMessage(role = "user", content = "hello world")
        val str = msg.toString()
        assertEquals(true, str.contains("user"))
        assertEquals(true, str.contains("hello world"))
    }
}
