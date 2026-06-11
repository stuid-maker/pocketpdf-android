package com.asuka.pocketpdf.data.pdf

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.pdf.PageTextWithPositions
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfTextExtractor
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PdfTextExtractor] 的 PDFium 实现。
 *
 * 通过 [PdfDocumentEngine] 打开 PDF 并按页提取文本。
 * 与 [PdfBoxTextExtractor] 的区别：
 * - 底层引擎从 PdfBox-Android 切换到 PDFium（`io.legere.pdfiumandroid`）
 * - [extractPagesTextWithPositions] 暂不构建旧模型要求的逐字符 position 列表。
 *   搜索高亮直接使用 [com.asuka.pocketpdf.domain.pdf.PdfDocumentSession.searchPage]
 *   返回的 PDFium 原生命中矩形；旧的长按标注位置仍由兼容提取器提供。
 *
 * 全部文本提取在 `withContext(dispatchers.io)` 中执行，避免阻塞主线程。
 * [PdfDocumentEngine.open] 返回的 [com.asuka.pocketpdf.domain.pdf.PdfDocumentSession]
 * 使用 `use {}` 确保资源正确释放。
 */
@Singleton
class PdfiumTextExtractor @Inject constructor(
    private val documentEngine: PdfDocumentEngine,
    private val dispatchers: DispatcherProvider,
) : PdfTextExtractor {

    override suspend fun extractPagesText(file: File): List<String> = withContext(dispatchers.io) {
        documentEngine.open(file).use { session ->
            (0 until session.pageCount).map { pageIndex ->
                session.extractText(pageIndex).text
            }
        }
    }

    override suspend fun extractPagesTextWithPositions(file: File): List<PageTextWithPositions> =
        withContext(dispatchers.io) {
            documentEngine.open(file).use { session ->
                (0 until session.pageCount).map { pageIndex ->
                    val text = session.extractText(pageIndex)
                    val pageInfo = session.pageInfo(pageIndex)
                    PageTextWithPositions(
                        pageIndex = pageIndex,
                        fullText = text.text,
                        positions = emptyList(),
                        pdfPageWidth = pageInfo.widthPoints,
                        pdfPageHeight = pageInfo.heightPoints,
                    )
                }
            }
        }
}
