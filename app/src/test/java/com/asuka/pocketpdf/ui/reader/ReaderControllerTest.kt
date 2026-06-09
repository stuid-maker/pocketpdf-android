package com.asuka.pocketpdf.ui.reader

import android.graphics.Bitmap
import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfDocumentSession
import com.asuka.pocketpdf.domain.pdf.PdfPageInfo
import com.asuka.pocketpdf.domain.pdf.PdfRenderRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReaderControllerTest {

    @Test
    fun renderClampsPageAndUsesPageAspectRatio() = runTest {
        val session = mockSession()
        val engine = mockEngine(session)
        val request = slot<PdfRenderRequest>()
        coEvery { session.pageInfo(2) } returns PdfPageInfo(2, 600f, 800f)
        coEvery { session.render(capture(request)) } returns mockk()
        val controller = createController(engine, this)

        controller.open(DOCUMENT, initialPage = 99)

        assertEquals(2, request.captured.pageInfo.pageIndex)
        assertEquals(1200, request.captured.widthPx)
        assertEquals(1600, request.captured.heightPx)
    }

    @Test
    fun subsequentRendersReuseTheOpenedDocumentSession() = runTest {
        val session = mockSession()
        val engine = mockEngine(session)
        coEvery { session.pageInfo(any()) } answers {
            PdfPageInfo(firstArg(), 600f, 800f)
        }
        coEvery { session.render(any()) } returns mockk()
        val controller = createController(engine, this)

        controller.open(DOCUMENT, initialPage = 0)
        controller.render(1)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { engine.open(File(DOCUMENT.uri), null) }
        coVerify { session.render(match { it.pageInfo.pageIndex == 0 }) }
        coVerify { session.render(match { it.pageInfo.pageIndex == 1 }) }
    }

    @Test
    fun closeReleasesTheOpenedDocumentSession() = runTest {
        val session = mockSession()
        val engine = mockEngine(session)
        coEvery { session.pageInfo(0) } returns PdfPageInfo(0, 600f, 800f)
        coEvery { session.render(any()) } returns mockk<Bitmap>()
        val controller = createController(engine, this)

        controller.open(DOCUMENT, initialPage = 0)
        controller.close()

        verify(exactly = 1) { session.close() }
        assertEquals(ReaderPageState(), controller.state.value)
    }

    private fun mockSession(): PdfDocumentSession = mockk {
        every { pageCount } returns 3
        every { close() } returns Unit
    }

    private fun mockEngine(session: PdfDocumentSession): PdfDocumentEngine = mockk {
        coEvery { open(any(), any()) } returns session
    }

    private fun createController(
        engine: PdfDocumentEngine,
        scope: CoroutineScope,
    ) = PdfReaderController(
        documentEngine = engine,
        dispatchers = TestDispatcherProvider(),
        scope = scope,
        renderWidth = { 1200 },
    )

    private companion object {
        val DOCUMENT = Document(
            id = 1L,
            title = "paper.pdf",
            uri = "/paper.pdf",
            pageCount = 3,
            indexStatus = IndexStatus.INDEXED,
            importedAt = 1L,
        )
    }
}
