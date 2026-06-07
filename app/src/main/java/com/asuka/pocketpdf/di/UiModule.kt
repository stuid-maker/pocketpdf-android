package com.asuka.pocketpdf.di

import com.asuka.pocketpdf.ui.library.AndroidPdfCoverRenderer
import com.asuka.pocketpdf.ui.library.DocumentCoverLoader
import com.asuka.pocketpdf.ui.library.PdfCoverRenderer
import com.asuka.pocketpdf.ui.library.PdfDocumentCoverLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UiModule {

    @Binds
    @Singleton
    abstract fun bindDocumentCoverLoader(
        impl: PdfDocumentCoverLoader,
    ): DocumentCoverLoader

    @Binds
    @Singleton
    abstract fun bindPdfCoverRenderer(
        impl: AndroidPdfCoverRenderer,
    ): PdfCoverRenderer
}
