package com.asuka.pocketpdf.di

import com.asuka.pocketpdf.core.DefaultDispatcherProvider
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.data.indexing.IndexingScheduler
import com.asuka.pocketpdf.data.indexing.WorkManagerIndexingScheduler
import com.asuka.pocketpdf.data.local.AndroidKeystoreApiKeyCipher
import com.asuka.pocketpdf.data.local.ApiKeyCipher
import com.asuka.pocketpdf.data.pdf.PdfiumTextExtractor
import com.asuka.pocketpdf.data.pdf.PdfTextExtractor
import com.asuka.pocketpdf.data.remote.repository.LlmRepositoryImpl
import com.asuka.pocketpdf.data.repository.ChatRepositoryImpl
import com.asuka.pocketpdf.data.repository.DocumentRepositoryImpl
import com.asuka.pocketpdf.data.repository.SummaryCacheStore
import com.asuka.pocketpdf.data.storage.FileStorage
import com.asuka.pocketpdf.data.storage.InternalFileStorage
import com.asuka.pocketpdf.domain.repository.ChatRepository
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * domain 接口 → data 实现 的 Hilt 绑定。
 *
 * 用 `@Binds` 而非 `@Provides` 的原因：
 * 1. 字节码层 `@Binds` 更省（Dagger 不生成 Provider 包装类）
 * 2. 语义清晰：这里只是"接口对应哪个实现"，没有任何构造逻辑
 *
 * 注意：本 Module 是 `abstract class` 而非 `object`，因为 `@Binds` 必须用 abstract fun。
 * 同时还需要 `@Provides` 暴露 [DispatcherProvider]，所以加一个嵌套 object [Providers]。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLlmRepository(impl: LlmRepositoryImpl): LlmRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindFileStorage(impl: InternalFileStorage): FileStorage

    @Binds
    @Singleton
    abstract fun bindPdfTextExtractor(impl: PdfiumTextExtractor): PdfTextExtractor

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindApiKeyCipher(impl: AndroidKeystoreApiKeyCipher): ApiKeyCipher

    @Binds
    @Singleton
    abstract fun bindSummaryCacheRepository(
        impl: SummaryCacheStore,
    ): SummaryCacheRepository

    @Binds @Singleton
    abstract fun bindIndexingScheduler(impl: WorkManagerIndexingScheduler): IndexingScheduler

    companion object Providers {

        @Provides
        @Singleton
        fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
    }
}
