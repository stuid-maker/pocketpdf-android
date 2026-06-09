package com.asuka.pocketpdf.domain.usecase

import android.graphics.Bitmap
import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.model.SearchResult
import com.asuka.pocketpdf.domain.pdf.*
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SearchDocumentUseCase] 的纯逻辑测试。
 *
 * 使用 fake [PdfDocumentEngine] 注入模拟 PDFium 搜索结果。
 */
class SearchDocumentUseCaseTest {

    private lateinit var useCase: SearchDocumentUseCase
    private lateinit var fakeEngine: FakePdfDocumentEngine
    private lateinit var fakeRepository: FakeDocumentRepository
    private lateinit var testFile: File

    @Before
    fun setUp() {
        testFile = File.createTempFile("search_test_", ".pdf").apply { deleteOnExit() }
        testFile.writeText("dummy pdf content")
        fakeEngine = FakePdfDocumentEngine()
        fakeRepository = FakeDocumentRepository(testFile)
        useCase = SearchDocumentUseCase(
            documentEngine = fakeEngine,
            documentRepository = fakeRepository,
            dispatchers = TestDispatcherProvider(),
        )
    }

    @Test
    fun `search returns matches for existing word`() = runTest {
        fakeEngine.matches = listOf(
            fakeMatch(0, "Hello", 0),
            fakeMatch(1, "Hello", 0),
        )
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "Hello")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertEquals("should find 2 matches (one per page)", 2, results.size)
        assertEquals(0, results[0].pageIndex)
        assertEquals(1, results[1].pageIndex)
    }

    @Test
    fun `search returns empty for non-existent word`() = runTest {
        fakeEngine.matches = emptyList()
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "missingword")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertTrue("expected empty results", results.isEmpty())
    }

    @Test
    fun `search is case insensitive`() = runTest {
        fakeEngine.matches = listOf(
            fakeMatch(0, "CamelCase", 0),
        )
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "camelcase")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertEquals("should match case-insensitively", 1, results.size)
        assertEquals("CamelCase", results[0].matchText)
    }

    @Test
    fun `search supports chinese text`() = runTest {
        fakeEngine.matches = listOf(
            fakeMatch(0, "搜索关键词", 8),
        )
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "搜索关键词")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertEquals("should match Chinese", 1, results.size)
        assertTrue(
            "match should contain the keyword",
            results[0].matchText.contains("搜索关键词"),
        )
    }

    @Test
    fun `empty query returns empty results`() = runTest {
        fakeEngine.matches = listOf(fakeMatch(0, "Some", 0))
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertTrue("empty query should yield empty results", results.isEmpty())

        val resultBlank = useCase(1, "   ")
        assertTrue("expected success", resultBlank.isSuccess)
        assertTrue(
            "blank query should yield empty results",
            resultBlank.getOrThrow().isEmpty(),
        )
    }

    @Test
    fun `search fails when document not found`() = runTest {
        fakeRepository.document = null

        val result = useCase(999, "anything")
        assertTrue("expected failure", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `multiple matches on same page are found`() = runTest {
        fakeEngine.matches = listOf(
            fakeMatch(0, "word", 4),
            fakeMatch(0, "word", 28),
        )
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "word")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertEquals("should find 2 matches on same page", 2, results.size)
        results.forEach { assertEquals(0, it.pageIndex) }
    }

    // --- Helpers ---

    private fun fakeMatch(pageIndex: Int, text: String, startIndex: Int): PdfSearchMatch =
        PdfSearchMatch(
            pageIndex = pageIndex,
            startIndex = startIndex,
            length = text.length,
            text = text,
            rects = listOf(
                PdfPageRect(
                    100f, (700 + pageIndex * 100).toFloat(),
                    100f + text.length * 10f, (712 + pageIndex * 100).toFloat(),
                ),
            ),
        )

    private fun document(id: Long, uri: String) = Document(
        id = id,
        title = "test.pdf",
        uri = uri,
        pageCount = 1,
        indexStatus = IndexStatus.NOT_INDEXED,
        importedAt = System.currentTimeMillis(),
    )

    /**
     * Fake [PdfDocumentEngine] that returns pre-configured search matches.
     */
    private class FakePdfDocumentEngine : PdfDocumentEngine {
        var matches: List<PdfSearchMatch> = emptyList()

        override suspend fun open(file: File, password: String?): PdfDocumentSession =
            FakeSession(matches)
    }

    /**
     * Fake [PdfDocumentSession] backed by a list of [PdfSearchMatch].
     */
    private class FakeSession(
        private val allMatches: List<PdfSearchMatch>,
    ) : PdfDocumentSession {
        private var closed = false

        override val pageCount: Int
            get() = 3

        override suspend fun pageInfo(pageIndex: Int): PdfPageInfo =
            PdfPageInfo(pageIndex, 612f, 792f)

        override suspend fun render(request: PdfRenderRequest): Bitmap =
            throw UnsupportedOperationException("render not used in search tests")

        override suspend fun extractText(pageIndex: Int): PdfPageText =
            throw UnsupportedOperationException("extractText not used in search tests")

        override suspend fun searchPage(pageIndex: Int, query: String): List<PdfSearchMatch> {
            require(!closed) { "Session is closed" }
            return allMatches.filter { it.pageIndex == pageIndex }
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeDocumentRepository(
        private val expectedFile: File,
    ) : DocumentRepository {
        var document: Document? = null

        override fun observeDocuments(): kotlinx.coroutines.flow.Flow<List<Document>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getDocument(id: Long): Document? = document
        override suspend fun updateDocument(document: Document): com.asuka.pocketpdf.core.Result<Unit> =
            com.asuka.pocketpdf.core.Result.Success(Unit)
        override suspend fun importDocument(sourceUri: String, displayName: String): com.asuka.pocketpdf.core.Result<Document> =
            com.asuka.pocketpdf.core.Result.Failure(UnsupportedOperationException())
        override suspend fun deleteDocument(id: Long): com.asuka.pocketpdf.core.Result<Unit> =
            com.asuka.pocketpdf.core.Result.Failure(UnsupportedOperationException())
        override suspend fun replaceChunks(
            documentId: Long,
            chunks: List<com.asuka.pocketpdf.domain.model.DocumentChunk>,
        ): com.asuka.pocketpdf.core.Result<Unit> =
            com.asuka.pocketpdf.core.Result.Failure(UnsupportedOperationException())
        override suspend fun getChunks(documentId: Long): List<com.asuka.pocketpdf.domain.model.DocumentChunk> =
            emptyList()
        override suspend fun getChunksByPage(
            documentId: Long,
            pageIndex: Int,
        ): List<com.asuka.pocketpdf.domain.model.DocumentChunk> = emptyList()
    }
}
