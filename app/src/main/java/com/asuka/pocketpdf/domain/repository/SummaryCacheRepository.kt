package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.domain.model.SummaryScope
import kotlinx.coroutines.flow.Flow

/**
 * 文档摘要缓存契约。
 *
 * 缓存键包含所有影响输出的身份信息：
 * - documentId
 * - SummaryScope
 * - 算法版本（[algorithmVersion]）
 * - 模型名称（[model]）
 * - systemPrompt 的稳定摘要（[systemPrompt]）
 *
 * 不同版本/模型/systemPrompt 的缓存互相隔离，旧版缓存自动失效。
 */
interface SummaryCacheRepository {
    /**
     * 读取缓存的摘要。
     *
     * @param documentId 文档 ID
     * @param scope 摘要范围
     * @param algorithmVersion 摘要算法版本（如 FullDocumentSummarizer.ALGORITHM_VERSION）
     * @param model LLM 模型名
     * @param systemPrompt system prompt 文本（用于区分不同系统提示）
     */
    fun get(
        documentId: Long,
        scope: SummaryScope,
        algorithmVersion: Int,
        model: String,
        systemPrompt: String = "",
    ): Flow<String?>

    /**
     * 写入摘要缓存。
     */
    suspend fun set(
        documentId: Long,
        scope: SummaryScope,
        algorithmVersion: Int,
        model: String,
        systemPrompt: String = "",
        text: String,
    )

    /** 清除指定文档的所有缓存 */
    suspend fun invalidate(documentId: Long)

    /** 清除所有缓存 */
    suspend fun invalidateAll()
}
