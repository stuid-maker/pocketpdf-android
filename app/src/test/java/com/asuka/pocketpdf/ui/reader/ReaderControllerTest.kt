package com.asuka.pocketpdf.ui.reader

import android.graphics.Bitmap
import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReaderControllerTest {

    @Test
    fun renderClampsPageToDocumentBounds() = runTest {
        val session = mockk<PdfDocumentSession>()
        val bitmap = mockk<Bitmap>()
        every { session.pageCount } returns 3
        coEvery { session.render(any(), any()) } returns bitmap
        val controller = PdfReaderController(
            sessionFactory = { session },
            dispatchers = TestDispatcherProvider(),
            scope = this,
            renderWidth = { 1200 },
        )

        controller.open(DOCUMENT, initialPage = 99)

        coVerify { session.render(2, 1200) }
    }

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
