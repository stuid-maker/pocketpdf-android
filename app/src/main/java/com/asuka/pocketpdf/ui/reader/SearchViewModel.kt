package com.asuka.pocketpdf.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.domain.usecase.SearchDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全文搜索 ViewModel。
 *
 * 管理搜索关键词、匹配结果列表、当前高亮索引的状态流转。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchDocumentUseCase: SearchDocumentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var documentId: Long = 0

    fun init(documentId: Long) {
        this.documentId = documentId
    }

    fun search(query: String) {
        _uiState.update { it.copy(query = query, isSearching = true, error = null) }
        viewModelScope.launch {
            searchDocumentUseCase(documentId, query).fold(
                onSuccess = { results ->
                    _uiState.update {
                        it.copy(
                            results = results,
                            currentMatchIndex = if (results.isNotEmpty()) 0 else 0,
                            totalMatches = results.size,
                            isSearching = false,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isSearching = false, error = e.message)
                    }
                },
            )
        }
    }

    fun nextMatch() {
        _uiState.update { state ->
            if (state.totalMatches == 0) state
            else state.copy(
                currentMatchIndex = (state.currentMatchIndex + 1) % state.totalMatches,
            )
        }
    }

    fun previousMatch() {
        _uiState.update { state ->
            if (state.totalMatches == 0) state
            else state.copy(
                currentMatchIndex =
                    (state.currentMatchIndex - 1 + state.totalMatches) % state.totalMatches,
            )
        }
    }

    fun clear() {
        _uiState.update { SearchUiState() }
    }
}
