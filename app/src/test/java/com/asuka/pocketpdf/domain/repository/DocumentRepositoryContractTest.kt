package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.IndexStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * DocumentRepository 接口契约测试：验证接口语义约束。
 *
 * 这些测试通过 fake 实现验证接口约定，确保实现类遵循相同契约。
 */
class DocumentRepositoryContractTest {

    /** 一个轻量 fake 实现，用于验证接口契约 */
    private class FakeDocumentRepository(
        private val initialDocuments: List<Document> = emptyList(),
        private val initialChunks: List<DocumentChunk> = emptyList(),
    ) : DocumentRepository {
        private val docs = initialDocuments.toMutableList()
        private val chunks = initialChunks.toMutableList()

        override fun observeDocuments(): Flow<List<Document>> = flowOf(docs.toList())

        override suspend fun getDocument(id: Long): Document? = docs.find { it.id == id }

        override suspend fun updateDocument(document: Document): Result<Unit> {
            val idx = docs.indexOfFirst { it.id == document.id }
            if (idx == -1) return Result.Failure(IllegalStateException("Not found"))
            docs[idx] = document
            return Result.Success(Unit)
        }

        override suspend fun importDocument(
            sourceUri: String,
            displayName: String,
        ): Result<Document> {
            val doc = Document(
                id = docs.size + 1L,
                title = displayName,
                uri = "/tmp/${displayName}",
                pageCount = 1,
                indexStatus = IndexStatus.NOT_INDEXED,
                importedAt = System.currentTimeMillis(),
            )
            docs.add(doc)
            return Result.Success(doc)
        }

        override suspend fun deleteDocument(id: Long): Result<Unit> {
            val removed = docs.removeAll { it.id == id }
            return if (removed) Result.Success(Unit)
            else Result.Failure(IllegalStateException("Document #$id not found"))
        }

        override suspend fun replaceChunks(
            documentId: Long,
            chunks: List<DocumentChunk>,
        ): Result<Unit> {
            this.chunks.removeAll { it.documentId == documentId }
            this.chunks.addAll(chunks)
            return Result.Success(Unit)
        }

        override suspend fun getChunks(documentId: Long): List<DocumentChunk> =
            chunks.filter { it.documentId == documentId }

        override suspend fun getChunksByPage(
            documentId: Long,
            pageIndex: Int,
        ): List<DocumentChunk> =
            chunks.filter { it.documentId == documentId && it.pageIndex == pageIndex }
    }

    @Test
    fun `observeDocuments returns flow of documents`() = runTest {
        val repo = FakeDocumentRepository()
        val docs = repo.observeDocuments().first()
        assertNotNull(docs)
        assertTrue(docs.isEmpty())
    }

    @Test
    fun `getDocument returns null for non-existent id`() = runTest {
        val repo = FakeDocumentRepository()
        assertNull(repo.getDocument(999L))
    }

    @Test
    fun `getDocument returns document after import`() = runTest {
        val repo = FakeDocumentRepository()
        val imported = repo.importDocument("content://test", "test.pdf")
        assertTrue(imported is Result.Success)
        val doc = repo.getDocument((imported as Result.Success).data.id)
        assertNotNull(doc)
        assertEquals("test.pdf", doc!!.title)
    }

    @Test
    fun `deleteDocument removes document`() = runTest {
        val repo = FakeDocumentRepository()
        repo.importDocument("content://test", "test.pdf")
        val doc = repo.observeDocuments().first().first()
        val result = repo.deleteDocument(doc.id)
        assertTrue(result is Result.Success)
        assertNull(repo.getDocument(doc.id))
    }

    @Test
    fun `deleteDocument returns Failure for non-existent id`() = runTest {
        val repo = FakeDocumentRepository()
        val result = repo.deleteDocument(999L)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `importDocument creates document with NOT_INDEXED status`() = runTest {
        val repo = FakeDocumentRepository()
        val result = repo.importDocument("content://src", "new.pdf")
        assertTrue(result is Result.Success)
        assertEquals(IndexStatus.NOT_INDEXED, (result as Result.Success).data.indexStatus)
    }

    @Test
    fun `replaceChunks and getChunks round-trip`() = runTest {
        val repo = FakeDocumentRepository()
        val chunks = listOf(
            DocumentChunk(documentId = 1L, pageIndex = 0, chunkIndex = 0, text = "chunk0"),
            DocumentChunk(documentId = 1L, pageIndex = 1, chunkIndex = 0, text = "chunk1"),
        )
        val saveResult = repo.replaceChunks(1L, chunks)
        assertTrue(saveResult is Result.Success)

        val retrieved = repo.getChunks(1L)
        assertEquals(2, retrieved.size)
    }

    @Test
    fun `getChunksByPage filters by page index`() = runTest {
        val repo = FakeDocumentRepository()
        repo.replaceChunks(
            1L,
            listOf(
                DocumentChunk(documentId = 1L, pageIndex = 0, chunkIndex = 0, text = "p0c0"),
                DocumentChunk(documentId = 1L, pageIndex = 0, chunkIndex = 1, text = "p0c1"),
                DocumentChunk(documentId = 1L, pageIndex = 1, chunkIndex = 0, text = "p1c0"),
            ),
        )
        val page0 = repo.getChunksByPage(1L, 0)
        assertEquals(2, page0.size)
        val page1 = repo.getChunksByPage(1L, 1)
        assertEquals(1, page1.size)
    }

    @Test
    fun `replaceChunks removes previous chunks for document`() = runTest {
        val oldChunk = DocumentChunk(
            documentId = 1L,
            pageIndex = 0,
            chunkIndex = 0,
            text = "old",
        )
        val repo = FakeDocumentRepository(initialChunks = listOf(oldChunk))
        val replacement = DocumentChunk(
            documentId = 1L,
            pageIndex = 1,
            chunkIndex = 0,
            text = "new",
        )

        repo.replaceChunks(1L, listOf(replacement))

        assertEquals(listOf(replacement), repo.getChunks(1L))
    }

    @Test
    fun `updateDocument changes index status`() = runTest {
        val repo = FakeDocumentRepository()
        repo.importDocument("content://src", "doc.pdf")
        val doc = repo.getDocument(1L)!!
        val updated = doc.copy(indexStatus = IndexStatus.INDEXED)
        val result = repo.updateDocument(updated)
        assertTrue(result is Result.Success)
        assertEquals(IndexStatus.INDEXED, repo.getDocument(1L)!!.indexStatus)
    }
}
