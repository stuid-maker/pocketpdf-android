package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.RetrievalResult
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 覆盖 [SummarizeDocumentUseCase] 的 MapReduce 摘要生成。
 *
 * 测试策略：mock [RetrieveChunksUseCase] 和 [LlmRepository]，
 * UseCase 本身用真实现。
 */
class SummarizeDocumentUseCaseTest {

    private val retrieveChunks = mockk<RetrieveChunksUseCase>()
    private val llmRepository = mockk<LlmRepository>()
    private val useCase = SummarizeDocumentUseCase(retrieveChunks, llmRepository)

    /** 构建一个简单的 RetrievalResult */
    private fun result(id: Long, pageIndex: Int, text: String, score: Float = 0.9f) =
        RetrievalResult(
            chunk = DocumentChunk(
                id = id,
                documentId = 1L,
                pageIndex = pageIndex,
                chunkIndex = 0,
                text = text,
            ),
            score = score,
        )

    /** 模拟 LLM 返回固定文本 */
    private fun stubStream(vararg chunks: String): Flow<String> = flow {
        chunks.forEach { emit(it) }
    }

    @Test
    fun `full mode produces merged summary from retrieved chunks`() = runTest {
        val results = listOf(
            result(1, 0, "机器学习是人工智能的核心分支"),
            result(2, 0, "深度学习使用多层神经网络"),
            result(3, 1, "自然语言处理是重要应用领域"),
        )
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns results

        // Map 阶段：每个 chunk 返回小结
        every {
            llmRepository.chatCompletionStream(
                model = "gemma-3-4b",
                messages = any(),
                temperature = null,
            )
        } returnsMany listOf(
            stubStream("机器学习", "核心分支"),         // chunk 1 的小结
            stubStream("深度", "学习"),                 // chunk 2 的小结
            stubStream("自然语言处理", "应用"),         // chunk 3 的小结
            stubStream("合并", "总结", "结果"),         // Reduce 合并
        )

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full, topK = 5).toList()
        assertEquals(listOf("合并", "总结", "结果"), tokens)
    }

    @Test
    fun `returns empty flow when no chunks match`() = runTest {
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns emptyList()

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `page mode filters to specified page only`() = runTest {
        val results = listOf(
            result(1, 0, "第1页内容"),
            result(2, 0, "第1页更多内容"),
            result(3, 2, "第3页内容"),
            result(4, 2, "第3页更多"),
        )
        coEvery { retrieveChunks(1L, "第 3 页的内容", 5) } returns results

        // 第 3 页 (pageIndex=2) 只有 2 个 chunk
        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returnsMany listOf(
            stubStream("页3片段1"),
            stubStream("页3片段2"),
            stubStream("页3合并"),
        )

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Page(2)).toList()
        assertEquals(listOf("页3合并"), tokens)
    }

    @Test
    fun `skips chunk when Map LLM call fails and continues with others`() = runTest {
        val results = listOf(
            result(1, 0, "chunk A"),
            result(2, 0, "chunk B"),
            result(3, 0, "chunk C"),
        )
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns results

        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returnsMany listOf(
            stubStream("summaryA"),               // chunk A 成功
            flow { throw IOException("fail") },    // chunk B 失败
            stubStream("summaryC"),               // chunk C 成功
            stubStream("merged"),                  // Reduce
        )

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
        // 应该跳过 B，用 A 和 C 的摘要合并
        assertEquals(listOf("merged"), tokens)
    }

    @Test
    fun `returns empty flow when all Map calls fail`() = runTest {
        val results = listOf(
            result(1, 0, "chunk A"),
            result(2, 0, "chunk B"),
        )
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns results

        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returns flow { throw IOException("all fail") }

        val tokens = useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `propagates exception when Reduce LLM call fails`() = runTest {
        val results = listOf(
            result(1, 0, "chunk A"),
        )
        coEvery { retrieveChunks(1L, "全文核心内容", 5) } returns results

        every {
            llmRepository.chatCompletionStream(model = "gemma-3-4b", messages = any(), temperature = null)
        } returnsMany listOf(
            stubStream("summaryA"),               // Map 成功
            flow { throw IOException("reduce fail") },  // Reduce 失败
        )

        try {
            useCase(1L, "gemma-3-4b", SummaryScope.Full).toList()
            assertTrue("Expected IOException", false)
        } catch (e: IOException) {
            assertEquals("reduce fail", e.message)
        }
    }
}
