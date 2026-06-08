package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.domain.model.SearchResult

/**
 * 搜索 UI 状态。
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val currentMatchIndex: Int = 0,
    val totalMatches: Int = 0,
    val isSearching: Boolean = false,
    val error: String? = null,
)
