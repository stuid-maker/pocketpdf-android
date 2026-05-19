package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetDocumentUseCaseTest {

    private val repository = mockk<DocumentRepository>()
    private val useCase = GetDocumentUseCase(repository)

    @Test
    fun `returns document when id exists`() = runTest {
        val doc = Document(
            id = 42L,
            title = "thesis.pdf",
            uri = "/files/documents/abc.pdf",
            pageCount = 120,
            indexStatus = IndexStatus.INDEXED,
            importedAt = 1L,
        )
        coEvery { repository.getDocument(42L) } returns doc

        assertEquals(doc, useCase(42L))
        coVerify(exactly = 1) { repository.getDocument(42L) }
    }

    @Test
    fun `returns null when id is not found`() = runTest {
        coEvery { repository.getDocument(99L) } returns null

        assertNull(useCase(99L))
    }
}
