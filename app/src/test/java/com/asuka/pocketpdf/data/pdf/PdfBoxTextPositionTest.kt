package com.asuka.pocketpdf.data.pdf

import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PdfBoxTextPositionTest {

    private lateinit var extractor: PdfBoxTextExtractor

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        extractor = PdfBoxTextExtractor(TestDispatcherProvider())
    }

    @Test
    fun `extractPagesTextWithPositions returns non-empty positions for a text pdf`() = runTest {
        val pdf = synthesizePdf(
            listOf("Hello World from page one."),
        )

        val pages = extractor.extractPagesTextWithPositions(pdf)

        assertEquals("page count must match", 1, pages.size)
        val page = pages[0]
        assertTrue("positions should not be empty", page.positions.isNotEmpty())

        // Each position should have valid coordinates
        val pos = page.positions.first()
        assertNotNull(pos.text)
        assertTrue("width should be positive", pos.width > 0)
    }

    @Test
    fun `extractPagesTextWithPositions returns correct page count`() = runTest {
        val pdf = synthesizePdf(
            listOf("Page 1", "Page 2", "Page 3"),
        )

        val pages = extractor.extractPagesTextWithPositions(pdf)

        assertEquals(3, pages.size)
        pages.forEachIndexed { index, page ->
            assertEquals(index, page.pageIndex)
        }
    }

    @Test
    fun `extractPagesTextWithPositions positions count is greater than zero`() = runTest {
        val pdf = synthesizePdf(
            listOf("Some text on page 1", "More text on page 2"),
        )

        val pages = extractor.extractPagesTextWithPositions(pdf)

        assertEquals(2, pages.size)
        pages.forEach { page ->
            assertTrue(
                "positions count should be > 0 for page ${page.pageIndex}",
                page.positions.size > 0,
            )
        }
    }

    private fun synthesizePdf(perPageText: List<String>): File {
        val file = File.createTempFile("synth_pos", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            perPageText.forEach { line ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText(line)
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }
}
