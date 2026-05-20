package com.asuka.pocketpdf.di

import com.asuka.pocketpdf.data.chunking.SlidingWindowChunker
import com.asuka.pocketpdf.domain.chunking.TextChunker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChunkingModule {

    @Binds
    @Singleton
    abstract fun bindTextChunker(impl: SlidingWindowChunker): TextChunker

    companion object {
        @Provides
        @Singleton
        fun provideSlidingWindowChunker(): SlidingWindowChunker {
            // 这里可以配置默认的 chunkSize 和 overlap
            return SlidingWindowChunker(
                chunkSize = 512,
                chunkOverlap = 50
            )
        }
    }
}
