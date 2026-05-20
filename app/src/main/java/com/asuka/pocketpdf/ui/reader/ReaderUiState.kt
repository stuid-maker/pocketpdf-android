package com.asuka.pocketpdf.ui.reader

import com.asuka.pocketpdf.domain.model.Document

sealed class ReaderUiState {
    data object Loading : ReaderUiState()
    data class Loaded(
        val document: Document,
        val summaryState: SummaryState = SummaryState.Idle,
    ) : Rea