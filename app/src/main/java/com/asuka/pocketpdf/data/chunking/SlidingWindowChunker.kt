package com.asuka.pocketpdf.data.chunking

import com.asuka.pocketpdf.domain.chunking.TextChunker
import com.asuka.pocketpdf.domain.model.DocumentChunk
import javax.inject.Inject

/**
 * 滑动窗口切块算法实现。
 *
 * 策略：
 * 1. 按页独立处理。
 * 2. 如果页面文本超过 [chunkSize]，则使用滑动窗口进行切分。
 * 3. [chunkOverlap] 用于保留上下文语义。
 *
 * @param chunkSize 每个切块的最大字符长度
 * @param chunkOverlap 相邻切块之间的重叠字符长度
 */
class SlidingWindowChunker @Inject constructor(
    private val chunkSize: Int = 512,
    private val chunkOverlap: Int = 50
) : TextChunker {

    init {
        require(chunkOverlap < chunkSize) { "Overlap must be less than chunkSize" }
    }

    override fun chunk(documentId: Long, pages: List<String>): List<DocumentChunk> {
        val allChunks = mutableListOf<DocumentChunk>()
        var globalChunkIndex = 0

        pages.forEachIndexed { pageIndex, rawText ->
            // Phase 3: 鲁棒性处理 - 清理异常空白符和多余换行
            val cleanText = rawText.trim()
                .replace(Regex("\\s+"), " ") // 将连续空格/换行替换为单个空格
            
            if (cleanText.isEmpty()) return@forEachIndexed

            if (cleanText.length <= chunkSize) {
                // 页面文本较短，直接作为一个 Chunk
                allChunks.add(
                    DocumentChunk(
                        documentId = documentId,
                        pageIndex = pageIndex,
                        chunkIndex = globalChunkIndex++,
                        text = cleanText
                    )
                )
            } else {
                // 滑动窗口切分
                var start = 0
                while (start < cleanText.length) {
                    var end = start + chunkSize
                    if (end > cleanText.length) {
                        end = cleanText.length
                    }

                    val chunkText = cleanText.substring(start, end)
                    allChunks.add(
                        DocumentChunk(
                            documentId = documentId,
                            pageIndex = pageIndex,
                            chunkIndex = globalChunkIndex++,
                            text = chunkText
                        )
                    )

                    if (end == cleanText.length) break
                    
                    // 移动窗口：起始点 = 当前结束点 - 重叠部分
                    start = end - chunkOverlap
                    
                    // 防止死循环：如果 start 没有往前推进，强制推进
                    if (start <= (end - chunkSize)) {
                        start = end // 异常情况兜底
                    }
                }
            }
        }

        return allChunks
    }
}
