package com.asuka.pocketpdf.domain.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfModelsTest {

    @Test
    fun `page info rejects invalid values`() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageInfo(pageIndex = -1, widthPoints = 100f, heightPoints = 200f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageInfo(pageIndex = 0, widthPoints = 0f, heightPoints = 200f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfPageInfo(pageIndex = 0, widthPoints = 100f, heightPoints = Float.NaN)
        }
    }

    @Test
    fun `page rect normalizes coordinate order`() {
        val rect = PdfPageRect(left = 30f, top = 40f, right = 10f, bottom = 20f)

        assertEquals(10f, rect.left)
        assertEquals(20f, rect.top)
        assertEquals(30f, rect.right)
        assertEquals(40f, rect.bottom)
    }

    @Test
    fun `search match preserves multiple line rectangles`() {
        val firstLine = PdfPageRect(10f, 20f, 80f, 30f)
        val secondLine = PdfPageRect(10f, 32f, 45f, 42f)

        val match = PdfSearchMatch(
            pageIndex = 2,
            startIndex = 14,
            length = 11,
            text = "hello world",
            rects = listOf(firstLine, secondLine),
        )

        assertEquals(listOf(firstLine, secondLine), match.rects)
    }

    @Test
    fun `search match rejects empty geometry and invalid ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfSearchMatch(
                pageIndex = 0,
                startIndex = 0,
                length = 0,
                text = "",
                rects = listOf(PdfPageRect(0f, 0f, 1f, 1f)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfSearchMatch(
                pageIndex = 0,
                startIndex = 0,
                length = 1,
                text = "a",
                rects = emptyList(),
            )
        }
    }

    @Test
    fun `render request requires positive bitmap dimensions`() {
        val pageInfo = PdfPageInfo(0, 612f, 792f)

        assertThrows(IllegalArgumentException::class.java) {
            PdfRenderRequest(pageInfo, widthPx = 0, heightPx = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfRenderRequest(pageInfo, widthPx = 100, heightPx = -1)
        }
    }
}
