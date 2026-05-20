package com.asuka.pocketpdf.domain.embedding

/**
 * 文本向量化引擎接口。
 * 将文本块转换为 FloatArray 向量。
 */
interface EmbeddingEngine {
    /**
     * 将单段文本转换为向量。
     */
    suspend fun getEmbedding(text: String): FloatArray

    /**
     * 批量将文本列表转换为向量列表。
     */
    suspend fun getEmbeddings(texts: List<String>): List<FloatArray>
}
