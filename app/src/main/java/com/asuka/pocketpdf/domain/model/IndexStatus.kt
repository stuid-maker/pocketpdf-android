package com.asuka.pocketpdf.domain.model

/**
 * 一篇文档相对于"切块 + 向量化 + 入库"的进度。
 *
 * 设计为 4 态枚举而非 boolean，原因：
 * 1. UI 卡片要在 [INDEXING] 时显示进度条 / [FAILED] 时显示重试按钮，2 态信息量不够
 * 2. 后续 W2 的 IndexWorker 可在状态机里精确判断"该不该重启 / 该不该删 chunks"
 * 3. 跨 W1（仅 NOT_INDEXED 一种取值会被写入）→ W2（全 4 态启用）零字段迁移
 *
 * Room 存为 String（mapper 里走 [name] ↔ [valueOf]），避免序数变更引发的数据错位。
 */
enum class IndexStatus {
    NOT_INDEXED,
    INDEXING,
    INDEXED,
    FAILED,
}
