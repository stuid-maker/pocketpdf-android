package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.data.local.dao.ChunkDao
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.domain.pdf.PdfTextExtractor
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfDocumentSession
import com.asuka.pocketpdf.data.repository.DocumentRepositoryImpl
import com.asuka.pocketpdf.data.storage.FileStorage
import com.asuka.pocketpdf.domain.model.IndexStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class ImportDocumentUseCaseTest {

    private val dao = mockk<DocumentDao>()
    private val chunkDao = mockk<ChunkDao>()
    private val fileStorage = mockk<FileStorage>()
    private val extractor = mockk<PdfTextExtractor>()
    private val pdfDocumentEngine = mockk<PdfDocumentEngine>()
    private val impl = DocumentRepositoryImpl(
        dao = dao,
        chunkDao = chunkDao,
        fileStorage = fileStorage,
        pdfTextExtractor = extractor,
        pdfDocumentEngine = pdfDocumentEngine,
        dispatchers = TestDispatcherProvider(),
    )
    private val useCase = ImportDocumentUseCase(impl)

    /** Create a mock session with the given pageCount */
    private fun mockSession(pageCount: Int): PdfDocumentSession {
        val session = mockk<PdfDocumentSession>()
        every { session.pageCount } returns pageCount
        every { session.close() } returns Unit
        return session
    }

    @Test
    fun `happy path returns Success with document carrying inserted id and parsed pageCount`() =
        runTest {
            val copied = File.createTempFile("happy", ".pdf").apply { deleteOnExit() }
            val sourceUri = "content://saf/doc/42"
            val displayName = "spec.pdf"
            val capturedEntity = slot<DocumentEntity>()
            val session = mockSession(3)

            coEvery { fileStorage.copyToInternal(sourceUri, displayName) } returns copied
            coEvery { pdfDocumentEngine.open(copied) } returns session
            coEvery { dao.insert(capture(capturedEntity)) } returns 99L

            val result = useCase(sourceUri, displayName)

            assertTrue("expected Success, got $result", result is Result.Success)
            val document = (result as Result.Success).data
            assertEquals(99L, document.id)
            assertEquals(displayName, document.title)
            assertEquals(copied.absolutePath, document.uri)
            assertEquals(3, document.pageCount)
            assertEquals(IndexStatus.NOT_INDEXED, document.indexStatus)
            assertEquals("NOT_INDEXED", capturedEntity.captured.indexStatus)
            coVerify(exactly = 1) { fileStorage.copyToInternal(sourceUri, displayName) }
            coVerify(exactly = 1) { pdfDocumentEngine.open(copied) }
            coVerify(exactly = 1) { dao.insert(any()) }
        }

    @Test
    fun `copyToInternal failure surfaces as Result Failure and storage owns its own cleanup`() =
        runTest {
            val boom = IOException("SAF denied read access")
            coEvery { fileStorage.copyToInternal(any(), any()) } throws boom

            val result = useCase("content://denied", "denied.pdf")

            assertTrue(result is Result.Failure)
            assertSame(boom, (result as Result.Failure).error)
            coVerify(exactly = 0) { pdfDocumentEngine.open(any()) }
            coVerify(exactly = 0) { dao.insert(any()) }
        }

    @Test
    fun `pdf engine failure deletes copied file and surfaces Failure (rollback)`() = runTest {
        val copied = File.createTempFile("brokenpdf", ".pdf").apply {
            writeText("not a real pdf")
            deleteOnExit()
        }
        val boom = RuntimeException("PDF parse error: corrupted xref")
        coEvery { fileStorage.copyToInternal(any(), any()) } returns copied
        coEvery { pdfDocumentEngine.open(copied) } throws boom

        val result = useCase("content://broken", "broken.pdf")

        assertTrue(result is Result.Failure)
        assertSame(boom, (result as Result.Failure).error)
        assertFalse(
            "rollback contract violated: copied file should be deleted on engine failure",
            copied.exists(),
        )
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `dao insert failure deletes copied file and surfaces Failure (rollback)`() = runTest {
        val copied = File.createTempFile("daoboom", ".pdf").apply {
            writeText("pdf payload")
            deleteOnExit()
        }
        val boom = IllegalStateException("Room insert failed: disk full")
        coEvery { fileStorage.copyToInternal(any(), any()) } returns copied
        coEvery { pdfDocumentEngine.open(copied) } returns mockSession(1)
        coEvery { dao.insert(any()) } throws boom

        val result = useCase("content://x", "x.pdf")

        assertTrue(result is Result.Failure)
        assertSame(boom, (result as Result.Failure).error)
        assertFalse(
            "rollback contract violated: copied file should be deleted on dao failure",
            copied.exists(),
        )
    }
}
