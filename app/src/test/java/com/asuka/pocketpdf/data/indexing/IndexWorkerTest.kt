package com.asuka.pocketpdf.data.indexing

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.data.chunking.ParagraphChunker
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.pdf.PdfTextExtractor
import com.asuka.pocketpdf.domain.chunking.TextChunker
import com.asuka.pocketpdf.domain.embedding.EmbeddingEngine
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.pdf.PdfExtractorVersion
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * IndexWorker 集成测试：验证 extract → chunk → embed → save → mark INDEXED 完整流程。
 *
 * 使用 Robolectric 模拟 Android 环境，通过 mock 注入所有依赖。
 * 直接构造 IndexWorker 并调用 doWork() 验证完整流程。
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class IndexWorkerTest {

    private val documentRepo = mockk<DocumentRepository>(relaxUnitFun = true)
    private val pdfExtractor = mockk<PdfTextExtractor>()
    private val chunker = mockk<TextChunker>()
    private val paragraphChunker = mockk<ParagraphChunker>()
    private val settingsDataStore = mockk<SettingsDataStore>()
    private val embedEngine = mockk<EmbeddingEngine>()
    private val summaryCacheRepository = mockk<SummaryCacheRepository>(relaxUnitFun = true)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private fun createWorker(documentId: Long): IndexWorker {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns IndexWorker.buildInputData(documentId)
        return IndexWorker(
            appContext = context,
            params = params,
            documentRepo = documentRepo,
            pdfExtractor = pdfExtractor,
            chunker = chunker,
            paragraphChunker = paragraphChunker,
            settingsDataStore = settingsDataStore,
            embedEngine = embedEngine,
            summaryCacheRepository = summaryCacheRepository,
        )
    }

    @Test
    fun `happy path extracts chunks embeds and marks INDEXED`() = runTest {
        val documentId = 1L
        val doc = Document(
            id = documentId,
            title = "test.pdf",
            uri = "/tmp/test.pdf",
            pageCount = 2,
            indexStatus = IndexStatus.NOT_INDEXED,
            importedAt = 1000L,
        )
        val pages = listOf("Page 1 content", "Page 2 content")
        val chunks = listOf(
            DocumentChunk(documentId = documentId, pageIndex = 0, chunkIndex = 0, text = "Page 1 content"),
            DocumentChunk(documentId = documentId, pageIndex = 1, chunkIndex = 0, text = "Page 2 content"),
        )
        val embeddings = listOf(floatArrayOf(0.1f, 0.2f), floatArrayOf(0.3f, 0.4f))

        every { settingsDataStore.chunkingStrategy } returns flowOf(SettingsDataStore.STRATEGY_SLIDING_WINDOW)
        coEvery { documentRepo.getDocument(documentId) } returns doc
        coEvery { documentRepo.updateDocument(any()) } returns Result.Success(Unit)
        coEvery { documentRepo.replaceChunks(documentId, any()) } returns Result.Success(Unit)
        coEvery { pdfExtractor.extractPagesText(any()) } returns pages
        every { chunker.chunk(documentId, pages) } returns chunks
        coEvery { embedEngine.getEmbeddings(listOf("Page 1 content", "Page 2 content")) } returns embeddings

        val worker = createWorker(documentId)
        val result = worker.doWork()

        assertTrue("Expected Success", result is ListenableWorker.Result.Success)

        coVerify(exactly = 1) { documentRepo.getDocument(documentId) }
        coVerify(exactly = 1) { pdfExtractor.extractPagesText(any()) }
        coVerify(exactly = 1) { documentRepo.replaceChunks(documentId, any()) }
        coVerify(exactly = 1) { embedEngine.getEmbeddings(any()) }
        coVerify {
            documentRepo.updateDocument(
                match {
                    it.indexStatus == IndexStatus.INDEXED &&
                        it.extractorVersion == PdfExtractorVersion.CURRENT
                },
            )
        }
    }

    @Test
    fun `returns failure when document not found`() = runTest {
        val documentId = 999L
        coEvery { documentRepo.getDocument(documentId) } returns null

        val worker = createWorker(documentId)
        val result = worker.doWork()

        assertTrue("Expected Failure", result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { pdfExtractor.extractPagesText(any()) }
    }

    @Test
    fun `returns failure when input data has invalid documentId`() = runTest {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns Data.Builder().build()

        val worker = IndexWorker(
            appContext = context,
            params = params,
            documentRepo = documentRepo,
            pdfExtractor = pdfExtractor,
            chunker = chunker,
            paragraphChunker = paragraphChunker,
            settingsDataStore = settingsDataStore,
            embedEngine = embedEngine,
            summaryCacheRepository = summaryCacheRepository,
        )
        val result = worker.doWork()

        assertTrue("Expected Failure", result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `marks NEEDS_OCR when document has no extractable text`() = runTest {
        val documentId = 1L
        val doc = Document(
            id = documentId, title = "empty.pdf", uri = "/tmp/empty.pdf",
            pageCount = 5, indexStatus = IndexStatus.NOT_INDEXED, importedAt = 1000L,
        )

        every { settingsDataStore.chunkingStrategy } returns flowOf(SettingsDataStore.STRATEGY_SLIDING_WINDOW)
        coEvery { documentRepo.getDocument(documentId) } returns doc
        coEvery { documentRepo.updateDocument(any()) } returns Result.Success(Unit)
        coEvery {
            documentRepo.replaceChunks(documentId, emptyList())
        } returns Result.Success(Unit)
        coEvery { pdfExtractor.extractPagesText(any()) } returns emptyList()
        // chunker.chunk will receive empty pages → returns empty list (default mockk behavior returns emptyList)
        every { chunker.chunk(any(), any()) } returns emptyList()

        val worker = createWorker(documentId)
        val result = worker.doWork()

        assertTrue("Expected Success", result is ListenableWorker.Result.Success)
        coVerify {
            documentRepo.updateDocument(
                match {
                    it.indexStatus == IndexStatus.NEEDS_OCR &&
                        it.extractorVersion == PdfExtractorVersion.CURRENT
                },
            )
        }
        coVerify(exactly = 0) { embedEngine.getEmbeddings(any()) }
        coVerify(exactly = 1) { documentRepo.replaceChunks(documentId, emptyList()) }
        coVerify(exactly = 1) { summaryCacheRepository.invalidate(documentId) }
    }

    @Test
    fun `marks FAILED when extraction throws`() = runTest {
        val documentId = 1L
        val doc = Document(
            id = documentId, title = "broken.pdf", uri = "/tmp/broken.pdf",
            pageCount = 1, indexStatus = IndexStatus.NOT_INDEXED, importedAt = 1000L,
        )

        every { settingsDataStore.chunkingStrategy } returns flowOf(SettingsDataStore.STRATEGY_SLIDING_WINDOW)
        coEvery { documentRepo.getDocument(documentId) } returns doc
        coEvery { documentRepo.updateDocument(any()) } returns Result.Success(Unit)
        coEvery { pdfExtractor.extractPagesText(any()) } throws RuntimeException("PDF parse error")

        val worker = createWorker(documentId)
        val result = worker.doWork()

        assertTrue("Expected Failure", result is ListenableWorker.Result.Failure)
        coVerify(atLeast = 1) { documentRepo.updateDocument(match { it.indexStatus == IndexStatus.FAILED }) }
    }

    @Test
    fun `marks FAILED when replacing chunks returns failure`() = runTest {
        val documentId = 1L
        val doc = document(documentId)
        val chunks = listOf(
            DocumentChunk(documentId = documentId, pageIndex = 0, chunkIndex = 0, text = "content"),
        )

        stubIndexing(doc, chunks, listOf(floatArrayOf(0.1f)))
        coEvery { documentRepo.replaceChunks(documentId, any()) } returns
            Result.Failure(IllegalStateException("database full"))

        val result = createWorker(documentId).doWork()

        assertTrue("Expected Failure", result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) {
            documentRepo.updateDocument(match { it.indexStatus == IndexStatus.INDEXED })
        }
        coVerify(atLeast = 1) {
            documentRepo.updateDocument(match { it.indexStatus == IndexStatus.FAILED })
        }
    }

    @Test
    fun `marks FAILED when embedding count differs from chunk count`() = runTest {
        val documentId = 1L
        val doc = document(documentId)
        val chunks = listOf(
            DocumentChunk(documentId = documentId, pageIndex = 0, chunkIndex = 0, text = "one"),
            DocumentChunk(documentId = documentId, pageIndex = 0, chunkIndex = 1, text = "two"),
        )

        stubIndexing(doc, chunks, listOf(floatArrayOf(0.1f)))

        val result = createWorker(documentId).doWork()

        assertTrue("Expected Failure", result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { documentRepo.replaceChunks(any(), any()) }
        coVerify(atLeast = 1) {
            documentRepo.updateDocument(match { it.indexStatus == IndexStatus.FAILED })
        }
    }

    @Test
    fun `returns failure when initial status update returns failure`() = runTest {
        val documentId = 1L
        val doc = document(documentId)

        every { settingsDataStore.chunkingStrategy } returns
            flowOf(SettingsDataStore.STRATEGY_SLIDING_WINDOW)
        coEvery { documentRepo.getDocument(documentId) } returns doc
        coEvery {
            documentRepo.updateDocument(match { it.indexStatus == IndexStatus.INDEXING })
        } returns Result.Failure(IllegalStateException("update failed"))
        coEvery {
            documentRepo.updateDocument(match { it.indexStatus == IndexStatus.FAILED })
        } returns Result.Success(Unit)

        val result = createWorker(documentId).doWork()

        assertTrue("Expected Failure", result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { pdfExtractor.extractPagesText(any()) }
    }

    @Test
    fun `cache invalidation failure does not prevent successful indexing`() = runTest {
        val documentId = 1L
        val doc = document(documentId)
        val chunks = listOf(
            DocumentChunk(documentId = documentId, pageIndex = 0, chunkIndex = 0, text = "content"),
        )
        stubIndexing(doc, chunks, listOf(floatArrayOf(0.1f)))
        coEvery { summaryCacheRepository.invalidate(documentId) } throws
            IllegalStateException("cache unavailable")

        val result = createWorker(documentId).doWork()

        // 缓存清理失败不影响索引流程，应返回 Success
        assertTrue("Expected Success", result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { documentRepo.replaceChunks(documentId, any()) }
        coVerify(atLeast = 1) {
            documentRepo.updateDocument(match { it.indexStatus == IndexStatus.INDEXED })
        }
    }

    private fun document(documentId: Long) = Document(
        id = documentId,
        title = "test.pdf",
        uri = "/tmp/test.pdf",
        pageCount = 1,
        indexStatus = IndexStatus.NOT_INDEXED,
        importedAt = 1000L,
    )

    private fun stubIndexing(
        doc: Document,
        chunks: List<DocumentChunk>,
        embeddings: List<FloatArray>,
    ) {
        every { settingsDataStore.chunkingStrategy } returns
            flowOf(SettingsDataStore.STRATEGY_SLIDING_WINDOW)
        coEvery { documentRepo.getDocument(doc.id) } returns doc
        coEvery { documentRepo.updateDocument(any()) } returns Result.Success(Unit)
        coEvery { documentRepo.replaceChunks(doc.id, any()) } returns Result.Success(Unit)
        coEvery { pdfExtractor.extractPagesText(any()) } returns listOf("page")
        every { chunker.chunk(doc.id, any()) } returns chunks
        coEvery { embedEngine.getEmbeddings(any()) } returns embeddings
    }
}
