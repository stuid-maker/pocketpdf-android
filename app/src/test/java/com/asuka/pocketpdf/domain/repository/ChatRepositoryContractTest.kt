package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.Conversation
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChatRepository 接口契约测试：验证多会话语义约束。
 */
class ChatRepositoryContractTest {

    private class FakeChatRepository : ChatRepository {
        private val conversations = mutableMapOf<Long, Conversation>()
        private val messages = mutableMapOf<Long, MutableList<StoredChatMessage>>()
        private var nextConversationId = 1L
        private var nextMessageId = 1L

        override fun observeConversations(documentId: Long): Flow<List<Conversation>> =
            flowOf(conversations.values.filter { it.documentId == documentId }.sortedByDescending { it.updatedAt })

        override suspend fun getConversations(documentId: Long): List<Conversation> =
            conversations.values.filter { it.documentId == documentId }.sortedByDescending { it.updatedAt }

        override suspend fun createConversation(documentId: Long, title: String): Long {
            val id = nextConversationId++
            val now = id // deterministic monotonic stand-in for updatedAt
            conversations[id] = Conversation(id, documentId, title, now, now)
            messages[id] = mutableListOf()
            return id
        }

        override suspend fun renameConversation(conversationId: Long, title: String) {
            conversations[conversationId]?.let {
                conversations[conversationId] = it.copy(title = title)
            }
        }

        override suspend fun deleteConversation(conversationId: Long) {
            conversations.remove(conversationId)
            messages.remove(conversationId)
        }

        override fun observeMessages(conversationId: Long): Flow<List<StoredChatMessage>> =
            flowOf(messages[conversationId]?.toList() ?: emptyList())

        override suspend fun saveMessage(conversationId: Long, message: ChatMessage) {
            val list = messages.getOrPut(conversationId) { mutableListOf() }
            list.add(
                StoredChatMessage(
                    id = nextMessageId++,
                    role = message.role,
                    content = message.content,
                ),
            )
        }

        override suspend fun clearHistory(conversationId: Long) {
            messages[conversationId]?.clear()
        }

        override suspend fun getHistorySnapshot(conversationId: Long): List<StoredChatMessage> =
            messages[conversationId]?.toList() ?: emptyList()
    }

    @Test
    fun `observeMessages returns empty for new conversation`() = runTest {
        val repo = FakeChatRepository()
        val conversationId = repo.createConversation(1L, "对话 1")
        val msgs = repo.observeMessages(conversationId).first()
        assertTrue(msgs.isEmpty())
    }

    @Test
    fun `saveMessage then observeMessages returns saved message`() = runTest {
        val repo = FakeChatRepository()
        val conversationId = repo.createConversation(1L, "对话 1")
        repo.saveMessage(conversationId, ChatMessage(role = "user", content = "hello"))

        val msgs = repo.observeMessages(conversationId).first()
        assertEquals(1, msgs.size)
        assertEquals("user", msgs[0].role)
        assertEquals("hello", msgs[0].content)
    }

    @Test
    fun `messages are scoped by conversation`() = runTest {
        val repo = FakeChatRepository()
        val c1 = repo.createConversation(1L, "对话 1")
        val c2 = repo.createConversation(1L, "对话 2")
        repo.saveMessage(c1, ChatMessage(role = "user", content = "conv1 msg"))
        repo.saveMessage(c2, ChatMessage(role = "assistant", content = "conv2 msg"))

        assertEquals(1, repo.observeMessages(c1).first().size)
        assertEquals(1, repo.observeMessages(c2).first().size)
    }

    @Test
    fun `conversations are scoped by document`() = runTest {
        val repo = FakeChatRepository()
        repo.createConversation(1L, "doc1 conv")
        repo.createConversation(2L, "doc2 conv")

        assertEquals(1, repo.getConversations(1L).size)
        assertEquals(1, repo.getConversations(2L).size)
    }

    @Test
    fun `clearHistory removes all messages but keeps conversation`() = runTest {
        val repo = FakeChatRepository()
        val c1 = repo.createConversation(1L, "对话 1")
        repo.saveMessage(c1, ChatMessage(role = "user", content = "msg1"))
        repo.saveMessage(c1, ChatMessage(role = "assistant", content = "msg2"))
        repo.clearHistory(c1)

        assertTrue(repo.observeMessages(c1).first().isEmpty())
        assertEquals(1, repo.getConversations(1L).size)
    }

    @Test
    fun `clearHistory only clears specified conversation`() = runTest {
        val repo = FakeChatRepository()
        val c1 = repo.createConversation(1L, "对话 1")
        val c2 = repo.createConversation(1L, "对话 2")
        repo.saveMessage(c1, ChatMessage(role = "user", content = "keep"))
        repo.saveMessage(c2, ChatMessage(role = "user", content = "delete"))
        repo.clearHistory(c2)

        assertEquals(1, repo.observeMessages(c1).first().size)
        assertTrue(repo.observeMessages(c2).first().isEmpty())
    }

    @Test
    fun `deleteConversation removes conversation and its messages`() = runTest {
        val repo = FakeChatRepository()
        val c1 = repo.createConversation(1L, "对话 1")
        repo.saveMessage(c1, ChatMessage(role = "user", content = "msg"))
        repo.deleteConversation(c1)

        assertTrue(repo.getConversations(1L).isEmpty())
        assertTrue(repo.observeMessages(c1).first().isEmpty())
    }

    @Test
    fun `renameConversation updates title`() = runTest {
        val repo = FakeChatRepository()
        val c1 = repo.createConversation(1L, "对话 1")
        repo.renameConversation(c1, "重命名后")

        assertEquals("重命名后", repo.getConversations(1L).first().title)
    }

    @Test
    fun `messages preserve order`() = runTest {
        val repo = FakeChatRepository()
        val c1 = repo.createConversation(1L, "对话 1")
        repo.saveMessage(c1, ChatMessage(role = "user", content = "first"))
        repo.saveMessage(c1, ChatMessage(role = "assistant", content = "second"))

        val msgs = repo.observeMessages(c1).first()
        assertEquals("first", msgs[0].content)
        assertEquals("second", msgs[1].content)
    }
}
