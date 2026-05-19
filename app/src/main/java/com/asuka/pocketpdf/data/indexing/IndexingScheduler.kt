package com.asuka.pocketpdf.data.indexing

/**
 * 索引任务调度器接口。
 *
 * 抽象出 WorkManager 依赖以便 ViewModel 单测注入 no-op 实现。
 */
interface IndexingScheduler {
    fun schedule(documentId: Long)
}
