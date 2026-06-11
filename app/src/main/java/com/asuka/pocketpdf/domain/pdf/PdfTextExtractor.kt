package com.asuka.pocketpdf.domain.pdf

import java.io.File

/**
 * 从已落地到内部存储的 PDF 文件里按页提取文本。
 *
 * 职责严格只做一件事：**按 PDF 自带的页边界把每页的文本剥出来**，不做任何 chunking /
 * splitting / 归一化（这些属于切块算法层，由独立的 ChunkDocumentUseCase 接管）。
 *
 * 位于 domain 层，所有 data 层实现（PdfBoxTextExtractor、PdfiumTextExtractor）依赖此接口。
 *
 * 失败语义：损坏 PDF / 加密 PDF / IO 异常一律以**抛异常**形式上抛。
 */
interface PdfTextExtractor {

    /**
     * 按页提取文本。
     *
     * @param file 已落到内部存储的 PDF 绝对路径文件
     * @return 每页一个 String，下标 0 = 第一页；空 PDF 返回 emptyList
     * @throws java.io.IOException 文件读取失败
     * @throws RuntimeException PDF 解析失败（损坏 / 加密 / 不支持的版本等）
     */
    suspend fun extractPagesText(file: File): List<String>

    /**
     * 按页提取文本与字符坐标。
     *
     * @param file 已落到内部存储的 PDF 绝对路径文件
     * @return 每页一个 [PageTextWithPositions]；空 PDF 返回 emptyList
     * @throws java.io.IOException 文件读取失败
     * @throws RuntimeException PDF 解析失败
     */
    suspend fun extractPagesTextWithPositions(file: File): List<PageTextWithPositions>
}
