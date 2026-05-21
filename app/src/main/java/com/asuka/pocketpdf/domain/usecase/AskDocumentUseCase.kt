package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.prompt.PromptTemplates
import com.asuka.pocketpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * 基于 RAG 的文档问答。
 *
 * 流程：
 * 1. [RetrieveChunksUseCase] 根据问题语义检索 Top-K 最相关 chunk
 * 2. 拼接上下文 + 页码标注 + 问题 → RAG prompt
 * 3. [LlmRepository] 流式生成回答
 *
 * 引用格式要求模型输出 `[第N页]`（见 [PromptTemplates.ragQuery]）。
 */
class AskDocumentUseCase @Inject constructor(
    private val retrieveChunks: RetrieveChunksUseCase,
    private val llmRepository: LlmRepository,
) {

    /**
     * @param documentId 目标文档 ID
     * @param question 用户问题
     * @param model LLM 模型名
     * @param topK 检索 chunk 数量，默认 5
     * @return 流式 token
     */
    operator fun invoke(
        documentId: Long,
        question: String,
        model: String,
        topK: Int = 5,
    ): Flow<String> = flow {
        val results = retrieveChunks(documentId, question, topK)
        if (results.isEmpty()) {
            Timber.tag(TAG).d("No chunks retrieved for question: %s", question)
            // 无上下文也尝试回答（让模型自己说不知道）
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

        try {
            llmRepository.chatCompletionStream(
                model = model,
                messages = listOf(ChatMessage(ChatMessage.ROLE_USER, prompt)),
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
