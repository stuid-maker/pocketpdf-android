package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.embedding.EmbeddingEngine
import com.asuka.pocketpdf.domain.model.RetrievalResult
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * 基于余弦相似度的 Top-K 文档切片检索。
 *
 * 流程：
 * 1. 从 [DocumentRepository] 取出目标文档的所有已有 embedding 的 chunk
 * 2. 用 [EmbeddingEngine] 将 query 转为同维度向量
 * 3. 计算每个 chunk 与 query 的余弦相似度
 * 4. 按相似度降序排列，取前 [topK] 个
 */
class RetrieveChunksUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val embeddingEngine: EmbeddingEngine,
) {

    /**
     * 对指定文档执行检索。
     *
     * @param documentId 目标文档 ID
     * @param query 用户查询文本
     * @param topK 返回的最大结果数，默认 5
     * @return 按相似度降序排列的检索结果列表，可能为空
     */
    suspend operator fun invoke(
        documentId: Long,
        query: String,
        topK: Int = DEFAULT_TOP_K,
    ): List<RetrievalResult> {
        if (topK <= 0) return emptyList()

        // 1. 取所有 chunk，过滤掉未向量化的
        val chunks = repository.getChunks(documentId)
            .filter { it.embedding != null && it.embedding.isNotEmpty() }
        if (chunks.isEmpty()) return emptyList()

        // 2. query → 向量
        val queryVec = embeddingEngine.getEmbedding(query)

        // 3. 计算余弦相似度并排序
        return chunks
            .map { chunk ->
                val score = cosineSimilarity(queryVec, chunk.embedding!!)
                RetrievalResult(chunk = chunk, score = score)
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    companion object {
        const val DEFAULT_TOP_K = 5
    }
}

/**
 * 计算两个等长向量的余弦相似度。
 *
 * cos(θ) = dot(a, b) / (|a| × |b|)
 *
 * 防御：
 * - 任一向量模为零 → 返回 0（全零向量无方向，相似度无意义）
 * - NaN 结果 → 返回 0（浮点精度边界兜底）
 * - 维度不一致 → 抛 [IllegalStateException]
 *
 * @return 余弦相似度，范围 [0, 1]（embedding 向量非负时），理论范围 [-1, 1]
 */
internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) {
        throw IllegalStateException(
            "Vector dimension mismatch: query=${a.size}, chunk=${b.size}"
        )
    }

    var dot = 0f
    var normA = 0f
    var normB = 0f

    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    val magnitudeA = kotlin.math.sqrt(normA)
    val magnitudeB = kotlin.math.sqrt(normB)

    if (magnitudeA == 0f || magnitudeB == 0f) return 0f

    val score = dot / (magnitudeA * magnitudeB)
    return if (score.isNaN()) 0f else score
}
