package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.RetrievalResult
import com.asuka.pocketpdf.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AskDocumentUseCaseTest {

    private val retrieveChunks = mockk<RetrieveChunksUseCase>()
    private val llmRepository = mockk<LlmRepository>()
    private val useCase = AskDocumentUseCase(retrieveChunks, llmRepository)

    private fun result(id: Long, pageIndex: Int, text: String, score: Float = 0.9f) =
        RetrievalResult(
            chunk = DocumentChunk(
                id = id, documentId = 1L, pageIndex = pageIndex, chunkIndex = 0, text = text,
                embedding = floatArrayOf(1f),
            ),
            score = score,
        )

    private fun stubStream(vararg tokens: String): Flow<String> = flow { tokens.forEach { emit(it) } }

    @Test
    fun `streams LLM response with retrieved chunks`() = runTest {
        val results = listOf(
            result(1, 2, "深度学习使用多层神经网络"),
            result(2, 0, "机器学习是人工智能的核心"),
        )
        coEvery { retrieveChunks(1L, "什么是深度学习", 5) } returns results
        every {
            llmRepository.chatCompletionStream(model = "g", messages = any(), temperature = null)
        } returns stubStream("深度", "学习是", "机器学习的子集")

        val tokens = useCase(1L, "什么是深度学习", "g").toList()
        assertEquals(listOf("深度", "学习是", "机器学习的子集"), tokens)
    }

    @Test
    fun `returns fallback text when no chunks match`() = runTest {
        coEvery { retrieveChunks(1L, "xxx", 5) } returns emptyList()

        val tokens = useCase(1L, "xxx", "g").toList()
        assertEquals(1, tokens.size)
        assertTrue(tokens[0].contains("未在文档中找到"))
    }

    @Test
    fun `propagates LLM exception`() = runTest {
        val results = listOf(result(1, 0, "text"))
        coEvery { retrieveChunks(1L, "q", 5) } returns results
        every {
            llmRepository.chatCompletionStream(model = "g", messages = any(), temperature = null)
        } returns flow { throw IOException("fail") }

        try {
            useCase(1L, "q", "g").toList()
            assertTrue("Expected IOException", false)
        } catch (e: IOException) {
            assertEquals("fail", e.message)
        }
    }
}
