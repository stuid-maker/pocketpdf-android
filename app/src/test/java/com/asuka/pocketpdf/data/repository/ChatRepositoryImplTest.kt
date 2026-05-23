package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRepositoryImplTest {

    @Test
    fun `observeMessages maps entities to stored messages`() = runTest {
        val dao = mockk<ChatMessageDao>()
        val entities = listOf(
            ChatMessageEntity(id = 1L, documentId = 1L, role = "user", content = "hello", createdAt = 1000L),
            ChatMessageEntity(id = 2L, documentId = 1L, role = "assistant", content = "hi", createdAt = 2000L),
        )
        every { dao.observeByDocumentId(1L) } returns flowOf(entities)

        val repo = ChatRepositoryImpl(dao)
        val messages = repo.observeMessages(1L).first()

        assertEquals(2, messages.size)
        assertEquals(StoredChatMessage(id = 1L, role = "user", content = "hello"), messages[0])
        assertEquals(StoredChatMessage(id = 2L, role = "assistant", content = "hi"), messages[1])
    }

    @Test
    fun `observeMessages returns empty list when no entities`() = runTest {
        val dao = mockk<ChatMessageDao>()
        every { dao.observeByDocumentId(1L) } returns flowOf(emptyList())

        val repo = ChatRepositoryImpl(dao)
        val messages = repo.observeMessages(1L).first()

        assertEquals(0, messages.size)
    }

    @Test
    fun `saveMessage inserts entity with correct fields`() = runTest {
        val dao = mockk<ChatMessageDao>()
        var capturedEntity: ChatMessageEntity? = null
        coEvery { dao.insert(any()) } answers {
            capturedEntity = firstArg()
            1L
        }

        val repo = ChatRepositoryImpl(dao)
        repo.saveMessage(documentId = 1L, message = ChatMessage(role = "user", content = "test message"))

        assertEquals(1L, capturedEntity!!.documentId)
        assertEquals("user", capturedEntity!!.role)
        assertEquals("test message", capturedEntity!!.content)
    }

    @Test
    fun `saveMessage passes different documentId and message correctly`() = runTest {
        val dao = mockk<ChatMessageDao>()
        var capturedEntity: ChatMessageEntity? = null
        coEvery { dao.insert(any()) } answers {
            capturedEntity = firstArg()
            2L
        }

        val repo = ChatRepositoryImpl(dao)
        repo.saveMessage(
            documentId = 5L,
            message = ChatMessage(role = "assistant", content = "response text")
        )

        assertEquals(5L, capturedEntity!!.documentId)
        assertEquals("assistant", capturedEntity!!.role)
        assertEquals("response text", capturedEntity!!.content)
    }

    @Test
    fun `clearHistory calls dao deleteByDocumentId`() = runTest {
        val dao = mockk<ChatMessageDao>()
        coEvery { dao.deleteByDocumentId(any()) } returns Unit

        val repo = ChatRepositoryImpl(dao)
        repo.clearHistory(documentId = 3L)

        coVerify(exactly = 1) { dao.deleteByDocumentId(3L) }
    }
}
