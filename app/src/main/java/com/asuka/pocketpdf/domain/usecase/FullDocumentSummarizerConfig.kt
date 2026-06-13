package com.asuka.pocketpdf.domain.usecase

/**
 * [FullDocumentSummarizer] 的可注入配置。
 *
 * 取代此前的 `internal var` + `testInstance()` 测试钩子（Code Review C-6）：
 * 生产通过 Hilt `@Provides` 注入默认值，测试直接用构造参数传入自定义值。
 */
data class FullDocumentSummarizerConfig(
    /** provider-independent 保守字符预算 */
    val batchCharBudget: Int = FullDocumentSummarizer.DEFAULT_BATCH_CHAR_BUDGET,
    /** Map 阶段并发数 */
    val mapConcurrency: Int = FullDocumentSummarizer.DEFAULT_MAP_CONCURRENCY,
    /** 单次 LLM 调用超时（毫秒） */
    val perCallTimeoutMillis: Long = FullDocumentSummarizer.PER_CALL_TIMEOUT_SECONDS * 1000L,
    /** 整个 Map-Reduce 流程最大耗时（毫秒） */
    val overallTimeoutMillis: Long = FullDocumentSummarizer.OVERALL_TIMEOUT_SECONDS * 1000L,
)
