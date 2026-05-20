package com.asuka.pocketpdf.domain.usecase

import app.cash.turbine.test
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveDocumentsUseCaseTest {

    private val repository = mockk<DocumentRepository>()
    private val useCase = ObserveDocumentsUseCase(repository)

    @Test
    fun `invoke forwards repository observeDocuments emissions verbatim`() = runTest {
        val documents = listOf(
            Document(1L, "a.pdf", "/abs/a.pdf", 10, IndexStatus.NOT_INDEXED, 1_000L),
            Document(2L, "b.pdf", "/abs/b.pdf", 20, IndexStatus.INDEXED, 2_000L),
        )
        every { repository.observeDocuments() } returns flowOf(documents)

        useCase().test {
            assertEquals(documents, awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { repository.observeDocuments() }
    }

    @Test
    fun `invoke emits empty list when library is empty`() = runTest {
        every { repository.observeDocuments() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(emptyList<Document>(), awaitItem())
            awaitComplete()
        }
    }
}
