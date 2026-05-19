package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteDocumentUseCaseTest {

    private val repository = mockk<DocumentRepository>()
    private val useCase = DeleteDocumentUseCase(repository)

    @Test
    fun `delegates to repository and surfaces Success`() = runTest {
        coEvery { repository.deleteDocument(7L) } returns Result.Success(Unit)

        val result = useCase(7L)

        assertTrue("expected Success, got $result", result is Result.Success)
        coVerify(exactly = 1) { repository.deleteDocument(7L) }
    }

    @Test
    fun `propagates Failure from repository unchanged`() = runTest {
        val error = IllegalStateException("Document #7 not found")
        coEvery { repository.deleteDocument(7L) } returns Result.Failure(error)

        val result = useCase(7L)

        assertTrue("expected Failure, got $result", result is Result.Failure)
        assertEquals(error, (result as Result.Failure).error)
    }
}
