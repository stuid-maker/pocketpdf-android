package com.asuka.pocketpdf.di

import com.asuka.pocketpdf.data.pdf.PdfiumDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfModule {

    @Binds
    @Singleton
    abstract fun bindPdfDocumentEngine(impl: PdfiumDocumentEngine): PdfDocumentEngine
}
