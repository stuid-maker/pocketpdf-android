package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.prompt.PromptTemplates
import com.asuka.pocketpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class AskDocumentUseCase @Inject constructor(
    private val retrieveChunks: RetrieveChunksUseCase,
    private val llmRepository: LlmRepository,
) {

    operator fun invoke(
        documentId: Long,
        question: String,
        model: String,
        topK: Int = 5,
        systemPrompt: String = "",
    ): Flow<String> = flow {
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
                appendLine("--- [第${page}页] 相似度=${"%.2f".format(result.score)} ---")
                appendLine(result.chunk.text)
                appendLine()
            }
        }

        val prompt = PromptTemplates.ragQuery(context, question)

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
            ).collect { token -> emit(token) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Q&A stream failed")
            throw e
        }
    }

    companion object {
        private const val TAG = "AskDocumentUC"
    }
}
