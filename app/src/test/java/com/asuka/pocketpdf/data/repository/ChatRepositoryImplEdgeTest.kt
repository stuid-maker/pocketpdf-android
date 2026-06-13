package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.dao.ConversationDao
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.data.local.entity.ConversationEntity
import com.asuka.pocketpdf.domain.model.ChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChatRepositoryImpl 扩展测试：覆盖更多边界情况。
 */
class ChatRepositoryImplEdgeTest {

    private fun conversation(id: Long, documentId: Long) = ConversationEntity(
        id = id,
        documentId = documentId,
        title = "对话",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `observeMessages handles empty entities`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        every { dao.observeByConversationId(9L) } returns flowOf(emptyList())

        val repo = ChatRepositoryImpl(dao, conversationDao)
        val messages = repo.observeMessages(9L).first()

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `observeMessages maps multiple entities correctly`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        val entities = (1..5).map {
            ChatMessageEntity(id = it.toLong(), documentId = 1L, conversationId = 9L, role = "user", content = "msg$it", createdAt = it * 1000L)
        }
        every { dao.observeByConversationId(9L) } returns flowOf(entities)

        val repo = ChatRepositoryImpl(dao, conversationDao)
        val messages = repo.observeMessages(9L).first()

        assertEquals(5, messages.size)
        assertEquals("msg1", messages[0].content)
        assertEquals("msg5", messages[4].content)
    }

    @Test
    fun `saveMessage with system role saves correctly`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        var capturedContent = ""
        coEvery { conversationDao.getById(9L) } returns conversation(id = 9L, documentId = 1L)
        coEvery { conversationDao.updateTimestamp(any(), any()) } returns Unit
        coEvery { dao.insert(any()) } answers {
            val entity = firstArg<ChatMessageEntity>()
            capturedContent = entity.content
            1L
        }

        val repo = ChatRepositoryImpl(dao, conversationDao)
        repo.saveMessage(9L, ChatMessage(role = "system", content = "You are a helper"))

        assertEquals("You are a helper", capturedContent)
    }

    @Test
    fun `clearHistory on conversation with no history does not throw`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        coEvery { dao.deleteByConversationId(any()) } returns Unit

        val repo = ChatRepositoryImpl(dao, conversationDao)
        repo.clearHistory(999L)

        coVerify(exactly = 1) { dao.deleteByConversationId(999L) }
    }

    @Test
    fun `saveMessage inserts once per call`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        var callCount = 0
        coEvery { conversationDao.getById(9L) } returns conversation(id = 9L, documentId = 1L)
        coEvery { conversationDao.updateTimestamp(any(), any()) } returns Unit
        coEvery { dao.insert(any()) } answers {
            callCount++
            callCount.toLong()
        }

        val repo = ChatRepositoryImpl(dao, conversationDao)
        repo.saveMessage(9L, ChatMessage(role = "user", content = "first"))
        repo.saveMessage(9L, ChatMessage(role = "assistant", content = "second"))

        assertEquals(2, callCount)
        coVerify(exactly = 2) { dao.insert(any()) }
    }

    @Test
    fun `observeMessages preserves entities order as returned by DAO`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        val entities = listOf(
            ChatMessageEntity(id = 1L, documentId = 1L, conversationId = 9L, role = "user", content = "first", createdAt = 1000L),
            ChatMessageEntity(id = 2L, documentId = 1L, conversationId = 9L, role = "assistant", content = "second", createdAt = 2000L),
            ChatMessageEntity(id = 3L, documentId = 1L, conversationId = 9L, role = "user", content = "third", createdAt = 3000L),
        )
        every { dao.observeByConversationId(9L) } returns flowOf(entities)

        val repo = ChatRepositoryImpl(dao, conversationDao)
        val messages = repo.observeMessages(9L).first()

        assertEquals(3, messages.size)
        assertEquals("first", messages[0].content)
        assertEquals("second", messages[1].content)
        assertEquals("third", messages[2].content)
    }
}
