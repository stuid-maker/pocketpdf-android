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
    private val fullDocumentSummarizer: FullDocumentSummarizer,
) {

    operator fun invoke(
        documentId: Long,
        model: String,
        scope: SummaryScope,
        topK: Int = 5,
        systemPrompt: String = "",
        onProgress: (FullDocumentProgress) -> Unit = {},
    ): Flow<String> = flow {
        val fullResult = StringBuilder()
        // 1. Check cache first (versioned key)
        val cached = summaryCacheRepository.get(
            documentId = documentId,
            scope = scope,
            algorithmVersion = FullDocumentSummarizer.ALGORITHM_VERSION,
            model = model,
            systemPrompt = systemPrompt,
        ).first()
        if (cached != null) {
            Timber.tag(TAG).d("Cache hit for scope=%s model=%s", scope, model)
            emit(cached)
            return@flow
        }

        Timber.tag(TAG).d("Cache miss for scope=%s, model=%s", scope, model)

        // 2. Process based on scope
        when (scope) {
            is SummaryScope.Full -> {
                // Full scope: delegate to FullDocumentSummarizer (all chunks, map-reduce)
                fullDocumentSummarizer.summarize(
                    documentId = documentId,
                    model = model,
                    systemPrompt = systemPrompt,
                    question = null, // standard summary
                    onProgress = onProgress,
                ).collect { token ->
                    emit(token)
                    fullResult.append(token)
                }
            }
            is SummaryScope.Page -> {
                // Page scope: existing per-page logic with embedding-filtered chunks
                val chunks = documentRepository.getChunksByPage(documentId, scope.pageIndex)
                    .filter { it.embedding != null && it.embedding.isNotEmpty() }

                if (chunks.isEmpty()) {
                    Timber.tag(TAG).d("No chunks for page %d", scope.pageIndex)
                    throw NoChunksForPageException(scope.pageIndex)
                }

                Timber.tag(TAG).d("Summarizing %d chunks for page %d",
                    chunks.size, scope.pageIndex)

                val pairs = chunks.map { "第 ${it.pageIndex + 1} 页" to it.text }
                val prompt = PromptTemplates.documentSummary(pairs)

                val messages = buildList {
                    if (systemPrompt.isNotBlank()) {
                        add(ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt))
                    }
                    add(ChatMessage(ChatMessage.ROLE_USER, prompt))
                }

                try {
                    llmRepository.chatCompletionStream(
                        model = model,
                        messages = messages,
                    ).collect { token ->
                        emit(token)
                        fullResult.append(token)
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "page summary failed")
                    throw e
                }
            }
        }

        // 3. Write to cache (only on success with non-empty result)
        val resultText = fullResult.toString()
        if (resultText.isNotBlank()) {
            summaryCacheRepository.set(
                documentId = documentId,
                scope = scope,
                algorithmVersion = FullDocumentSummarizer.ALGORITHM_VERSION,
                model = model,
                systemPrompt = systemPrompt,
                text = resultText,
            )
            Timber.tag(TAG).d("Cache written for scope=%s", scope)
        }
    }

    companion object {
        private const val TAG = "SummarizeDocumentUC"
    }
}

class NoChunksForPageException(pageIndex: Int) :
    Exception("第 ${pageIndex + 1} 页无文本内容")

class NoChunksException(val documentId: Long) :
    Exception("文档 #$documentId 未索引或无文本内容，请等待索引完成后重试")
