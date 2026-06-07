package com.asuka.pocketpdf.data.local.dao

import androidx.room.Room
import com.asuka.pocketpdf.data.local.AppDatabase
import com.asuka.pocketpdf.data.local.entity.ChunkEntity
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.domain.model.IndexStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChunkDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var chunkDao: ChunkDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
        // 允许在主线程执行查询（仅限测试）
        .allowMainThreadQueries()
        .build()

        documentDao = db.documentDao()
        chunkDao = db.chunkDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `cascade delete works when document is deleted`() = runTest {
        // 1. 插入 Document
        val docId = documentDao.insert(
            DocumentEntity(
                title = "test.pdf",
                uri = "content://test",
                pageCount = 1,
                indexStatus = IndexStatus.NOT_INDEXED.name,
                importedAt = 100L
            )
        )

        // 2. 插入对应的 Chunks
        val chunks = listOf(
            ChunkEntity(documentId = docId, pageIndex = 0, chunkIndex = 0, text = "Hello"),
            ChunkEntity(documentId = docId, pageIndex = 0, chunkIndex = 1, text = "World")
        )
        chunkDao.insertAll(chunks)

        // 验证插入成功
        var fetchedChunks = chunkDao.getChunksByDocumentId(docId)
        assertEquals(2, fetchedChunks.size)

        // 3. 删除 Document
        documentDao.deleteById(docId)

        // 4. 验证 Chunk 是否因级联（CASCADE）被自动删除
        fetchedChunks = chunkDao.getChunksByDocumentId(docId)
        assertTrue("Chunks should be automatically deleted", fetchedChunks.isEmpty())
    }

    @Test
    fun `replaceForDocument removes old chunks before inserting replacements`() = runTest {
        val docId = documentDao.insert(
            DocumentEntity(
                title = "test.pdf",
                uri = "content://test",
                pageCount = 2,
                indexStatus = IndexStatus.NOT_INDEXED.name,
                importedAt = 100L,
            ),
        )
        chunkDao.insertAll(
            listOf(
                ChunkEntity(
                    documentId = docId,
                    pageIndex = 0,
                    chunkIndex = 0,
                    text = "old",
                ),
            ),
        )
        val replacements = listOf(
            ChunkEntity(
                documentId = docId,
                pageIndex = 1,
                chunkIndex = 0,
                text = "new",
            ),
        )

        chunkDao.replaceForDocument(docId, replacements)

        val stored = chunkDao.getChunksByDocumentId(docId)
        assertEquals(1, stored.size)
        assertEquals("new", stored.single().text)
        assertEquals(1, stored.single().pageIndex)
    }
}
