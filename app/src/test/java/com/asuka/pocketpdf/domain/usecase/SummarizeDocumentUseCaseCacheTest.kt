package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarizeDocumentUseCaseCacheTest {

    private val retrieveChunks = mockk<RetrieveChunksUseCase>()
    private val documentRepository = mockk<DocumentRepository>()
    private val llmRepository = mockk<LlmRepository>()
    private val summaryCacheRepository = mockk<SummaryCacheRepository>(relaxUnitFun = true)
    private val fullDocumentSummarizer = mockk<FullDocumentSummarizer>(relaxed = true)
    private val useCase = SummarizeDocumentUseCase(
        retrieveChunks, documentRepository, llmRepository,
        summaryCacheRepository, fullDocumentSummarizer,
    )

    @Test
    fun `returns cached value when cache hits for Full scope`() = runTest {
        every {
            summaryCacheRepository.get(
                documentId = 1L, scope = SummaryScope.Full,
                algorithmVersion = any(), model = any(), systemPrompt = any(),
            )
        } returns flowOf("缓存的全文总结")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
        assertEquals(listOf("缓存的全文总结"), tokens)
    }

    @Test
    fun `returns cached value when cache hits for Page scope`() = runTest {
        every {
            summaryCacheRepository.get(
                documentId = 1L, scope = SummaryScope.Page(2),
                algorithmVersion = any(), model = any(), systemPrompt = any(),
            )
        } returns flowOf("缓存的第3页总结")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Page(2)).toList()
        assertEquals(listOf("缓存的第3页总结"), tokens)
    }

    @Test
    fun `cache hit does not call LLM`() = runTest {
        every {
            summaryCacheRepository.get(
                documentId = 1L, scope = SummaryScope.Full,
                algorithmVersion = any(), model = any(), systemPrompt = any(),
            )
        } returns flowOf("cached")

        useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
        // Neither summarizer nor LLM should be called
    }

    // ── Progress callback ──────────────────────────────

    @Test
    fun `cache hit does not call progress callback`() = runTest {
        val events = mutableListOf<FullDocumentProgress>()
        every {
            summaryCacheRepository.get(any(), any(), any(), any(), any())
        } returns flowOf("cached")

        useCase(1L, "gemma-3-4b", SummaryScope.Full, onProgress = events::add).toList()

        assertTrue(events.isEmpty())
    }
}
