package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.data.pdf.PageTextWithPositions
import com.asuka.pocketpdf.data.pdf.PdfTextExtractor
import com.asuka.pocketpdf.data.pdf.PdfTextPosition
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
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
 * 不依赖 Robolectric / PdfBox：使用 fake [PdfTextExtractor] 注入模拟坐标。
 */
class SearchDocumentUseCaseTest {

    private lateinit var useCase: SearchDocumentUseCase
    private lateinit var fakeExtractor: FakeTextExtractor
    private lateinit var fakeRepository: FakeDocumentRepository
    private lateinit var testFile: File

    @Before
    fun setUp() {
        testFile = File.createTempFile("search_test_", ".pdf").apply { deleteOnExit() }
        testFile.writeText("dummy pdf content")
        fakeExtractor = FakeTextExtractor()
        fakeRepository = FakeDocumentRepository(testFile)
        useCase = SearchDocumentUseCase(
            textExtractor = fakeExtractor,
            documentRepository = fakeRepository,
            dispatchers = TestDispatcherProvider(),
        )
    }

    @Test
    fun `search returns matches for existing word`() = runTest {
        fakeExtractor.pages = listOf(
            pageText(0, "Hello world, welcome to PocketPDF"),
            pageText(1, "Hello again on page two"),
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
        fakeExtractor.pages = listOf(
            pageText(0, "Only simple text here"),
        )
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "missingword")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertTrue("expected empty results", results.isEmpty())
    }

    @Test
    fun `search is case insensitive`() = runTest {
        fakeExtractor.pages = listOf(
            pageText(0, "CamelCase Word upper LOWER"),
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
        fakeExtractor.pages = listOf(
            pageText(0, "这是一段中文测试文本，包含搜索关键词"),
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
        fakeExtractor.pages = listOf(
            pageText(0, "Some text"),
        )
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
        fakeExtractor.pages = listOf(
            pageText(0, "the word appears the word twice on the same page"),
        )
        fakeRepository.document = document(1, testFile.absolutePath)

        val result = useCase(1, "word")
        assertTrue("expected success", result.isSuccess)
        val results = result.getOrThrow()
        assertEquals("should find 2 matches on same page", 2, results.size)
        results.forEach { assertEquals(0, it.pageIndex) }
    }

    // --- Helpers ---

    private fun pageText(pageIndex: Int, text: String): PageTextWithPositions {
        val positions = text.mapIndexed { i, ch ->
            PdfTextPosition(
                text = ch.toString(),
                pageIndex = pageIndex,
                x = i * 10f,
                y = 700f,
                width = 10f,
                height = 12f,
            )
        }
        return PageTextWithPositions(pageIndex, text, positions)
    }

    private fun document(id: Long, uri: String) = Document(
        id = id,
        title = "test.pdf",
        uri = uri,
        pageCount = 1,
        indexStatus = IndexStatus.NOT_INDEXED,
        importedAt = System.currentTimeMillis(),
    )

    private class FakeTextExtractor : PdfTextExtractor {
        var pages: List<PageTextWithPositions> = emptyList()
        override suspend fun extractPagesText(file: File): List<String> =
            pages.map { it.fullText }
        override suspend fun extractPagesTextWithPositions(file: File): List<PageTextWithPositions> =
            pages
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
