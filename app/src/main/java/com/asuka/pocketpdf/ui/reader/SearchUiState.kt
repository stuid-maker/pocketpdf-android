package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.data.pdf.PageTextWithPositions
import com.asuka.pocketpdf.domain.model.SearchResult

/**
 * 搜索 UI 状态，也缓存全页文字坐标供长按选中。
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val currentMatchIndex: Int = 0,
    val totalMatches: Int = 0,
    val isSearching: Boolean = false,
    val error: String? = null,
    /** 全页文字坐标缓存，key = pageIndex，用于长按文字选中（独立于搜索） */
    val pageTextCache: Map<Int, PageTextWithPositions> = emptyMap(),
) {
    /** 是否有可用的 PDFium 精确矩形供高亮 */
    fun rectsAvailable(): Boolean = results.any { it.rects.isNotEmpty() }
}
