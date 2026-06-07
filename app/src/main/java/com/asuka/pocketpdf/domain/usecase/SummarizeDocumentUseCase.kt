package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.prompt.PromptTemplates
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class SummarizeDocumentUseCase @Inject constructor(
    private val retrieveChunks: RetrieveChunksUseCase,
    private val documentRepository: DocumentRepository,
    private val llmRepository: LlmRepository,
    private val summaryCacheRepository: SummaryCacheRepository,
) {

    operator fun invoke(
        documentId: Long,
        model: String,
        scope: SummaryScope,
        topK: Int = 5,
        systemPrompt: String = "",
    ): Flow<String> = flow {
        // 1. Check cache first
        val cached = summaryCacheRepository.get(documentId, scope).first()
        if (cached != null) {
            Timber.tag(TAG).d("Cache hit for scope=%s", scope)
            emit(cached)
            return@flow
        }

        Timber.tag(TAG).d("Cache miss for scope=%s, calling LLM", scope)

        // 2. Build chunks and prompt (same as before)
        val chunks = when (scope) {
            is SummaryScope.Full -> {
                val query = "全文核心内容"
                retrieveChunks(documentId, query, topK).map { it.chunk }
            }
            is SummaryScope.Page -> {
                documentRepository.getChunksByPage(documentId, scope.pageIndex)
                    .filter { it.embedding != null && it.embedding.isNotEmpty() }
            }
        }

        if (chunks.isEmpty()) {
            Timber.tag(TAG).d("No chunks for scope=%s", scope)
            throw when (scope) {
                is SummaryScope.Page -> NoChunksForPageException(scope.pageIndex)
                is SummaryScope.Full -> NoChunksException(documentId)
            }
        }

        Timber.tag(TAG).d("Summarizing %d chunks for scope=%s", chunks.size, scope)

        val pairs = chunks.map { "第 ${it.pageIndex + 1} 页" to it.text }
        val prompt = PromptTemplates.documentSummary(pairs)

        val messages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt))
            }
            add(ChatMessage(ChatMessage.ROLE_USER, prompt))
        }

        // 3. Call LLM and accumulate result
        val fullResult = StringBuilder()
        try {
            llmRepository.chatCompletionStream(
                model = model,
                messages = messages,
            ).collect { token ->
                emit(token)
                fullResult.append(token)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "summary failed")
            throw e
        }

        // 4. Write to cache
        summaryCacheRepository.set(documentId, scope, fullResult.toString())
        Timber.tag(TAG).d("Cache written for scope=%s", scope)
    }

    companion object {
        private const val TAG = "SummarizeDocumentUC"
    }
}

class NoChunksForPageException(pageIndex: Int) :
    Exception("第 ${pageIndex + 1} 页无文本内容")

class NoChunksException(val documentId: Long) :
    Exception("文档 #$documentId 未索引或无文本内容，请等待索引完成后重试")
