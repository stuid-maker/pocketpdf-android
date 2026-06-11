package com.asuka.pocketpdf.di

import android.content.Context
import androidx.room.Room
import com.asuka.pocketpdf.data.local.AppDatabase
import com.asuka.pocketpdf.data.local.dao.AnnotationDao
import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.dao.ChunkDao
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room 依赖图。
 *
 * 全部 `@Singleton`：[AppDatabase] 内部已用线程安全连接池，
 * 复用同一实例避免多个连接池 / Cursor 泄漏。DAO 由 Database 实例派生，
 * 自然继承单例。
 *
 * 暂未开启 `fallbackToDestructiveMigration` —— W1 schema 还在 v1，
 * 真正出现破坏性 schema 变更前不引入"静默清表"风险。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME,
    ).build()

    @Provides
    fun provideDocumentDao(database: AppDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao = database.chatMessageDao()

    @Provides fun provideChunkDao(database: AppDatabase): ChunkDao = database.chunkDao()

    @Provides
    fun provideAnnotationDao(database: AppDatabase): AnnotationDao = database.annotationDao()
}
