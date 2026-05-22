package com.asuka.pocketpdf.core

/**
 * 从 AI 回复中解析文档页码引用。
 *
 * 容错多种格式：
 * - [第N页] / [第 N 页]
 * - [来源: 第N页]
 * - [Page N] / (P.N) / 【来源N】
 *
 * @return 0-based pageIndex 列表，去重排序
 */
object CitationParser {

    private val regexes = listOf(
        Regex("""\[来源[：:]\s*第\s*(\d+)\s*页]"""),       // [来源: 第3页]
        Regex("""\[第\s*(\d+)\s*页]"""),                      // [第3页]
        Regex("""\[Page\s+(\d+)]""", RegexOption.IGNORE_CASE), // [Page 3]
        Regex("""\([Pp]\.\s*(\d+)\)"""),                      // (P.3)
        Regex("""【来源\s*(\d+)】"""),                         // 【来源3】
    )

    /**
     * 解析文本中的所有页码引用。
     *
     * @return 0-based pageIndex 列表（去重、升序），可能为空
     */
    fun parse(text: String): List<Int> {
        val pages = mutableSetOf<Int>()
        for (regex in regexes) {
            regex.findAll(text).forEach { match ->
                val pageStr = match.groupValues[1]
                val pageNum = pageStr.toIntOrNull() ?: return@forEach
                if (pageNum > 0) {
                    pages.add(pageNum - 1) // 1-based → 0-based
                }
            }
        }
        return pages.toList().sorted()
    }

    /**
     * 解析引用并返回匹配区间列表，用于 UI 渲染可点击区域。
     *
     * @return Pair(原始文本中的起止位置, 0-based pageIndex)
     */
    fun parseWithRanges(text: String): List<CitationRange> {
        val results = mutableListOf<CitationRange>()
        for (regex in regexes) {
            regex.findAll(text).forEach { match ->
                val pageStr = match.groupValues[1]
                val pageNum = pageStr.toIntOrNull() ?: return@forEach
                if (pageNum > 0) {
                    results.add(
                        CitationRange(
                            start = match.range.first,
                            end = match.range.last + 1,
                            pageIndex = pageNum - 1,
                            displayText = match.value,
                        )
                    )
                }
            }
        }
        return results.distinctBy { it.start }.sortedBy { it.start }
    }
}

data class CitationRange(
    val start: Int,
    val end: Int,
    val pageIndex: Int,
    val displayText: String,
)
