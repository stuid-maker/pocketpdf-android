package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.usecase.GetDocumentUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: DocumentRepository = mockk()
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ReaderViewModel(
            getDocument = GetDocumentUseCase(repository),
            dispatchers = object : DispatcherProvider {
                override val main: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
            },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        assertEquals("Invalid document id: -1", (state as ReaderUiState.Error).message)
    }

    @Test
    fun `load emits Error when document is missing`() = runTest(dispatcher) {
        coEvery { repository.getDocument(7L) } returns null

        viewModel.load(7L)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Error)
        assertEquals("Document #7 not found", (state as ReaderUiState.Error).message)
    }

    @Test
    fun `load emits Error when pdf file is missing`() = runTest(dispatcher) {
        coEvery { repository.getDocument(8L) } returns STUB.copy(id = 8L, uri = "/missing/reader.pdf")

        viewModel.load(8L)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.Error)
        assertEquals("PDF file missing: stub.pdf", (state as ReaderUiState.Error).message)
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
