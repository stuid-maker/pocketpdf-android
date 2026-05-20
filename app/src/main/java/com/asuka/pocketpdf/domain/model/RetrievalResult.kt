package com.asuka.pocketpdf.domain.model

/**
 * 检索结果：一个文档切片及其与查询的相似度。
 *
 * @param chunk 匹配到的文档切片
 * @param score 余弦相似度，范围 [0, 1]，越接近 1 越相关
 */
data class RetrievalResult(
    val chunk: DocumentChunk,
    val score: Float,
)
