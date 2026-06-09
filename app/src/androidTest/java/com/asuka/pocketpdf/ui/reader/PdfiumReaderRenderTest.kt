package com.asuka.pocketpdf.ui.reader

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.data.pdf.PdfiumDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfRenderRequest
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Visual parity verification tests for PdfiumDocumentEngine + PdfiumDocumentSession.
 *
 * Synthesizes PDF documents with known dimensions and verifies that:
 * - Page info reports correct width, height, and rotation.
 * - Render produces a bitmap of the requested size with visible content.
 * - Text extraction returns the expected synthesized text.
 *
 * These tests run on device as Android instrumentation tests (androidTest).
 */
@RunWith(AndroidJUnit4::class)
class PdfiumReaderRenderTest {

    private lateinit var context: android.content.Context
    private lateinit var dispatchers: DispatcherProvider
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val io = Dispatchers.IO
            override val default = Dispatchers.Default
        }
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
    }

    // ── Portrait (600×800 pts) ────────────────────────────────────────

    @Test
    fun portraitPage_reportsCorrectDimensionsAndRendersText() = runBlocking {
        val pdfFile = synthesizePdf(
            width = 600f,
            height = 800f,
            text = "Portrait parity check",
            rotation = 0,
        )
        val engine = PdfiumDocumentEngine(context, dispatchers)

        engine.open(pdfFile).use { session ->
            assertEquals("page count", 1, session.pageCount)

            val pageInfo = session.pageInfo(0)
            assertEquals("widthPoints", 600f, pageInfo.widthPoints, 0.1f)
            assertEquals("heightPoints", 800f, pageInfo.heightPoints, 0.1f)
            assertEquals("rotationDegrees", 0, pageInfo.rotationDegrees)

            // Render at 1:1 pixel scale
            val request = PdfRenderRequest(pageInfo, widthPx = 600, heightPx = 800)
            val bitmap = session.render(request)
            assertNotNull(bitmap)
            assertEquals("bitmap width", 600, bitmap.width)
            assertEquals("bitmap height", 800, bitmap.height)
            assertTrue("rendered bitmap must contain visible pixels", bitmapHasNonTransparentPixel(bitmap))
            bitmap.recycle()

            val pageText = session.extractText(0)
            assertTrue(
                "extracted text should contain synthesized content",
                pageText.text.contains("Portrait parity check"),
            )
        }
    }

    // ── Landscape (800×600 pts) ───────────────────────────────────────

    @Test
    fun landscapePage_reportsCorrectDimensionsAndRendersText() = runBlocking {
        val pdfFile = synthesizePdf(
            width = 800f,
            height = 600f,
            text = "Landscape parity check",
            rotation = 0,
        )
        val engine = PdfiumDocumentEngine(context, dispatchers)

        engine.open(pdfFile).use { session ->
            assertEquals("page count", 1, session.pageCount)

            val pageInfo = session.pageInfo(0)
            assertEquals("widthPoints", 800f, pageInfo.widthPoints, 0.1f)
            assertEquals("heightPoints", 600f, pageInfo.heightPoints, 0.1f)
            assertEquals("rotationDegrees", 0, pageInfo.rotationDegrees)

            val request = PdfRenderRequest(pageInfo, widthPx = 800, heightPx = 600)
            val bitmap = session.render(request)
            assertNotNull(bitmap)
            assertEquals("bitmap width", 800, bitmap.width)
            assertEquals("bitmap height", 600, bitmap.height)
            assertTrue("rendered bitmap must contain visible pixels", bitmapHasNonTransparentPixel(bitmap))
            bitmap.recycle()

            val pageText = session.extractText(0)
            assertTrue(
                "extracted text should contain synthesized content",
                pageText.text.contains("Landscape parity check"),
            )
        }
    }

    // ── Rotated (600×800 pts, 90° rotation) ──────────────────────────

    @Test
    fun rotatedPage_reportsCorrectRotationAndStillRenders() = runBlocking {
        val pdfFile = synthesizePdf(
            width = 600f,
            height = 800f,
            text = "Rotated parity check",
            rotation = 90,
        )
        val engine = PdfiumDocumentEngine(context, dispatchers)

        engine.open(pdfFile).use { session ->
            assertEquals("page count", 1, session.pageCount)

            val pageInfo = session.pageInfo(0)
            // Raw media box dimensions remain the same; rotation is separate.
            assertEquals("widthPoints", 600f, pageInfo.widthPoints, 0.1f)
            assertEquals("heightPoints", 800f, pageInfo.heightPoints, 0.1f)
            assertEquals("rotationDegrees", 90, pageInfo.rotationDegrees)

            // Render with swapped dimensions to account for rotation.
            val request = PdfRenderRequest(pageInfo, widthPx = 800, heightPx = 600)
            val bitmap = session.render(request)
            assertNotNull(bitmap)
            assertEquals("bitmap width", 800, bitmap.width)
            assertEquals("bitmap height", 600, bitmap.height)
            assertTrue("rendered bitmap must contain visible pixels", bitmapHasNonTransparentPixel(bitmap))
            bitmap.recycle()

            val pageText = session.extractText(0)
            assertTrue(
                "extracted text should contain synthesized content",
                pageText.text.contains("Rotated parity check"),
            )
        }
    }

    // ── Multi-page PDF ────────────────────────────────────────────────

    @Test
    fun multiPagePdf_eachPageHasIndependentContent() = runBlocking {
        val pdfFile = synthesizeMultiPagePdf(
            pages = listOf(
                Triple(500f, 700f, "Page one"),
                Triple(500f, 700f, "Page two"),
                Triple(700f, 500f, "Page three landscape"),
            ),
        )
        val engine = PdfiumDocumentEngine(context, dispatchers)

        engine.open(pdfFile).use { session ->
            assertEquals("page count", 3, session.pageCount)

            // Page 0
            val info0 = session.pageInfo(0)
            assertEquals(500f, info0.widthPoints, 0.1f)
            assertEquals(700f, info0.heightPoints, 0.1f)
            assertTrue(session.extractText(0).text.contains("Page one"))

            // Page 1
            val info1 = session.pageInfo(1)
            assertEquals(500f, info1.widthPoints, 0.1f)
            assertEquals(700f, info1.heightPoints, 0.1f)
            assertTrue(session.extractText(1).text.contains("Page two"))

            // Page 2 (landscape)
            val info2 = session.pageInfo(2)
            assertEquals(700f, info2.widthPoints, 0.1f)
            assertEquals(500f, info2.heightPoints, 0.1f)
            assertTrue(session.extractText(2).text.contains("Page three landscape"))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun synthesizePdf(
        width: Float,
        height: Float,
        text: String,
        rotation: Int,
    ): File {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(width, height))
            if (rotation > 0) {
                page.rotation = rotation
            }
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, height - 50f)
                stream.showText(text)
                stream.endText()
            }
            document.save(output)
        }
        val file = File.createTempFile("pdfium_render_test_", ".pdf", context.cacheDir)
        file.writeBytes(output.toByteArray())
        tempFiles.add(file)
        return file
    }

    private fun synthesizeMultiPagePdf(
        pages: List<Triple<Float, Float, String>>,
    ): File {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            for ((width, height, text) in pages) {
                val page = PDPage(PDRectangle(width, height))
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, height - 50f)
                    stream.showText(text)
                    stream.endText()
                }
            }
            document.save(output)
        }
        val file = File.createTempFile("pdfium_render_multi_", ".pdf", context.cacheDir)
        file.writeBytes(output.toByteArray())
        tempFiles.add(file)
        return file
    }

    private fun bitmapHasNonTransparentPixel(bitmap: Bitmap): Boolean {
        for (y in 0 until bitmap.height step 16) {
            for (x in 0 until bitmap.width step 16) {
                if (bitmap.getPixel(x, y) ushr 24 != 0) return true
            }
        }
        return false
    }
}
