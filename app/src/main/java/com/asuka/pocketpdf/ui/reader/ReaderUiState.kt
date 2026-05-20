package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.domain.model.Document

sealed class ReaderUiState {
    data object Loading : ReaderUiState()
    data class Loaded(
        val document: Document,
        val summaryState: SummaryState = SummaryState.Idle,
    ) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

sealed class SummaryState {
    data object Idle : SummaryState()
    data object Loading : SummaryState()
    data class Streaming(val tokens: String) : SummaryState()
    data class Done(val fullText: String) : SummaryState()
    data class Error(val message: String) : SummaryState()
}
