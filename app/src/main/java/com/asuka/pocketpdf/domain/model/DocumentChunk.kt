package com.asuka.pocketpdf.domain.model

/**
 * 表示 PDF 文档的一段文本切片 (Chunk)。
 *
 * @param id 唯一标识（通常由数据库自动生成）
 * @param documentId 归属的文档 ID
 * @param pageIndex 该切片所在或起始的页码（0-based）
 * @param chunkIndex 该切片在整个文档中的序号（0-based）
 * @param text 文本内容
 * @param embedding 向量数据；如果尚未进行向量化，则为 null 或空
 */
data class DocumentChunk(
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val chunkIndex: Int,
    val text: String,
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentChunk

        if (id != other.id) return false
        if (documentId != other.documentId) return false
        if (pageIndex != other.pageIndex) return false
        if (chunkIndex != other.chunkIndex) return false
        if (text != other.text) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + pageIndex
        result = 31 * result + chunkIndex
        result = 31 * result + text.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
