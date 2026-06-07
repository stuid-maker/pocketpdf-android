package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SummarizeDocumentUseCase 缓存命中测试。
 */
class SummarizeDocumentUseCaseCacheTest {

    private val retrieveChunks = mockk<RetrieveChunksUseCase>()
    private val documentRepository = mockk<DocumentRepository>()
    private val llmRepository = mockk<LlmRepository>()
    private val summaryCacheRepository = mockk<SummaryCacheRepository>(relaxUnitFun = true)
    private val useCase = SummarizeDocumentUseCase(
        retrieveChunks, documentRepository, llmRepository, summaryCacheRepository,
    )

    @Test
    fun `returns cached value when cache hits for Full scope`() = runTest {
        every { summaryCacheRepository.get(1L, SummaryScope.Full) } returns flowOf("缓存的全文总结")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()

        assertEquals(listOf("缓存的全文总结"), tokens)
    }

    @Test
    fun `returns cached value when cache hits for Page scope`() = runTest {
        every {
            summaryCacheRepository.get(1L, SummaryScope.Page(2))
        } returns flowOf("缓存的第3页总结")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Page(2)).toList()

        assertEquals(listOf("缓存的第3页总结"), tokens)
    }

    @Test
    fun `cache hit does not call LLM`() = runTest {
        every { summaryCacheRepository.get(1L, SummaryScope.Full) } returns flowOf("cached")

        useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()

        // LLM 不应该被调用
        coEvery { retrieveChunks(any(), any(), any()) } returns emptyList()
    }
}
