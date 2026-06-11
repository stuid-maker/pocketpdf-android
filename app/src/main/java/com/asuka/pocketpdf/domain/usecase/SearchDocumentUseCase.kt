package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.model.SearchResult
import com.asuka.pocketpdf.domain.pdf.PageTextWithPositions
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfTextExtractor
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject

/**
 * 全文搜索用例：在指定文档中搜索关键词，返回匹配结果列表。
 *
 * 搜索策略：
 * - 使用 PDFium 引擎 [PdfDocumentEngine] 打开文档 session，逐页调用 searchPage
 * - 大小写匹配由 PDFium 引擎处理
 * - 精确矩形坐标通过 [PdfDocumentEngine] 原生的 [com.asuka.pocketpdf.domain.pdf.PdfSearchMatch.rects] 返回
 * - positions 字段保留但为空列表（PDFium 提供 rects 而非字符坐标，标注流程用 pageTextCache）
 */
open class SearchDocumentUseCase @Inject constructor(
    private val documentEngine: PdfDocumentEngine,
    private val documentRepository: DocumentRepository,
    private val dispatchers: DispatcherProvider,
    private val textExtractor: PdfTextExtractor,
) {
    open suspend operator fun invoke(
        documentId: Long,
        query: String,
    ): Result<List<SearchResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return withContext(dispatchers.io) {
            try {
                val doc = documentRepository.getDocument(documentId)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Document not found: id=$documentId"),
                    )
                val file = File(doc.uri)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("File not found: ${doc.uri}"),
                    )
                }
                val session = documentEngine.open(file)
                try {
                    val results = mutableListOf<SearchResult>()
                    val pageCount = session.pageCount
                    for (pageIndex in 0 until pageCount) {
                        val pageInfo = session.pageInfo(pageIndex)
                        val matches = session.searchPage(pageIndex, query)
                        val canonicalWidth = pageInfo.widthPoints.toInt().coerceAtLeast(1)
                        val canonicalHeight = pageInfo.heightPoints.toInt().coerceAtLeast(1)
                        for (match in matches) {
                            val mappedRects = session.mapPageRectsToDevice(
                                pageIndex = pageIndex,
                                rects = match.rects,
                                widthPx = canonicalWidth,
                                heightPx = canonicalHeight,
                            )
                            results.add(
                                SearchResult(
                                    pageIndex = match.pageIndex,
                                    matchText = match.text,
                                    matchIndex = match.startIndex,
                                    positions = emptyList(),  // PDFium 提供 rects 而非字符坐标
                                    pdfPageWidth = canonicalWidth.toFloat(),
                                    pdfPageHeight = canonicalHeight.toFloat(),
                                    rects = mappedRects,
                                ),
                            )
                        }
                    }
                    Result.success(results)
                } finally {
                    session.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 提取文档所有页面的文字坐标（供长按选中，不依赖搜索） */
    open suspend fun extractPageTextPositions(documentId: Long): Result<List<PageTextWithPositions>> {
        return withContext(dispatchers.io) {
            try {
                val doc = documentRepository.getDocument(documentId)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Document not found: id=$documentId"),
                    )
                val file = File(doc.uri)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("File not found: ${doc.uri}"),
                    )
                }
                Result.success(textExtractor.extractPagesTextWithPositions(file))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
