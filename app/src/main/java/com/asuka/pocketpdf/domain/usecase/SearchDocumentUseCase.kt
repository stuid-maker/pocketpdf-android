package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.data.pdf.PdfTextExtractor
import com.asuka.pocketpdf.domain.model.SearchResult
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 全文搜索用例：在指定文档中搜索关键词，返回匹配结果列表。
 *
 * 搜索策略：
 * - 大小写不敏感（lowerQuery vs lowerText.indexOf）
 * - 对每页的 fullText 做全字符串匹配
 * - 匹配到的字符坐标通过 PdfTextPosition 返回，供 UI 层高亮
 */
class SearchDocumentUseCase @Inject constructor(
    private val textExtractor: PdfTextExtractor,
    private val documentRepository: DocumentRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
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
                val pages = textExtractor.extractPagesTextWithPositions(file)
                val results = mutableListOf<SearchResult>()
                val lowerQuery = query.lowercase()
                for (page in pages) {
                    val lowerText = page.fullText.lowercase()
                    var startIndex = 0
                    while (true) {
                        val index = lowerText.indexOf(lowerQuery, startIndex)
                        if (index < 0) break
                        val endIndex = index + query.length
                        val matchText = page.fullText.substring(index, endIndex)
                        // Collect positions that overlap with the match range
                        val matchedPositions = page.positions.filter { pos ->
                            val posLower = pos.text.lowercase()
                            // Check if this position's text falls within the match range
                            // by searching from the match start
                            val posIndex = lowerText.indexOf(posLower, index)
                            posIndex >= 0 && posIndex < endIndex
                        }
                        results.add(
                            SearchResult(
                                pageIndex = page.pageIndex,
                                matchText = matchText,
                                matchIndex = index,
                                positions = matchedPositions,
                                pdfPageWidth = page.pdfPageWidth,
                                pdfPageHeight = page.pdfPageHeight,
                            ),
                        )
                        startIndex = index + 1
                    }
                }
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
