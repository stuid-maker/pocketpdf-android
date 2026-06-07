package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteDocumentUseCaseTest {

    private val repository = mockk<DocumentRepository>()
    private val summaryCacheRepository = mockk<SummaryCacheRepository>(relaxUnitFun = true)
    private val useCase = DeleteDocumentUseCase(repository, summaryCacheRepository)

    @Test
    fun `delegates to repository and surfaces Success`() = runTest {
        coEvery { repository.deleteDocument(7L) } returns Result.Success(Unit)

        val result = useCase(7L)

        assertTrue("expected Success, got $result", result is Result.Success)
        coVerify(exactly = 1) { repository.deleteDocument(7L) }
        coVerify(exactly = 1) { summaryCacheRepository.invalidate(7L) }
    }

    @Test
    fun `propagates Failure from repository unchanged`() = runTest {
        val error = IllegalStateException("Document #7 not found")
        coEvery { repository.deleteDocument(7L) } returns Result.Failure(error)

        val result = useCase(7L)

        assertTrue("expected Failure, got $result", result is Result.Failure)
        assertEquals(error, (result as Result.Failure).error)
        coVerify(exactly = 0) { summaryCacheRepository.invalidate(any()) }
    }

    @Test
    fun `cache cleanup failure does not turn completed deletion into failure`() = runTest {
        coEvery { repository.deleteDocument(7L) } returns Result.Success(Unit)
        coEvery { summaryCacheRepository.invalidate(7L) } throws
            IllegalStateException("cache unavailable")

        val result = useCase(7L)

        assertTrue("expected Success, got $result", result is Result.Success)
    }
}
