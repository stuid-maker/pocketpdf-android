package com.asuka.pocketpdf.ui.library

import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class DocumentCoverLoaderTest {

    @Test
    fun renderFailureReturnsStableFallback() = runTest {
        val renderer = object : PdfCoverRenderer {
            override suspend fun renderFirstPage(
                uri: String,
                widthPx: Int,
                heightPx: Int,
            ) = error("broken pdf")
        }
        val loader = PdfDocumentCoverLoader(renderer, TestDispatcherProvider())

        val result = loader.load(DOCUMENT, 180, 240)

        assertEquals(fallbackCover(DOCUMENT.id, DOCUMENT.title), result)
    }

    private companion object {
        val DOCUMENT = Document(
            id = 42L,
            title = "Effective Kotlin",
            uri = "/missing.pdf",
            pageCount = 3,
            indexStatus = IndexStatus.INDEXED,
            importedAt = 1L,
        )
    }
}
