package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.RetrievalResult
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SummarizeDocumentUseCaseTest {

    private val retrieveChunks = mockk<RetrieveChunksUseCase>()
    private val documentRepository = mockk<DocumentRepository>()
    private val llmRepository = mockk<LlmRepository>()
    private val summaryCacheRepository = mockk<SummaryCacheRepository>(relaxUnitFun = true)
    private val fullDocumentSummarizer = mockk<FullDocumentSummarizer>(relaxed = true)
    private val useCase = SummarizeDocumentUseCase(
        retrieveChunks,
        documentRepository,
        llmRepository,
        summaryCacheRepository,
        fullDocumentSummarizer,
    )

    private fun chunk(id: Long, pageIndex: Int, text: String) =
        DocumentChunk(
            id = id, documentId = 1L, pageIndex = pageIndex, chunkIndex = 0, text = text,
            embedding = floatArrayOf(1f),
        )

    private fun stubStream(vararg tokens: String): Flow<String> = flow {
        tokens.forEach { emit(it) }
    }

    private fun stubCacheMiss() {
        every {
            summaryCacheRepository.get(
                documentId = any(), scope = any(), algorithmVersion = any(),
                model = any(), systemPrompt = any(),
            )
        } returns flowOf(null)
    }

    // ── Full mode ──────────────────────────────────────

    @Test
    fun `full mode delegates to FullDocumentSummarizer`() = runTest {
        stubCacheMiss()
        coEvery {
            fullDocumentSummarizer.summarize(
                documentId = 1L, model = "gemma-3-4b",
                systemPrompt = any(), question = null,
                onProgress = any(),
            )
        } returns stubStream("全文", "总结")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
        assertEquals(listOf("全文", "总结"), tokens)
    }

    @Test
    fun `full mode throws NoChunksException from summarizer`() = runTest {
        stubCacheMiss()
        coEvery {
            fullDocumentSummarizer.summarize(
                documentId = 1L, model = any(), systemPrompt = any(), question = null,
                onProgress = any(),
            )
        } returns flow { throw NoChunksException(1L) }

        try {
            useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
            assertTrue("Expected NoChunksException", false)
        } catch (e: NoChunksException) {
            assertEquals(1L, e.documentId)
        }
    }

    // ── Page mode ──────────────────────────────────────

    @Test
    fun `page mode summarizes chunks from specified page`() = runTest {
        stubCacheMiss()
        val chunks = listOf(
            chunk(1, 0, "第1页文本"),
            chunk(2, 2, "第3页文本A"),
            chunk(3, 2, "第3页文本B"),
        )
        coEvery { documentRepository.getChunksByPage(1L, 2) } returns chunks
        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returns stubStream("页3", "内容")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Page(2)).toList()
        assertEquals(listOf("页3", "内容"), tokens)
    }

    @Test
    fun `page mode throws when no chunks on that page`() = runTest {
        stubCacheMiss()
        coEvery { documentRepository.getChunksByPage(1L, 4) } returns emptyList()

        try {
            useCase(1L, "gemma-3-4b", SummaryScope.Page(4)).toList()
            assertTrue("Expected NoChunksForPageException", false)
        } catch (e: NoChunksForPageException) {
            assertTrue(e.message!!.contains("5"))
        }
    }

    @Test
    fun `page mode filters out null-embedding chunks`() = runTest {
        stubCacheMiss()
        val chunks = listOf(
            DocumentChunk(id = 1, documentId = 1L, pageIndex = 2, chunkIndex = 0, text = "有效", embedding = floatArrayOf(1f)),
            DocumentChunk(id = 2, documentId = 1L, pageIndex = 2, chunkIndex = 1, text = "无效", embedding = null),
            DocumentChunk(id = 3, documentId = 1L, pageIndex = 2, chunkIndex = 2, text = "空向量", embedding = floatArrayOf()),
        )
        coEvery { documentRepository.getChunksByPage(1L, 2) } returns chunks
        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returns stubStream("只", "有效")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Page(2)).toList()
        assertEquals(listOf("只", "有效"), tokens)
    }

    // ── Error propagation ──────────────────────────────

    @Test
    fun `propagates exception when page LLM call fails`() = runTest {
        stubCacheMiss()
        val chunks = listOf(chunk(1, 0, "chunk"))
        coEvery { documentRepository.getChunksByPage(1L, 0) } returns chunks
        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returns flow { throw IOException("network error") }

        try {
            useCase(1L, "gemma-3-4b", SummaryScope.Page(0)).toList()
            assertTrue("Expected IOException", false)
        } catch (e: IOException) {
            assertEquals("network error", e.message)
        }
    }

    // ── Progress callback ──────────────────────────────

    @Test
    fun `full summary forwards progress callback`() = runTest {
        stubCacheMiss()
        val events = mutableListOf<FullDocumentProgress>()
        val progressSlot = slot<(FullDocumentProgress) -> Unit>()
        coEvery {
            fullDocumentSummarizer.summarize(
                documentId = 1L,
                model = "gemma-3-4b",
                systemPrompt = any(),
                question = null,
                onProgress = capture(progressSlot),
            )
        } answers {
            progressSlot.captured.invoke(FullDocumentProgress.Preparing)
            flowOf("done")
        }

        useCase(
            documentId = 1L,
            model = "gemma-3-4b",
            scope = SummaryScope.Full,
            onProgress = events::add,
        ).toList()

        assertEquals(listOf(FullDocumentProgress.Preparing), events)
    }
}
