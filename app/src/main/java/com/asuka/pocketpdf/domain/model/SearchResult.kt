package com.asuka.pocketpdf.domain.model

import com.asuka.pocketpdf.data.pdf.PdfTextPosition

/**
 * 全文搜索的单个匹配结果。
 *
 * @param pageIndex 匹配所在页码（0-based）
 * @param matchText 匹配到的子串（保留原始大小写）
 * @param matchIndex 匹配在整页 fullText 中的字符偏移
 * @param positions 匹配区间内的字符坐标列表（用于 Canvas 高亮绘制）
 */
data class SearchResult(
    val pageIndex: Int,
    val matchText: String,
    val matchIndex: Int,
    val positions: List<PdfTextPosition>,
)
