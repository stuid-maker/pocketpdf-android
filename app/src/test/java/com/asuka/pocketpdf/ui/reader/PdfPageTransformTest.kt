package com.asuka.pocketpdf.ui.reader

import android.graphics.RectF
import com.asuka.pocketpdf.domain.pdf.PdfPageRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfPageTransformTest {

    @Test
    fun `scaleX and scaleY correctly computed for 600pts to 1200px`() {
        val transform = PdfPageTransform(
            pdfPageWidthPoints = 600f,
            pdfPageHeightPoints = 800f,
            bitmapWidthPx = 1200,
            bitmapHeightPx = 1600,
        )

        assertEquals(2.0f, transform.scaleX)
        assertEquals(2.0f, transform.scaleY)
    }

    @Test
    fun `pdfRectToBitmapRect correctly maps a single rectangle`() {
        val transform = PdfPageTransform(
            pdfPageWidthPoints = 600f,
            pdfPageHeightPoints = 800f,
            bitmapWidthPx = 1200,
            bitmapHeightPx = 1600,
        )

        val pdfRect = PdfPageRect(left = 100f, top = 200f, right = 300f, bottom = 400f)
        val result = transform.pdfRectToBitmapRect(pdfRect)

        assertEquals(200f, result.left)
        assertEquals(400f, result.top)
        assertEquals(600f, result.right)
        assertEquals(800f, result.bottom)
    }

    @Test
    fun `pdfRectsToBitmapRects correctly maps multiple rectangles preserving multi-line`() {
        val transform = PdfPageTransform(
            pdfPageWidthPoints = 500f,
            pdfPageHeightPoints = 700f,
            bitmapWidthPx = 1000,
            bitmapHeightPx = 1400,
        )

        val rects = listOf(
            PdfPageRect(10f, 20f, 80f, 30f),
            PdfPageRect(10f, 32f, 45f, 42f),
        )
        val results = transform.pdfRectsToBitmapRects(rects)

        assertEquals(2, results.size)
        assertEquals(RectF(20f, 40f, 160f, 60f), results[0])
        assertEquals(RectF(20f, 64f, 90f, 84f), results[1])
    }

    @Test
    fun `origin rectangle maps correctly`() {
        val transform = PdfPageTransform(
            pdfPageWidthPoints = 612f,
            pdfPageHeightPoints = 792f,
            bitmapWidthPx = 1224,
            bitmapHeightPx = 1584,
        )

        val result = transform.pdfRectToBitmapRect(PdfPageRect(0f, 0f, 100f, 100f))

        assertEquals(0f, result.left)
        assertEquals(0f, result.top)
        assertEquals(200f, result.right)
        assertEquals(200f, result.bottom)
    }

    @Test
    fun `constructor rejects zero or negative values`() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageTransform(
                pdfPageWidthPoints = 0f,
                pdfPageHeightPoints = 800f,
                bitmapWidthPx = 1200,
                bitmapHeightPx = 1600,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageTransform(
                pdfPageWidthPoints = 600f,
                pdfPageHeightPoints = 0f,
                bitmapWidthPx = 1200,
                bitmapHeightPx = 1600,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageTransform(
                pdfPageWidthPoints = 600f,
                pdfPageHeightPoints = 800f,
                bitmapWidthPx = 0,
                bitmapHeightPx = 1600,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageTransform(
                pdfPageWidthPoints = 600f,
                pdfPageHeightPoints = 800f,
                bitmapWidthPx = 1200,
                bitmapHeightPx = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageTransform(
                pdfPageWidthPoints = -1f,
                pdfPageHeightPoints = 800f,
                bitmapWidthPx = 1200,
                bitmapHeightPx = 1600,
            )
        }
    }

    @Test
    fun `aspect ratio scaling handles landscape and portrait`() {
        // Landscape: 800x600
        val landscape = PdfPageTransform(
            pdfPageWidthPoints = 800f,
            pdfPageHeightPoints = 600f,
            bitmapWidthPx = 1600,
            bitmapHeightPx = 1200,
        )
        assertEquals(2.0f, landscape.scaleX)
        assertEquals(2.0f, landscape.scaleY)

        val landscapeRect = landscape.pdfRectToBitmapRect(PdfPageRect(100f, 50f, 300f, 150f))
        assertEquals(200f, landscapeRect.left)
        assertEquals(100f, landscapeRect.top)
        assertEquals(600f, landscapeRect.right)
        assertEquals(300f, landscapeRect.bottom)

        // Portrait: 600x800
        val portrait = PdfPageTransform(
            pdfPageWidthPoints = 600f,
            pdfPageHeightPoints = 800f,
            bitmapWidthPx = 900,
            bitmapHeightPx = 1200,
        )
        assertEquals(1.5f, portrait.scaleX)
        assertEquals(1.5f, portrait.scaleY)

        val portraitRect = portrait.pdfRectToBitmapRect(PdfPageRect(200f, 400f, 400f, 600f))
        assertEquals(300f, portraitRect.left)
        assertEquals(600f, portraitRect.top)
        assertEquals(600f, portraitRect.right)
        assertEquals(900f, portraitRect.bottom)
    }
}
