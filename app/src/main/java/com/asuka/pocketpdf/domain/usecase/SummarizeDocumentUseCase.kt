package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.prompt.PromptTemplates
import com.asuka.pocketpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

/**
 * 基于 MapReduce 策略的文档摘要生成。
 *
 * **Map 阶段**（同步收集，不流式）：
 * 1. 用 [RetrieveChunksUseCase] 检索与文档范围最相关的 Top-K chunk
 * 2. 对每个 chunk 调 LLM 生成简短小结（2-3 句话）
 * 3. 单个 chunk 失败 → 跳过，继续处理其余
 *
 * **Reduce 阶段**（流式输出）：
 * 1. 将所有小结拼成合并 prompt
 * 2. 调 LLM 生成全文总结，以 [Flow] 流式 emit token
 */
class SummarizeDocumentUseCase @Inject constructor(
    private val retrieveChunks: RetrieveChunksUseCase,
    private val llmRepository: LlmRepository,
) {

    /**
     * @param documentId 目标文档 ID
     * @param model LLM 模型名
     * @param scope 摘要范围（全文 / 单页）
     * @param topK Map 阶段检索的 chunk 数量，默认 5
     * @return Reduce 阶段的流式 token，每个 emit 是一个文本片段
     */
    operator fun invoke(
        documentId: Long,
        model: String,
        scope: SummaryScope,
        topK: Int = 5,
    ): Flow<String> = flow {
        val query = when (scope) {
            is SummaryScope.Full -> "全文核心内容"
            is SummaryScope.Page -> "第 ${scope.pageIndex + 1} 页的内容"
        }

        // 1. 检索
        val results = retrieveChunks(documentId, query, topK)

        // 2. PAGE 模式：额外过滤 pageIndex
        val filtered = when (scope) {
            is SummaryScope.Page -> results.filter { it.chunk.pageIndex == scope.pageIndex }
            is SummaryScope.Full -> results
        }

        if (filtered.isEmpty()) {
            Timber.tag(TAG).d("No chunks found for scope=$scope")
            return@flow
        }

        // 3. Map：逐 chunk 小结（非流式）
        val summaries = mutableListOf<String>()
        for (result in filtered) {
            try {
                val prompt = PromptTemplates.chunkSummary(result.chunk.text)
                val tokens = mutableListOf<String>()
                llmRepository.chatCompletionStream(
                    model = model,
                    messages = listOf(ChatMessage(ChatMessage.ROLE_USER, prompt)),
                ).collect { token -> tokens.add(token) }
                val summary = tokens.joinToString("")
                if (summary.isNotBlank()) {
                    summaries.add(summary)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Map phase: chunk %d failed, skipping", result.chunk.id)
            }
        }

        if (summaries.isEmpty()) {
            Timber.tag(TAG).d("All Map calls failed, no summaries generated")
            return@flow
        }

        // 4. Reduce：合并小结（流式 emit）
        val mergePrompt = PromptTemplates.mergeSummaries(summaries)
        try {
            llmRepository.chatCompletionStream(
                model = model,
                messages = listOf(ChatMessage(ChatMessage.ROLE_USER, mergePrompt)),
            ).collect { token -> emit(token) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Reduce phase failed")
            throw e
        }
    }

    companion object {
        private const val TAG = "SummarizeDocumentUC"
    }
}
