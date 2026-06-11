package com.asuka.pocketpdf.domain.usecase

sealed interface FullDocumentProgress {
    data object Preparing : FullDocumentProgress
    data class Mapping(val completed: Int, val total: Int) : FullDocumentProgress
    data class Reducing(val completed: Int, val total: Int) : FullDocumentProgress
    data object Finalizing : FullDocumentProgress
    data object Completed : FullDocumentProgress
}
