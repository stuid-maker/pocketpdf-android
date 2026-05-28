package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.LlmModel
import com.asuka.pocketpdf.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * GetAvailableModelsUseCase 测试：验证直接委托给 LlmRepository.listModels()。
 */
class GetAvailableModelsUseCaseTest {

    private val repository = mockk<LlmRepository>()
    private val useCase = GetAvailableModelsUseCase(repository)

    @Test
    fun `invoke delegates to repository and returns Success`() = runTest {
        val models = listOf(
            LlmModel(id = "gemma-3-4b", ownedBy = "google"),
            LlmModel(id = "llama-3.2-3b", ownedBy = "meta"),
        )
        coEvery { repository.listModels() } returns Result.Success(models)

        val result = useCase()

        assertTrue("expected Success, got $result", result is Result.Success)
        assertEquals(models, (result as Result.Success).data)
        coVerify(exactly = 1) { repository.listModels() }
    }

    @Test
    fun `invoke returns Failure when repository fails`() = runTest {
        val boom = IOException("connection refused")
        coEvery { repository.listModels() } returns Result.Failure(boom)

        val result = useCase()

        assertTrue("expected Failure, got $result", result is Result.Failure)
        assertEquals(boom, (result as Result.Failure).error)
    }

    @Test
    fun `invoke returns empty list when no models available`() = runTest {
        coEvery { repository.listModels() } returns Result.Success(emptyList())

        val result = useCase()

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
    }
}
