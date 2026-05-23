package com.asuka.pocketpdf.data.chunking

import com.asuka.pocketpdf.domain.chunking.TextChunker
import com.asuka.pocketpdf.domain.model.DocumentChunk
import javax.inject.Inject

/**
 * 按段落边界切分文本（双换行 `\n\n`）。
 *
 * 适合结构清晰的文档（论文、报告），比固定窗口更尊重语义边界。
 * 每个段落生成一个 chunk。如果段落超过 [maxChunkChars]，则在段落内部
 * 使用滑动窗口二次切分，相邻窗口保留 [chunkOverlap] 字符重叠。
 *
 * @param maxChunkChars 每个切块的最大字符长度
 * @param chunkOverlap 相邻切块之间的重叠字符长度
 */
class ParagraphChunker @Inject constructor(
    private val maxChunkChars: Int = 1024,
    private val chunkOverlap: Int = 50
) : TextChunker {

    init {
        require(chunkOverlap < maxChunkChars) { "chunkOverlap must be less than maxChunkChars" }
    }

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
                if (paragraph.length <= maxChunkChars) {
                    // Short paragraph → single chunk
                    chunks.add(
                        DocumentChunk(
                            documentId = documentId,
                            pageIndex = pageIndex,
                            chunkIndex = chunkIndex++,
                            text = paragraph,
                        )
                    )
                } else {
                    // Long paragraph → sliding window within the paragraph
                    var start = 0
                    while (start < paragraph.length) {
                        var end = start + maxChunkChars
                        if (end > paragraph.length) {
                            end = paragraph.length
                        }

                        chunks.add(
                            DocumentChunk(
                                documentId = documentId,
                                pageIndex = pageIndex,
                                chunkIndex = chunkIndex++,
                                text = paragraph.substring(start, end),
                            )
                        )

                        if (end == paragraph.length) break

                        start = end - chunkOverlap

                        // Prevent infinite loop if overlap >= maxChunkChars
                        if (start >= paragraph.length || start >= end) {
                            start = end
                        }
                    }
                }
            }
        }
        return chunks
    }
}
