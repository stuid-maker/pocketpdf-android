package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.dao.ConversationDao
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.data.local.entity.ConversationEntity
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryImplTest {

    private fun conversation(id: Long, documentId: Long) = ConversationEntity(
        id = id,
        documentId = documentId,
        title = "对话",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `observeMessages maps entities to stored messages`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        val entities = listOf(
            ChatMessageEntity(id = 1L, documentId = 1L, conversationId = 9L, role = "user", content = "hello", createdAt = 1000L),
            ChatMessageEntity(id = 2L, documentId = 1L, conversationId = 9L, role = "assistant", content = "hi", createdAt = 2000L),
        )
        every { dao.observeByConversationId(9L) } returns flowOf(entities)

        val repo = ChatRepositoryImpl(dao, conversationDao)
        val messages = repo.observeMessages(9L).first()

        assertEquals(2, messages.size)
        assertEquals(StoredChatMessage(id = 1L, role = "user", content = "hello"), messages[0])
        assertEquals(StoredChatMessage(id = 2L, role = "assistant", content = "hi"), messages[1])
    }

    @Test
    fun `observeMessages returns empty list when no entities`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        every { dao.observeByConversationId(9L) } returns flowOf(emptyList())

        val repo = ChatRepositoryImpl(dao, conversationDao)
        val messages = repo.observeMessages(9L).first()

        assertEquals(0, messages.size)
    }

    @Test
    fun `saveMessage inserts entity with conversation's documentId and conversationId`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        var capturedEntity: ChatMessageEntity? = null
        coEvery { conversationDao.getById(9L) } returns conversation(id = 9L, documentId = 1L)
        coEvery { conversationDao.updateTimestamp(any(), any()) } returns Unit
        coEvery { dao.insert(any()) } answers {
            capturedEntity = firstArg()
            1L
        }

        val repo = ChatRepositoryImpl(dao, conversationDao)
        repo.saveMessage(conversationId = 9L, message = ChatMessage(role = "user", content = "test message"))

        assertEquals(1L, capturedEntity!!.documentId)
        assertEquals(9L, capturedEntity!!.conversationId)
        assertEquals("user", capturedEntity!!.role)
        assertEquals("test message", capturedEntity!!.content)
        coVerify(exactly = 1) { conversationDao.updateTimestamp(9L, any()) }
    }

    @Test
    fun `saveMessage throws when conversation is missing`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        coEvery { conversationDao.getById(404L) } returns null

        val repo = ChatRepositoryImpl(dao, conversationDao)
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.saveMessage(conversationId = 404L, message = ChatMessage(role = "user", content = "orphan")) }
        }
        assertTrue(ex.message!!.contains("404"))

    }

    @Test
    fun `createConversation delegates to conversationDao insert`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        coEvery { conversationDao.insert(any()) } returns 42L

        val repo = ChatRepositoryImpl(dao, conversationDao)
        val id = repo.createConversation(documentId = 1L, title = "对话 1")

        assertEquals(42L, id)
        coVerify(exactly = 1) { conversationDao.insert(any()) }
    }

    @Test
    fun `clearHistory calls dao deleteByConversationId`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val conversationDao = mockk<ConversationDao>()
        coEvery { dao.deleteByConversationId(any()) } returns Unit

        val repo = ChatRepositoryImpl(dao, conversationDao)
        repo.clearHistory(conversationId = 3L)

        coVerify(exactly = 1) { dao.deleteByConversationId(3L) }
    }
}
