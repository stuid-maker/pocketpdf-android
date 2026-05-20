package com.asuka.pocketpdf.domain.model

/**
 * 摘要范围：全文 或 指定页码。
 */
sealed class SummaryScope {
    /** 全文摘要 */
    data object Full : SummaryScope()

    /** 单页摘要，[pageIndex] 为 0-based 页码 */
    data class Page(val pageIndex: Int) : SummaryScope()
}
