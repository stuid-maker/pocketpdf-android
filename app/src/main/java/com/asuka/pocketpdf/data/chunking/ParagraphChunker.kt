package com.asuka.pocketpdf.data.chunking

import com.asuka.pocketpdf.domain.chunking.TextChunker
import com.asuka.pocketpdf.domain.model.DocumentChunk

/**
 * 按段落边界切分文本（双换行 `\n\n`）。
 *
 * 适合结构清晰的文档（论文、报告），比固定窗口更尊重语义边界。
 * 每个段落生成一个 chunk，不限制最大长度。
 */
class ParagraphChunker : TextChunker {

    override fun chunk(documentId: Long, pages: List<String>): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        var chunkIndex = 0

        for ((pageIndex, pageText) in pages.withIndex()) {
            if (pageText.isBlank()) continue

            // Split by double newline (paragraph break)
            val paragraphs = pageText.split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (paragraph in paragraphs) {
                chunks.add(
                    DocumentChunk(
                        documentId = documentId,
                        pageIndex = pageIndex,
                        chunkIndex = chunkIndex++,
                        text = paragraph,
                    )
                )
            }
        }
        return chunks
    }
}
