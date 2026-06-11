package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.domain.model.SearchResult
import com.asuka.pocketpdf.domain.pdf.PdfPageRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchHighlightLayoutTest {

    @Test
    fun `current rectangle index accounts for earlier multiline matches`() {
        val first = result(
            pageIndex = 0,
            rects = listOf(
                PdfPageRect(0f, 0f, 10f, 10f),
                PdfPageRect(0f, 12f, 10f, 22f),
            ),
        )
        val second = result(
            pageIndex = 0,
            rects = listOf(PdfPageRect(20f, 0f, 30f, 10f)),
        )

        val layout = buildSearchHighlightLayout(
            results = listOf(first, second),
            currentMatchIndex = 1,
            pageIndex = 0,
            bitmapWidth = 100,
            bitmapHeight = 100,
        )

        assertEquals(3, layout.rects.size)
        assertEquals(setOf(2), layout.currentRectIndices)
    }

    @Test
    fun `all rectangles in current multiline match are active`() {
        val layout = buildSearchHighlightLayout(
            results = listOf(
                result(0, listOf(PdfPageRect(0f, 0f, 10f, 10f))),
                result(
                    0,
                    listOf(
                        PdfPageRect(20f, 0f, 30f, 10f),
                        PdfPageRect(20f, 12f, 30f, 22f),
                    ),
                ),
            ),
            currentMatchIndex = 1,
            pageIndex = 0,
            bitmapWidth = 100,
            bitmapHeight = 100,
        )

        assertEquals(setOf(1, 2), layout.currentRectIndices)
    }

    @Test
    fun `current rectangle is absent when active match is on another page`() {
        val layout = buildSearchHighlightLayout(
            results = listOf(
                result(0, listOf(PdfPageRect(0f, 0f, 10f, 10f))),
                result(1, listOf(PdfPageRect(0f, 0f, 10f, 10f))),
            ),
            currentMatchIndex = 1,
            pageIndex = 0,
            bitmapWidth = 100,
            bitmapHeight = 100,
        )

        assertEquals(emptySet<Int>(), layout.currentRectIndices)
    }

    @Test
    fun `active search page follows current result`() {
        val results = listOf(
            result(0, listOf(PdfPageRect(0f, 0f, 10f, 10f))),
            result(3, listOf(PdfPageRect(0f, 0f, 10f, 10f))),
        )

        assertEquals(3, activeSearchPage(results, 1))
        assertNull(activeSearchPage(results, -1))
        assertNull(activeSearchPage(results, 9))
    }

    private fun result(pageIndex: Int, rects: List<PdfPageRect>) = SearchResult(
        pageIndex = pageIndex,
        matchText = "match",
        matchIndex = 0,
        positions = emptyList(),
        pdfPageWidth = 100f,
        pdfPageHeight = 100f,
        rects = rects,
    )
}
