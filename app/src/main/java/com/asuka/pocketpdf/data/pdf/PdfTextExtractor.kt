package com.asuka.pocketpdf.data.pdf

import java.io.File

/**
 * 从已落地到内部存储的 PDF 文件里按页提取文本。
 *
 * 职责严格只做一件事：**按 PDF 自带的页边界把每页的文本剥出来**，不做任何 chunking /
 * splitting / 归一化（这些属于 W2 起的切块算法层，由独立的 ChunkDocumentUseCase 接管）。
 *
 * 抽接口的两个理由：
 * 1. Repository 单测可注入 fake，不必真碰 PdfBox（PdfBox-Android 在 JVM 上需要资源加载器
 *    初始化，单测里走 fake 更轻）
 * 2. 未来若想换其他 PDF 引擎（如 MuPDF / PDFium）只换实现，Repository 零改动
 *
 * 失败语义：损坏 PDF / 加密 PDF / IO 异常一律以**抛异常**形式上抛，由 Repository 用
 * [com.asuka.pocketpdf.core.resultOf] 包成 [com.asuka.pocketpdf.core.Result.Failure]。
 * 不在本接口里返回 nullable / Result——避免双层包装。
 */
interface PdfTextExtractor {

    /**
     * 按页提取文本。
     *
     * @param file 已落到内部存储的 PDF 绝对路径文件
     * @return 每页一个 String，下标 0 = 第一页；空 PDF 返回 emptyList；扫描件（无文本层）
     *   返回 List 中每个元素均为空字符串（**仍有效**，pageCount = list.size）
     * @throws java.io.IOException 文件读取失败
     * @throws RuntimeException PDF 解析失败（损坏 / 加密 / 不支持的版本等）
     */
    suspend fun extractPagesText(file: File): List<String>
}
