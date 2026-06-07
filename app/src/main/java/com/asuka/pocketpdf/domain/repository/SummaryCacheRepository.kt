package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.domain.model.SummaryScope
import kotlinx.coroutines.flow.Flow

/**
 * 文档摘要缓存契约。
 *
 * Domain 只描述缓存行为，不关心 DataStore、Room 或其他持久化实现。
 */
interface SummaryCacheRepository {
    fun get(documentId: Long, scope: SummaryScope): Flow<String?>
    suspend fun set(documentId: Long, scope: SummaryScope, text: String)
    suspend fun invalidate(documentId: Long)
    suspend fun invalidateAll()
}
