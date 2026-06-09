package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.domain.model.SearchResult
import com.asuka.pocketpdf.domain.usecase.SearchDocumentUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SearchViewModel] 导航行为测试。
 *
 * 测试 nextMatch/previousMatch 循环、空结果不崩溃、边界情况。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchNavigatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeUseCase: FakeSearchDocumentUseCase
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeUseCase = FakeSearchDocumentUseCase()
        viewModel = SearchViewModel(fakeUseCase)
        viewModel.init(1L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nextMatch wraps around to first result`() = runTest {
        fakeUseCase.results = listOf(
            searchResult(0, "hello", 0),
            searchResult(0, "hello", 10),
            searchResult(1, "hello", 0),
        )

        viewModel.search("hello")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("should have 3 total matches", 3, state.totalMatches)
        assertEquals("current index starts at 0", 0, state.currentMatchIndex)

        viewModel.nextMatch()
        assertEquals("after first next -> index 1", 1, viewModel.uiState.value.currentMatchIndex)

        viewModel.nextMatch()
        assertEquals("after second next -> index 2", 2, viewModel.uiState.value.currentMatchIndex)

        viewModel.nextMatch()
        assertEquals("after third next -> wraps to 0", 0, viewModel.uiState.value.currentMatchIndex)
    }

    @Test
    fun `previousMatch wraps around to last result`() = runTest {
        fakeUseCase.results = listOf(
            searchResult(0, "hello", 0),
            searchResult(0, "hello", 10),
            searchResult(1, "hello", 0),
        )

        viewModel.search("hello")
        advanceUntilIdle()
        assertEquals("starts at 0", 0, viewModel.uiState.value.currentMatchIndex)

        viewModel.previousMatch()
        assertEquals("prev from 0 wraps to 2", 2, viewModel.uiState.value.currentMatchIndex)

        viewModel.previousMatch()
        assertEquals("prev from 2 -> 1", 1, viewModel.uiState.value.currentMatchIndex)
    }

    @Test
    fun `nextMatch does nothing when no results`() = runTest {
        fakeUseCase.results = emptyList()

        viewModel.search("nothing")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("total should be 0", 0, state.totalMatches)
        assertEquals("index should be 0", 0, state.currentMatchIndex)

        viewModel.nextMatch()
        assertEquals("index stays 0", 0, viewModel.uiState.value.currentMatchIndex)

        viewModel.previousMatch()
        assertEquals("index stays 0", 0, viewModel.uiState.value.currentMatchIndex)
    }

    @Test
    fun `clear resets state`() = runTest {
        fakeUseCase.results = listOf(searchResult(0, "test", 0))

        viewModel.search("test")
        advanceUntilIdle()
        assertTrue("query should be set", viewModel.uiState.value.query.isNotEmpty())

        viewModel.clear()
        val state = viewModel.uiState.value
        assertEquals("query cleared", "", state.query)
        assertEquals("results cleared", 0, state.totalMatches)
    }

    @Test
    fun `search sets currentMatchIndex to 0 when results found`() = runTest {
        fakeUseCase.results = listOf(
            searchResult(0, "match", 5),
            searchResult(2, "match", 3),
        )

        viewModel.search("match")
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentMatchIndex)

        viewModel.nextMatch()
        assertEquals(1, viewModel.uiState.value.currentMatchIndex)

        viewModel.search("match")
        advanceUntilIdle()
        assertEquals("re-search resets index to 0", 0, viewModel.uiState.value.currentMatchIndex)
    }

    @Test
    fun `single result navigation stays at 0`() = runTest {
        fakeUseCase.results = listOf(searchResult(0, "only", 0))

        viewModel.search("only")
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentMatchIndex)

        viewModel.nextMatch()
        assertEquals("wraps to 0 (only one)", 0, viewModel.uiState.value.currentMatchIndex)

        viewModel.previousMatch()
        assertEquals("wraps to 0 (only one)", 0, viewModel.uiState.value.currentMatchIndex)
    }

    @Test
    fun `next then previous returns to original`() = runTest {
        fakeUseCase.results = listOf(
            searchResult(0, "a", 0),
            searchResult(0, "a", 2),
            searchResult(1, "a", 0),
        )

        viewModel.search("a")
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentMatchIndex)

        viewModel.nextMatch()
        viewModel.nextMatch()
        assertEquals(2, viewModel.uiState.value.currentMatchIndex)

        viewModel.previousMatch()
        assertEquals(1, viewModel.uiState.value.currentMatchIndex)

        viewModel.previousMatch()
        assertEquals(0, viewModel.uiState.value.currentMatchIndex)
    }

    // --- Helpers ---

    private fun searchResult(pageIndex: Int, matchText: String, matchIndex: Int) =
        SearchResult(
            pageIndex = pageIndex,
            matchText = matchText,
            matchIndex = matchIndex,
            positions = emptyList(),
            pdfPageWidth = 612f,
            pdfPageHeight = 792f,
        )

    /**
     * Fake [SearchDocumentUseCase] that returns pre-configured results immediately.
     */
    private class FakeSearchDocumentUseCase(
        var results: List<SearchResult> = emptyList(),
    ) : SearchDocumentUseCase(
        documentEngine = object : com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine {
            override suspend fun open(
                file: java.io.File,
                password: String?,
            ): com.asuka.pocketpdf.domain.pdf.PdfDocumentSession =
                throw UnsupportedOperationException()
        },
        documentRepository = object : com.asuka.pocketpdf.domain.repository.DocumentRepository {
            override fun observeDocuments(): kotlinx.coroutines.flow.Flow<List<com.asuka.pocketpdf.domain.model.Document>> =
                kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getDocument(id: Long): com.asuka.pocketpdf.domain.model.Document? = null
            override suspend fun updateDocument(document: com.asuka.pocketpdf.domain.model.Document): com.asuka.pocketpdf.core.Result<Unit> =
                com.asuka.pocketpdf.core.Result.Success(Unit)
            override suspend fun importDocument(sourceUri: String, displayName: String): com.asuka.pocketpdf.core.Result<com.asuka.pocketpdf.domain.model.Document> =
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
        },
        dispatchers = object : com.asuka.pocketpdf.core.DispatcherProvider {
            override val main: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            override val io: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            override val default: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        },
    ) {
        override suspend fun invoke(
            documentId: Long,
            query: String,
        ): Result<List<SearchResult>> {
            if (query.isBlank()) return Result.success(emptyList())
            return Result.success(results)
        }

        override suspend fun extractPageTextPositions(
            documentId: Long,
        ): Result<List<com.asuka.pocketpdf.data.pdf.PageTextWithPositions>> =
            Result.success(emptyList())
    }
}
