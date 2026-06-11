package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import com.asuka.pocketpdf.domain.prompt.PromptTemplates
import com.asuka.pocketpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class AskDocumentUseCase @Inject constructor(
    private val retrieveChunks: RetrieveChunksUseCase,
    private val llmRepository: LlmRepository,
    private val queryIntentRouter: QueryIntentRouter,
    private val fullDocumentSummarizer: FullDocumentSummarizer,
) {

    operator fun invoke(
        documentId: Long,
        question: String,
        model: String,
        topK: Int = 5,
        systemPrompt: String = "",
        history: List<StoredChatMessage> = emptyList(),
        onProgress: (FullDocumentProgress) -> Unit = {},
    ): Flow<String> = flow {
        // 1. Route intent
        val intent = queryIntentRouter.route(question, model)
        Timber.tag(TAG).d("Intent for '%s': %s", question, intent)

        when (intent) {
            QueryIntent.FULL_DOCUMENT -> {
                fullDocumentSummarizer.summarize(
                    documentId = documentId,
                    model = model,
                    systemPrompt = systemPrompt,
                    question = question,
                    onProgress = onProgress,
                ).collect { token -> emit(token) }
            }
            QueryIntent.TOP_K -> {
                val results = retrieveChunks(documentId, question, topK)
                if (results.isEmpty()) {
                    Timber.tag(TAG).d("No chunks retrieved for question: %s", question)
                    emit("未在文档中找到与「$question」相关的内容。")
                    return@flow
                }

                Timber.tag(TAG).d("Retrieved %d chunks for Q&A", results.size)

                val context = buildString {
                    results.forEach { result ->
                        val page = result.chunk.pageIndex + 1
                        appendLine("--- [第${page}页] 相似度=${
                            "%.2f".format(result.score)
                        } ---")
                        appendLine(result.chunk.text)
                        appendLine()
                    }
                }

                val prompt = PromptTemplates.ragQuery(context, question)

                val messages = buildList {
                    // system prompt first
                    if (systemPrompt.isNotBlank()) {
                        add(ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt))
                    }
                    // include conversation history (last N rounds, length-trimmed)
                    val trimmedHistory = trimHistory(history)
                    trimmedHistory.forEach { stored ->
                        add(ChatMessage(stored.role, stored.content))
                    }
                    // current user question
                    add(ChatMessage(ChatMessage.ROLE_USER, prompt))
                }

                try {
                    llmRepository.chatCompletionStream(
                        model = model,
                        messages = messages,
                    ).collect { token -> emit(token) }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Q&A stream failed")
                    throw e
                }
            }
        }
    }

    /**
     * Trim conversation history to fit within reasonable context budget.
     * Keeps last N messages, discarding older ones to prevent token overflow.
     * Each message capped at MAX_MESSAGE_CHARS to avoid single long messages dominating.
     */
    private fun trimHistory(history: List<StoredChatMessage>): List<StoredChatMessage> {
        if (history.isEmpty()) return emptyList()
        // Keep last MAX_HISTORY_MESSAGES, truncate long content
        return history.takeLast(MAX_HISTORY_MESSAGES).map { msg ->
            if (msg.content.length <= MAX_MESSAGE_CHARS) msg
            else msg.copy(content = msg.content.take(MAX_MESSAGE_CHARS) + "…")
        }
    }

    companion object {
        private const val TAG = "AskDocumentUC"
        /** Maximum history messages to include in context (user + assistant pairs) */
        const val MAX_HISTORY_MESSAGES = 8
        /** Max chars per historical message before truncation */
        const val MAX_MESSAGE_CHARS = 2000
    }
}
