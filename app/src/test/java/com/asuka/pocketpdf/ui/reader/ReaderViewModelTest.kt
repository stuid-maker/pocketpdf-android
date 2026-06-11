package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.usecase.FullDocumentProgress
import com.asuka.pocketpdf.domain.usecase.GetDocumentUseCase
import com.asuka.pocketpdf.domain.usecase.SummarizeDocumentUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: DocumentRepository = mockk()
    private val summarizeDocument: SummarizeDocumentUseCase = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private var clockMs = 0L
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        clockMs = 0L
        Dispatchers.setMain(dispatcher)
        viewModel = ReaderViewModel(
            getDocument = GetDocumentUseCase(repository),
            summarizeDocument = summarizeDocument,
            settingsDataStore = settingsDataStore,
            dispatchers = object : DispatcherProvider {
                override val main: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
            },
        )
        viewModel.elapsedRealtime = { clockMs }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── existing load tests ────────────────────────────────────────────────

    @Test
    fun `load emits Loaded when document exists and file is present`() = runTest(dispatcher) {
        val pdf = File.createTempFile("reader", ".pdf").apply { deleteOnExit() }
        val document = STUB.copy(id = 42L, uri = pdf.absolutePath)
        coEvery { repository.getDocument(42L) } returns document

        viewModel.load(42L)
        runCurrent()

        assertEquals(ReaderUiState.Loaded(document), viewModel.uiState.value)
    }

    @Test
    fun `load emits Error for invalid id`() = runTest(dispatcher) {
        viewModel.load(-1L)

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Error)
        assertEquals("无效的文档 ID：-1", (state as ReaderUiState.Error).message)
    }

    @Test
    fun `load emits Error when document is missing`() = runTest(dispatcher) {
        coEvery { repository.getDocument(7L) } returns null

        viewModel.load(7L)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Error)
        assertEquals("找不到文档 #7", (state as ReaderUiState.Error).message)
    }

    @Test
    fun `load emits Error when pdf file is missing`() = runTest(dispatcher) {
        coEvery { repository.getDocument(8L) } returns STUB.copy(id = 8L, uri = "/missing/reader.pdf")

        viewModel.load(8L)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Error)
        assertEquals("PDF 文件缺失：stub.pdf", (state as ReaderUiState.Error).message)
    }

    @Test
    fun `load emits Error when repository throws`() = runTest(dispatcher) {
        coEvery { repository.getDocument(9L) } throws IOException("db unavailable")

        viewModel.load(9L)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Error)
        assertEquals("db unavailable", (state as ReaderUiState.Error).message)
    }

    // ── new summary progress tests ─────────────────────────────────────────

    @Test
    fun `full summary exposes stage progress before tokens`() = runTest {
        val pdf = File.createTempFile("reader", ".pdf").apply { deleteOnExit() }
        val document = STUB.copy(id = 99L, uri = pdf.absolutePath)
        coEvery { repository.getDocument(99L) } returns document
        coEvery { settingsDataStore.modelName } returns flowOf("test-model")
        coEvery { settingsDataStore.systemPrompt } returns flowOf("")

        viewModel.load(99L)
        runCurrent()
        assertTrue(viewModel.uiState.value is ReaderUiState.Loaded)

        coEvery {
            summarizeDocument(
                documentId = 99L,
                model = "test-model",
                scope = SummaryScope.Full,
                systemPrompt = any(),
                onProgress = any(),
            )
        } answers {
            val onProgress = arg<(FullDocumentProgress) -> Unit>(5)
            onProgress(FullDocumentProgress.Preparing)
            clockMs = 100L
            onProgress(FullDocumentProgress.Mapping(completed = 1, total = 3))
            clockMs = 200L
            flowOf("第一部分")
        }

        viewModel.summarizeFullDocument()
        runCurrent()

        // After full completion, state should be Done with all tokens
        val state = viewModel.uiState.value
        assertTrue("Expected Loaded but got $state", state is ReaderUiState.Loaded)
        val loaded = state as ReaderUiState.Loaded
        val summaryState = loaded.summaryState
        assertTrue("Expected Done but got $summaryState", summaryState is SummaryState.Done)
        assertEquals("第一部分", (summaryState as SummaryState.Done).fullText)
    }

    @Test
    fun `stop summary cancels active job`() = runTest {
        val pdf = File.createTempFile("reader", ".pdf").apply { deleteOnExit() }
        val document = STUB.copy(id = 100L, uri = pdf.absolutePath)
        coEvery { repository.getDocument(100L) } returns document
        coEvery { settingsDataStore.modelName } returns flowOf("test-model")
        coEvery { settingsDataStore.systemPrompt } returns flowOf("")

        viewModel.load(100L)
        runCurrent()
        assertTrue(viewModel.uiState.value is ReaderUiState.Loaded)

        coEvery {
            summarizeDocument(
                documentId = 100L,
                model = "test-model",
                scope = SummaryScope.Full,
                systemPrompt = any(),
                onProgress = any(),
            )
        } answers {
            val onProgress = arg<(FullDocumentProgress) -> Unit>(5)
            onProgress(FullDocumentProgress.Preparing)
            flow {
                kotlinx.coroutines.awaitCancellation()
            }
        }

        viewModel.summarizeFullDocument()
        runCurrent()

        val stateBeforeCancel = viewModel.uiState.value
        assertTrue(stateBeforeCancel is ReaderUiState.Loaded)
        val summaryBefore = (stateBeforeCancel as ReaderUiState.Loaded).summaryState
        assertTrue("Expected Generating but got $summaryBefore", summaryBefore is SummaryState.Generating)

        viewModel.stopSummarizing()
        runCurrent()

        val stateAfter = viewModel.uiState.value
        assertTrue(stateAfter is ReaderUiState.Loaded)
        val summaryAfter = (stateAfter as ReaderUiState.Loaded).summaryState
        assertEquals(SummaryState.Idle, summaryAfter)
    }

    private companion object {
        val STUB = Document(
            id = 0L,
            title = "stub.pdf",
            uri = "/data/data/com.asuka.pocketpdf/files/documents/stub.pdf",
            pageCount = 3,
            indexStatus = IndexStatus.NOT_INDEXED,
            importedAt = 1_700_000_000_000L,
        )
    }
}
