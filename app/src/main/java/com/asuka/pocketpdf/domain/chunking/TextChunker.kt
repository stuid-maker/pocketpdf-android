package com.asuka.pocketpdf.domain.chunking

import com.asuka.pocketpdf.domain.model.DocumentChunk

/**
 * 文本切块算法接口。
 *
 * 将 PDF 解析出的原始页面文本列表转换为适合向量化索引的切片 (Chunks)。
 */
interface TextChunker {
    /**
     * @param documentId 所属文档 ID
     * @param pages 页面内容列表（Index 为页码）
     * @return 切片后的 DocumentChunk 列表
     */
    fun chunk(documentId: Long, pages: List<String>): List<DocumentChunk>
}
