package com.asuka.pocketpdf.data.local.dao

import androidx.room.Room
import com.asuka.pocketpdf.data.local.AppDatabase
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.data.local.entity.ConversationEntity
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.domain.model.IndexStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var conversationDao: ConversationDao
    private lateinit var chatMessageDao: ChatMessageDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        documentDao = db.documentDao()
        conversationDao = db.conversationDao()
        chatMessageDao = db.chatMessageDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun insertDocument(): Long = documentDao.insert(
        DocumentEntity(
            title = "test.pdf",
            uri = "content://test",
            pageCount = 1,
            indexStatus = IndexStatus.NOT_INDEXED.name,
            importedAt = 100L,
        ),
    )

    private fun conversation(documentId: Long, title: String, updatedAt: Long) = ConversationEntity(
        documentId = documentId,
        title = title,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `conversations are ordered by updatedAt desc`() = runTest {
        val docId = insertDocument()
        conversationDao.insert(conversation(docId, "old", updatedAt = 100L))
        conversationDao.insert(conversation(docId, "new", updatedAt = 300L))
        conversationDao.insert(conversation(docId, "mid", updatedAt = 200L))

        val list = conversationDao.observeByDocumentId(docId).first()

        assertEquals(listOf("new", "mid", "old"), list.map { it.title })
    }

    @Test
    fun `updateTitle and updateTimestamp persist`() = runTest {
        val docId = insertDocument()
        val id = conversationDao.insert(conversation(docId, "对话 1", updatedAt = 100L))

        conversationDao.updateTitle(id, "重命名")
        conversationDao.updateTimestamp(id, 500L)

        val loaded = conversationDao.getById(id)!!
        assertEquals("重命名", loaded.title)
        assertEquals(500L, loaded.updatedAt)
    }

    @Test
    fun `countByDocumentId reflects inserts`() = runTest {
        val docId = insertDocument()
        assertEquals(0, conversationDao.countByDocumentId(docId))
        conversationDao.insert(conversation(docId, "a", 1L))
        conversationDao.insert(conversation(docId, "b", 2L))
        assertEquals(2, conversationDao.countByDocumentId(docId))
    }

    @Test
    fun `deleting conversation cascades to its messages`() = runTest {
        val docId = insertDocument()
        val convId = conversationDao.insert(conversation(docId, "对话 1", 1L))
        chatMessageDao.insert(
            ChatMessageEntity(
                documentId = docId,
                conversationId = convId,
                role = "user",
                content = "hello",
            ),
        )
        assertEquals(1, chatMessageDao.getByConversationId(convId).size)

        conversationDao.deleteById(convId)

        assertNull(conversationDao.getById(convId))
        assertTrue(chatMessageDao.getByConversationId(convId).isEmpty())
    }

    @Test
    fun `deleting document cascades to conversations`() = runTest {
        val docId = insertDocument()
        conversationDao.insert(conversation(docId, "对话 1", 1L))
        conversationDao.insert(conversation(docId, "对话 2", 2L))
        assertEquals(2, conversationDao.countByDocumentId(docId))

        documentDao.deleteById(docId)

        assertTrue(conversationDao.observeByDocumentId(docId).first().isEmpty())
    }
}
