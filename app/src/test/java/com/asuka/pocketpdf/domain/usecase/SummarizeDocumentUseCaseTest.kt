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
    private val useCase = SummarizeDocumentUseCase(
        retrieveChunks,
        documentRepository,
        llmRepository,
        summaryCacheRepository,
    )

    private fun result(id: Long, pageIndex: Int, text: String, score: Float = 0.9f) =
        RetrievalResult(
            chunk = chunk(id, pageIndex, text),
            score = score,
        )

    private fun chunk(id: Long, pageIndex: Int, text: String) =
        DocumentChunk(
            id = id, documentId = 1L, pageIndex = pageIndex, chunkIndex = 0, text = text,
            embedding = floatArrayOf(1f),  // 非空，否则过滤掉
        )

    private fun stubStream(vararg tokens: String): Flow<String> = flow {
        tokens.forEach { emit(it) }
    }

    /** 为每个测试设置缓存默认行为：缓存未命中（走 LLM） */
    private fun stubCacheMiss() {
        every { summaryCacheRepository.get(any(), any()) } returns flowOf(null)
    }

    // ── Full mode ──────────────────────────────────────

    @Test
    fun `full mode streams tokens from semantic retrieval`() = runTest {
        stubCacheMiss()
        val results = listOf(
            result(1, 0, "机器学习"),
            result(2, 0, "深度学习"),
            result(3, 1, "自然语言处理"),
        )
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns results
        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returns stubStream("全文", "总结")

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
        assertEquals(listOf("全文", "总结"), tokens)
    }

    @Test
    fun `full mode throws NoChunksException when document has no chunks`() = runTest {
        stubCacheMiss()
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns emptyList()

        try {
            useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
            assertTrue("Expected NoChunksException", false)
        } catch (e: NoChunksException) {
            // expected
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
        val chunks = emptyList<DocumentChunk>()
        coEvery { documentRepository.getChunksByPage(1L, 4) } returns chunks

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
    fun `propagates exception when LLM call fails`() = runTest {
        stubCacheMiss()
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns listOf(result(1, 0, "chunk"))
        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returns flow { throw IOException("network error") }

        try {
            useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
            assertTrue("Expected IOException", false)
        } catch (e: IOException) {
            assertEquals("network error", e.message)
        }
    }
}
