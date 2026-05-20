package com.asuka.pocketpdf.domain.model

/**
 * 文档库里一篇已导入的 PDF 在 domain 层的表示。
 *
 * 字段语义：
 * - [id]：主键，由 Room autoGenerate；导入流程结束前为 0（未持久化）。
 * - [title]：用户在文档库列表里看到的标题，初始取自 SAF DISPLAY_NAME，可后续手动改。
 * - [uri]：**App 内部存储**的绝对路径（`filesDir/documents/<uuid>.pdf`），不存 SAF 原始 URI——
 *   SAF URI 重启后可能失效，复制到内部存储才能保证"重启 App 仍可读"。
 * - [pageCount]：PdfBox 解析得到，0 表示尚未解析（importDocument 真实现前都是 0）。
 * - [indexStatus]：见 [IndexStatus]；W1 阶段只会写入 [IndexStatus.NOT_INDEXED]。
 * - [importedAt]：Unix epoch 毫秒；列表按此倒序展示。
 *
 * 纯 Kotlin data class，不依赖任何 Android / Room 类型——确保能在 JVM 单测里直接构造。
 */
data class Document(
    val id: Long,
    val title: String,
    val uri: String,
    val pageCount: Int,
    val indexStatus: IndexStatus,
    val importedAt: Long,
)
