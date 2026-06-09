package com.asuka.pocketpdf.data.pdf

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
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
 * - 字符级坐标（positions）暂不支持 — PDFium 的 `textPageGetText` 只返回纯文本，
 *   不提供字符级坐标 API，因此 [extractPagesTextWithPositions] 返回的 positions 为空列表。
 *   标注流程已改用 [com.asuka.pocketpdf.ui.reader.SearchUiState.pageTextCache]，
 *   因此这不影响现有功能。
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
                        positions = emptyList(), // PDFium 不提供字符级坐标
                        pdfPageWidth = pageInfo.widthPoints,
                        pdfPageHeight = pageInfo.heightPoints,
                    )
                }
            }
        }
}
