package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.LlmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * LlmRepository 接口契约测试：验证接口语义约束。
 */
class LlmRepositoryContractTest {

    private class FakeLlmRepository : LlmRepository {
        var shouldFail = false

        override suspend fun listModels(): Result<List<LlmModel>> {
            return if (shouldFail) {
                Result.Failure(IOException("network error"))
            } else {
                Result.Success(
                    listOf(
                        LlmModel(id = "gemma-3-4b", ownedBy = "google"),
                    ),
                )
            }
        }

        override fun chatCompletionStream(
            model: String,
            messages: List<ChatMessage>,
            temperature: Float?,
        ): Flow<String> = flowOf("token1", "token2")

        override suspend fun testConnection(baseUrl: String): Result<List<LlmModel>> {
            return if (shouldFail) {
                Result.Failure(IOException("connection failed"))
            } else {
                Result.Success(
                    listOf(LlmModel(id = "test-model", ownedBy = "test")),
                )
            }
        }
    }

    @Test
    fun `listModels returns models`() = runTest {
        val repo = FakeLlmRepository()
        val result = repo.listModels()
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        assertEquals("gemma-3-4b", result.data[0].id)
    }

    @Test
    fun `listModels returns Failure on error`() = runTest {
        val repo = FakeLlmRepository()
        repo.shouldFail = true
        val result = repo.listModels()
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `chatCompletionStream emits tokens`() = runTest {
        val repo = FakeLlmRepository()
        val tokens = repo.chatCompletionStream(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "hi")),
        ).toList()
        assertEquals(listOf("token1", "token2"), tokens)
    }

    @Test
    fun `testConnection returns Success on reachable endpoint`() = runTest {
        val repo = FakeLlmRepository()
        val result = repo.testConnection("http://localhost:1234")
        assertTrue(result is Result.Success)
    }

    @Test
    fun `testConnection returns Failure on unreachable endpoint`() = runTest {
        val repo = FakeLlmRepository()
        repo.shouldFail = true
        val result = repo.testConnection("http://unreachable")
        assertTrue(result is Result.Failure)
    }
}
