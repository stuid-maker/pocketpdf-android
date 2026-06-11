package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.RetrievalResult
import com.asuka.pocketpdf.domain.repository.LlmRepository
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

class AskDocumentUseCaseTest {

    private val retrieveChunks = mockk<RetrieveChunksUseCase>()
    private val llmRepository = mockk<LlmRepository>()
    private val queryIntentRouter = mockk<QueryIntentRouter>(relaxed = true)
    private val fullDocumentSummarizer = mockk<FullDocumentSummarizer>(relaxed = true)
    private val useCase = AskDocumentUseCase(
        retrieveChunks, llmRepository, queryIntentRouter, fullDocumentSummarizer,
    )

    private fun result(id: Long, pageIndex: Int, text: String, score: Float = 0.9f) =
        RetrievalResult(
            chunk = DocumentChunk(
                id = id, documentId = 1L, pageIndex = pageIndex, chunkIndex = 0, text = text,
                embedding = floatArrayOf(1f),
            ),
            score = score,
        )

    private fun stubStream(vararg tokens: String): Flow<String> = flow {
        tokens.forEach { emit(it) }
    }

    // ── Global queries route to FullDocumentSummarizer ─

    @Test
    fun `full document query routes to FullDocumentSummarizer`() = runTest {
        coEvery {
            queryIntentRouter.route("总结全文", any())
        } returns QueryIntent.FULL_DOCUMENT
        coEvery {
            fullDocumentSummarizer.summarize(
                documentId = 1L, model = "g", systemPrompt = any(),
                question = "总结全文",
                onProgress = any(),
            )
        } returns stubStream("全文", "总结")

        val tokens = useCase(1L, "总结全文", "g").toList()
        assertEquals(listOf("全文", "总结"), tokens)
    }

    // ── Focused queries use Top-K ──────────────────────

    @Test
    fun `focused query uses Top-K retrieval`() = runTest {
        coEvery {
            queryIntentRouter.route("金额是多少", any())
        } returns QueryIntent.TOP_K

        val results = listOf(
            result(1, 2, "金额为100万元"),
        )
        coEvery { retrieveChunks(1L, "金额是多少", 5) } returns results
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns stubStream("金额", "100万")

        val tokens = useCase(1L, "金额是多少", "g").toList()
        assertEquals(listOf("金额", "100万"), tokens)
    }

    // ── Top-K: no match fallback ───────────────────────

    @Test
    fun `top_k returns fallback text when no chunks match`() = runTest {
        coEvery {
            queryIntentRouter.route("xxx", any())
        } returns QueryIntent.TOP_K
        coEvery { retrieveChunks(1L, "xxx", 5) } returns emptyList()

        val tokens = useCase(1L, "xxx", "g").toList()
        assertEquals(1, tokens.size)
        assertTrue(tokens[0].contains("未在文档中找到"))
    }

    // ── Top-K: error propagation ───────────────────────

    @Test
    fun `propagates LLM exception in Top-K path`() = runTest {
        coEvery {
            queryIntentRouter.route("q", any())
        } returns QueryIntent.TOP_K
        val results = listOf(result(1, 0, "text"))
        coEvery { retrieveChunks(1L, "q", 5) } returns results
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flow { throw IOException("fail") }

        try {
            useCase(1L, "q", "g").toList()
            assertTrue("Expected IOException", false)
        } catch (e: IOException) {
            assertEquals("fail", e.message)
        }
    }

    // ── Progress callback ──────────────────────────────

    @Test
    fun `top k query does not report full document progress`() = runTest {
        val events = mutableListOf<FullDocumentProgress>()
        coEvery { queryIntentRouter.route(any(), any()) } returns QueryIntent.TOP_K
        coEvery { retrieveChunks(any(), any(), any()) } returns emptyList()

        useCase(1L, "金额是多少", "g", onProgress = events::add).toList()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `full document chat query forwards progress`() = runTest {
        val events = mutableListOf<FullDocumentProgress>()
        coEvery { queryIntentRouter.route("总结全文", any()) } returns QueryIntent.FULL_DOCUMENT
        val progressSlot = slot<(FullDocumentProgress) -> Unit>()
        coEvery {
            fullDocumentSummarizer.summarize(
                documentId = 1L, model = any(), systemPrompt = any(),
                question = "总结全文", onProgress = capture(progressSlot),
            )
        } answers {
            progressSlot.captured.invoke(FullDocumentProgress.Preparing)
            flowOf("全文", "总结")
        }

        useCase(1L, "总结全文", "g", onProgress = events::add).toList()

        assertEquals(listOf(FullDocumentProgress.Preparing), events)
    }
}
