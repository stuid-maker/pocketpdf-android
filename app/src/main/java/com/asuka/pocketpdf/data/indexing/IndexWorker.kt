package com.asuka.pocketpdf.data.indexing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asuka.pocketpdf.data.chunking.ParagraphChunker
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.core.Result as OperationResult
import com.asuka.pocketpdf.domain.chunking.TextChunker
import com.asuka.pocketpdf.domain.embedding.EmbeddingEngine
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import com.asuka.pocketpdf.data.pdf.PdfTextExtractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File

@HiltWorker
class IndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val documentRepo: DocumentRepository,
    private val pdfExtractor: PdfTextExtractor,
    private val chunker: TextChunker,
    private val paragraphChunker: ParagraphChunker,
    private val settingsDataStore: SettingsDataStore,
    private val embedEngine: EmbeddingEngine,
    private val summaryCacheRepository: SummaryCacheRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val documentId = inputData.getLong(DOCUMENT_ID_KEY, -1L)
        if (documentId == -1L) {
            Timber.tag(TAG).e("Missing or invalid documentId in inputData")
            return@withContext Result.failure()
        }

        Timber.tag(TAG).i("IndexWorker started for document #%d", documentId)

        try {
            // 0. 选择切块策略
            val strategy = try {
                withTimeout(5000) { settingsDataStore.chunkingStrategy.first() }
            } catch (e: TimeoutCancellationException) {
                Timber.tag(TAG).w("chunking strategy read timed out, using default")
                SettingsDataStore.STRATEGY_SLIDING_WINDOW
            }
            val activeChunker = when (strategy) {
                SettingsDataStore.STRATEGY_PARAGRAPH -> paragraphChunker
                else -> chunker
            }
            Timber.tag(TAG).d("Chunking strategy: %s", strategy)

            // 1. 读取文档并标记 INDEXING
            val doc = documentRepo.getDocument(documentId)
                ?: run {
                    Timber.tag(TAG).e("Document #%d not found", documentId)
                    return@withContext Result.failure()
                }
            documentRepo.updateDocument(doc.copy(indexStatus = IndexStatus.INDEXING))
                .getOrThrow()

            // 2. 提取 PDF 文本
            Timber.tag(TAG).d("Step 2: extracting text from %s", doc.uri)
            val pages = pdfExtractor.extractPagesText(File(doc.uri))

            // 3. 切片
            Timber.tag(TAG).d("Step 3: chunking %d pages", pages.size)
            val chunks = activeChunker.chunk(documentId, pages)
            Timber.tag(TAG).d("Produced %d chunks", chunks.size)

            if (chunks.isEmpty()) {
                summaryCacheRepository.invalidate(documentId)
                documentRepo.replaceChunks(documentId, emptyList())
                    .getOrThrow()
                // 无文本内容的 PDF（如纯扫描件），直接标记 INDEXED
                documentRepo.updateDocument(doc.copy(indexStatus = IndexStatus.INDEXED))
                    .getOrThrow()
                Timber.tag(TAG).i("Document #%d has no extractable text, marked INDEXED", documentId)
                return@withContext Result.success()
            }

            // 4. 批量向量化
            Timber.tag(TAG).d("Step 4: embedding %d chunks", chunks.size)
            val texts = chunks.map { it.text }
            val embeddings = embedEngine.getEmbeddings(texts)
            check(embeddings.size == chunks.size) {
                "Embedding count mismatch: chunks=${chunks.size}, embeddings=${embeddings.size}"
            }

            // 5. 回填 embedding 并落库
            val chunksWithEmbeddings = chunks.zip(embeddings) { chunk, embedding ->
                chunk.copy(embedding = embedding)
            }
            summaryCacheRepository.invalidate(documentId)
            documentRepo.replaceChunks(documentId, chunksWithEmbeddings)
                .getOrThrow()

            // 6. 标记 INDEXED
            documentRepo.updateDocument(doc.copy(indexStatus = IndexStatus.INDEXED))
                .getOrThrow()

            Timber.tag(TAG).i("Document #%d indexed successfully: %d chunks", documentId, chunks.size)
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "IndexWorker failed for document #%d", documentId)
            // 尝试标记 FAILED，如果失败记录日志但不掩盖原始异常
            try {
                val doc = documentRepo.getDocument(documentId)
                if (doc != null) {
                    documentRepo.updateDocument(doc.copy(indexStatus = IndexStatus.FAILED))
                }
            } catch (updateEx: Exception) {
                Timber.tag(TAG).e(updateEx, "Failed to mark document #%d as FAILED", documentId)
            }
            Result.failure()
        }
    }

    companion object {
        const val TAG = "IndexWorker"
        const val DOCUMENT_ID_KEY = "documentId"

        /**
         * 构建 IndexWorker 的输入数据。
         */
        fun buildInputData(documentId: Long): androidx.work.Data = androidx.work.Data.Builder()
            .putLong(DOCUMENT_ID_KEY, documentId)
            .build()
    }
}

private fun OperationResult<Unit>.getOrThrow() {
    if (this is OperationResult.Failure) throw error
}
