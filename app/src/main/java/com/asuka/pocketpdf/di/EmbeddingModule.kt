package com.asuka.pocketpdf.di

import com.asuka.pocketpdf.data.embedding.MediaPipeEmbeddingEngine
import com.asuka.pocketpdf.domain.embedding.EmbeddingEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbeddingModule {

    @Binds
    @Singleton
    abstract fun bindEmbeddingEngine(impl: MediaPipeEmbeddingEngine): EmbeddingEngine
}
