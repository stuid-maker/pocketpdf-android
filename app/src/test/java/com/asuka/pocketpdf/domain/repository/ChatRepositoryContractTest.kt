package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChatRepository 接口契约测试：验证接口语义约束。
 */
class ChatRepositoryContractTest {

    private class FakeChatRepository : ChatRepository {
        private val messages = mutableMapOf<Long, MutableList<StoredChatMessage>>()
        private var nextId = 1L

        override fun observeMessages(documentId: Long): Flow<List<StoredChatMessage>> =
            flowOf(messages[documentId]?.toList() ?: emptyList())

        override suspend fun saveMessage(documentId: Long, message: ChatMessage) {
            val list = messages.getOrPut(documentId) { mutableListOf() }
            list.add(
                StoredChatMessage(
                    id = nextId++,
                    role = message.role,
                    content = message.content,
                ),
            )
        }

        override suspend fun clearHistory(documentId: Long) {
            messages.remove(documentId)
        }

        override suspend fun getHistorySnapshot(documentId: Long): List<StoredChatMessage> =
            messages[documentId]?.toList() ?: emptyList()
    }

    @Test
    fun `observeMessages returns empty for new document`() = runTest {
        val repo = FakeChatRepository()
        val msgs = repo.observeMessages(1L).first()
        assertTrue(msgs.isEmpty())
    }

    @Test
    fun `saveMessage then observeMessages returns saved message`() = runTest {
        val repo = FakeChatRepository()
        repo.saveMessage(1L, ChatMessage(role = "user", content = "hello"))

        val msgs = repo.observeMessages(1L).first()
        assertEquals(1, msgs.size)
        assertEquals("user", msgs[0].role)
        assertEquals("hello", msgs[0].content)
    }

    @Test
    fun `messages are scoped by documentId`() = runTest {
        val repo = FakeChatRepository()
        repo.saveMessage(1L, ChatMessage(role = "user", content = "doc1 msg"))
        repo.saveMessage(2L, ChatMessage(role = "assistant", content = "doc2 msg"))

        assertEquals(1, repo.observeMessages(1L).first().size)
        assertEquals(1, repo.observeMessages(2L).first().size)
    }

    @Test
    fun `clearHistory removes all messages`() = runTest {
        val repo = FakeChatRepository()
        repo.saveMessage(1L, ChatMessage(role = "user", content = "msg1"))
        repo.saveMessage(1L, ChatMessage(role = "assistant", content = "msg2"))
        repo.clearHistory(1L)

        val msgs = repo.observeMessages(1L).first()
        assertTrue(msgs.isEmpty())
    }

    @Test
    fun `clearHistory only clears specified document`() = runTest {
        val repo = FakeChatRepository()
        repo.saveMessage(1L, ChatMessage(role = "user", content = "keep"))
        repo.saveMessage(2L, ChatMessage(role = "user", content = "delete"))
        repo.clearHistory(2L)

        assertEquals(1, repo.observeMessages(1L).first().size)
        assertTrue(repo.observeMessages(2L).first().isEmpty())
    }

    @Test
    fun `messages preserve order`() = runTest {
        val repo = FakeChatRepository()
        repo.saveMessage(1L, ChatMessage(role = "user", content = "first"))
        repo.saveMessage(1L, ChatMessage(role = "assistant", content = "second"))

        val msgs = repo.observeMessages(1L).first()
        assertEquals("first", msgs[0].content)
        assertEquals("second", msgs[1].content)
    }
}
