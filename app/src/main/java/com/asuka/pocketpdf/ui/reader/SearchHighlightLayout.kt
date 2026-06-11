package com.asuka.pocketpdf.ui.reader

import android.graphics.RectF
import com.asuka.pocketpdf.domain.model.SearchResult

data class SearchHighlightLayout(
    val rects: List<RectF>,
    val currentRectIndices: Set<Int>,
)

fun activeSearchPage(
    results: List<SearchResult>,
    currentMatchIndex: Int,
): Int? = results.getOrNull(currentMatchIndex)?.pageIndex

fun buildSearchHighlightLayout(
    results: List<SearchResult>,
    currentMatchIndex: Int,
    pageIndex: Int,
    bitmapWidth: Int,
    bitmapHeight: Int,
): SearchHighlightLayout {
    val rects = mutableListOf<RectF>()
    val currentRectIndices = mutableSetOf<Int>()

    results.forEachIndexed { resultIndex, result ->
        if (result.pageIndex != pageIndex || result.rects.isEmpty()) return@forEachIndexed
        val transform = PdfPageTransform(
            pdfPageWidthPoints = result.pdfPageWidth,
            pdfPageHeightPoints = result.pdfPageHeight,
            bitmapWidthPx = bitmapWidth,
            bitmapHeightPx = bitmapHeight,
        )
        val mappedRects = transform.pdfRectsToBitmapRects(result.rects)
        if (resultIndex == currentMatchIndex) {
            currentRectIndices += mappedRects.indices.map { rects.size + it }
        }
        rects += mappedRects
    }

    return SearchHighlightLayout(rects, currentRectIndices)
}
