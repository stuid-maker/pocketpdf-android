package com.asuka.pocketpdf.data.pdf

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.legere.pdfiumandroid.PdfiumCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PdfiumSmokeTest {

    @Test
    fun opensExtractsAndRendersSynthesizedPdf() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PDFBoxResourceLoader.init(context)
        val pdfBytes = synthesizePdf("Pocket PDFium smoke test")

        PdfiumCore(context).newDocument(pdfBytes).use { document ->
            assertEquals(1, document.getPageCount())

            document.openPage(0).use { page ->
                assertTrue(page.getPageWidthPoint() > 0)
                assertTrue(page.getPageHeightPoint() > 0)

                page.openTextPage().use { textPage ->
                    val count = textPage.textPageCountChars()
                    val text = textPage.textPageGetText(0, count).orEmpty()
                    assertTrue("Extracted text was '$text'", text.contains("Pocket PDFium smoke test"))
                }

                val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
                page.renderPageBitmap(
                    bitmap = bitmap,
                    startX = 0,
                    startY = 0,
                    drawSizeX = bitmap.width,
                    drawSizeY = bitmap.height,
                )
                assertTrue(bitmapHasNonTransparentPixel(bitmap))
                bitmap.recycle()
            }
        }
    }

    private fun synthesizePdf(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText(text)
                stream.endText()
            }
            document.save(output)
        }
        return output.toByteArray()
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
