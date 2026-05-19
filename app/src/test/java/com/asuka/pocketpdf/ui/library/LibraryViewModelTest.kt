package com.asuka.pocketpdf.ui.library

import app.cash.turbine.test
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.usecase.DeleteDocumentUseCase
import com.asuka.pocketpdf.domain.usecase.ImportDocumentUseCase
import com.asuka.pocketpdf.domain.usecase.ObserveDocumentsUseCase
import com.asuka.pocketpdf.data.indexing.IndexingScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * [LibraryViewModel] 状态机单元测试。
 *
 * 测试金字塔定位：纯 JVM + mockk + Turbine（决策 7）；不上 Robolectric。
 * 时间用 [StandardTestDispatcher] 控制，`advanceTimeBy` 触发 5s UNDO timer 兜底逻辑。
 *
 * 不 mock UseCase 而 mock 底层 [DocumentRepository] 的原因：
 * UseCase 是 final class（mockk 需要 byte-buddy agent），且 UseCase 本身已经被
 * 各自的 *UseCaseTest 测过；mock Repository 多覆盖一层组合，更接近线上集成行为。
 *
 * 覆盖矩阵（12 case）：
 * - 加载链路：empty → Loading→Empty / 推送 → Loaded / 上游异常 → Error
 * - 导入链路：成功切换 isImporting / 进行中再次点击被忽略 / 失败发 ShowImportError
 * - 删除链路：swipe 立即过滤 + 发 ShowDeleteUndo / 撤销恢复并取消 timer /
 *           timer 到点自动 commit / Snackbar dismiss 立即 commit /
 *           delete 失败发 ShowDeleteError / 同 id 重复 swipe 幂等
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: DocumentRepository = mockk()
    private val documentsFlow = MutableStateFlow<List<Document>>(emptyList())
    private val indexingScheduler: IndexingScheduler = mockk(relaxed = true)

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeDocuments() } returns documentsFlow
        viewModel = LibraryViewModel(
            observeDocuments = ObserveDocumentsUseCase(repository),
            importDocument = ImportDocumentUseCase(repository),
            deleteDocument = DeleteDocumentUseCase(repository),
            indexingScheduler = indexingScheduler,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty library settles from Loading to Empty`() = runTest(dispatcher) {
        viewModel.uiState.test {
            assertEquals(LibraryUiState.Loading, awaitItem())
            assertEquals(LibraryUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `room flow emission with documents settles to Loaded`() = runTest(dispatcher) {
        viewModel.uiState.test {
            assertEquals(LibraryUiState.Loading, awaitItem())
            assertEquals(LibraryUiState.Empty, awaitItem())

            documentsFlow.value = listOf(STUB.copy(id = 1L), STUB.copy(id = 2L))

            val state = awaitItem()
            assertTrue("expected Loaded, got $state", state is LibraryUiState.Loaded)
            val loaded = state as LibraryUiState.Loaded
            assertEquals(2, loaded.documents.size)
            assertEquals(false, loaded.isImporting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `import success toggles isImporting true then false`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 1L))
        val gate = CompletableDeferred<Result<Document>>()
        coEvery { repository.importDocument(any(), any()) } coAnswers { gate.await() }

        viewModel.uiState.test {
            awaitItem() // Loading
            val initial = awaitItem() as LibraryUiState.Loaded
            assertEquals(false, initial.isImporting)

            viewModel.onImportRequested("content://saf/42", "spec.pdf")
            runCurrent()

            val mid = awaitItem() as LibraryUiState.Loaded
            assertEquals(true, mid.isImporting)

            gate.complete(Result.Success(STUB.copy(id = 2L, title = "spec.pdf")))
            runCurrent()

            val after = awaitItem() as LibraryUiState.Loaded
            assertEquals(false, after.isImporting)
            coVerify(exactly = 1) { repository.importDocument("content://saf/42", "spec.pdf") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `import is skipped when an import is already in progress`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Result<Document>>()
        coEvery { repository.importDocument(any(), any()) } coAnswers { gate.await() }

        viewModel.onImportRequested("content://first", "a.pdf")
        runCurrent()
        viewModel.onImportRequested("content://second", "b.pdf")
        runCurrent()

        coVerify(exactly = 1) { repository.importDocument(any(), any()) }
        coVerify(exactly = 0) { repository.importDocument("content://second", any()) }
        gate.complete(Result.Success(STUB))
    }

    @Test
    fun `import failure emits ShowImportError oneshot`() = runTest(dispatcher) {
        val boom = IOException("SAF denied read access")
        coEvery { repository.importDocument(any(), any()) } returns Result.Failure(boom)

        viewModel.oneShotEvents.test {
            viewModel.onImportRequested("content://denied", "denied.pdf")
            runCurrent()
            val event = awaitItem()
            assertTrue(event is LibraryEvent.ShowImportError)
            assertEquals("SAF denied read access", (event as LibraryEvent.ShowImportError).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `swipe hides the document from the visible list immediately`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 1L, title = "alpha.pdf"))
        coEvery { repository.deleteDocument(1L) } returns Result.Success(Unit)

        viewModel.uiState.test {
            awaitItem() // Loading
            val initial = awaitItem() as LibraryUiState.Loaded
            assertEquals(1, initial.documents.size)

            viewModel.onSwipeDelete(1L, "alpha.pdf")

            assertEquals(LibraryUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `swipe emits ShowDeleteUndo event with documentId and title`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 7L, title = "alpha.pdf"))
        coEvery { repository.deleteDocument(7L) } returns Result.Success(Unit)

        viewModel.oneShotEvents.test {
            viewModel.onSwipeDelete(7L, "alpha.pdf")
            runCurrent()
            val event = awaitItem()
            assertTrue(event is LibraryEvent.ShowDeleteUndo)
            assertEquals(7L, (event as LibraryEvent.ShowDeleteUndo).documentId)
            assertEquals("alpha.pdf", event.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo restores hidden document and cancels auto-commit timer`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 1L))
        coEvery { repository.deleteDocument(any()) } returns Result.Success(Unit)

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // initial Loaded
            viewModel.onSwipeDelete(1L, "x.pdf")
            awaitItem() // Empty after filter

            viewModel.onUndoDelete(1L)
            val restored = awaitItem() as LibraryUiState.Loaded
            assertEquals(1, restored.documents.size)

            advanceTimeBy(UNDO_TIMEOUT_MS + 1_000L)
            runCurrent()
            coVerify(exactly = 0) { repository.deleteDocument(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auto-commit fires deleteDocument after UNDO_TIMEOUT elapses`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 1L))
        coEvery { repository.deleteDocument(1L) } returns Result.Success(Unit)

        viewModel.onSwipeDelete(1L, "x.pdf")
        runCurrent()
        coVerify(exactly = 0) { repository.deleteDocument(any()) }

        advanceTimeBy(UNDO_TIMEOUT_MS + 1L)
        runCurrent()

        coVerify(exactly = 1) { repository.deleteDocument(1L) }
    }

    @Test
    fun `snackbar dismiss without undo commits delete immediately`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 1L))
        coEvery { repository.deleteDocument(1L) } returns Result.Success(Unit)

        viewModel.onSwipeDelete(1L, "x.pdf")
        runCurrent()
        viewModel.onSnackbarDismissedWithoutUndo(1L)
        runCurrent()

        coVerify(exactly = 1) { repository.deleteDocument(1L) }

        // 验证 5s timer 不会再触发第二次（idempotent，由 pendingDeleteIds 检查保证）
        advanceTimeBy(UNDO_TIMEOUT_MS + 1_000L)
        runCurrent()
        coVerify(exactly = 1) { repository.deleteDocument(1L) }
    }

    @Test
    fun `delete failure surfaces ShowDeleteError oneshot`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 1L))
        val boom = IOException("disk eject")
        coEvery { repository.deleteDocument(1L) } returns Result.Failure(boom)

        viewModel.oneShotEvents.test {
            viewModel.onSwipeDelete(1L, "x.pdf")
            runCurrent()
            assertTrue(awaitItem() is LibraryEvent.ShowDeleteUndo)

            advanceTimeBy(UNDO_TIMEOUT_MS + 1L)
            runCurrent()

            val event = awaitItem()
            assertTrue("expected ShowDeleteError, got $event", event is LibraryEvent.ShowDeleteError)
            assertEquals("disk eject", (event as LibraryEvent.ShowDeleteError).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeated swipe on same id is idempotent`() = runTest(dispatcher) {
        documentsFlow.value = listOf(STUB.copy(id = 1L))
        coEvery { repository.deleteDocument(1L) } returns Result.Success(Unit)

        viewModel.oneShotEvents.test {
            viewModel.onSwipeDelete(1L, "x.pdf")
            runCurrent()
            assertTrue(awaitItem() is LibraryEvent.ShowDeleteUndo)

            viewModel.onSwipeDelete(1L, "x.pdf")
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val UNDO_TIMEOUT_MS = 5_000L
        val STUB = Document(
            id = 0L,
            title = "stub.pdf",
            uri = "/data/data/com.asuka.pocketpdf/files/documents/stub.pdf",
            pageCount = 10,
            indexStatus = IndexStatus.NOT_INDEXED,
            importedAt = 1_700_000_000_000L,
        )
    }
}
