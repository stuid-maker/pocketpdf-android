package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.prompt.PromptTemplates
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.LlmRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 全文 Map-Reduce 摘要组件。
 *
 * 供 [SummarizeDocumentUseCase] 和 [AskDocumentUseCase] 共同调用，
 * 对文档所有非空白 chunk 按 chunkIndex 顺序进行分批 Map-Reduce 摘要。
 *
 * 生命周期：
 * 1. 加载所有非空 chunk，按 chunkIndex → pageIndex → id 排序
 * 2. 按保守字符预算分批
 * 3. 小文档（单批）→ 一次 LLM 调用
 * 4. 大文档 → Map 阶段逐批生成内部摘要 → Reduce 阶段递归合并
 * 5. 只流式输出最终答案，中间摘要不对外 emit
 *
 * Map-Reduce 预算：
 * - [DEFAULT_BATCH_CHAR_BUDGET] = 12,000 字符（provider-independent 保守值）
 * - chunk 不跨批拆分；单 chunk 超预算时整 chunk 放入单批
 * - Reduce 合并输入超预算时递归合并
 *
 * 算法版本标识：
 * - [ALGORITHM_VERSION] = 2（v1 = 旧 Top-5 检索摘要）
 */
class FullDocumentSummarizer @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val llmRepository: LlmRepository,
) {
    /**
     * provider-independent 保守字符预算。
     *
     * 定义为内部变量（而非构造参数），避免 Hilt 无法注入 Int。
     * 测试可通过 [FullDocumentSummarizer.testInstance] 设置自定义预算。
     */
    internal var batchCharBudget: Int = DEFAULT_BATCH_CHAR_BUDGET

    /** Map 阶段并发数。默认 2，设为 1 退化为串行。 */
    internal var mapConcurrency: Int = DEFAULT_MAP_CONCURRENCY

    companion object {
        /** 全文摘要算法版本。修改摘要逻辑时必须递增，确保旧缓存自动失效。 */
        const val ALGORITHM_VERSION = 2

        /** provider-independent 保守字符预算 */
        const val DEFAULT_BATCH_CHAR_BUDGET = 12_000

        /** Map 阶段默认并发 */
        const val DEFAULT_MAP_CONCURRENCY = 2

        /** 单次 LLM 调用超时（秒）。模型响应慢于此时长则放弃该批次 */
        const val PER_CALL_TIMEOUT_SECONDS = 120L

        /** 整个 Map-Reduce 流程最大耗时（秒）。超出后抛 OverallTimeoutException */
        const val OVERALL_TIMEOUT_SECONDS = 600L

        private const val TAG = "FullDocSummarizer"

        /**
         * 创建测试实例（可自定预算）。
         *
         * 注意：Hilt 不使用此方法；仅用于单元测试。
         */
        fun testInstance(
            documentRepository: DocumentRepository,
            llmRepository: LlmRepository,
            batchCharBudget: Int = DEFAULT_BATCH_CHAR_BUDGET,
        ): FullDocumentSummarizer {
            return FullDocumentSummarizer(documentRepository, llmRepository).also {
                it.batchCharBudget = batchCharBudget
            }
        }
    }

    /**
     * 执行全文摘要。
     *
     * @param documentId 文档 ID
     * @param model 使用的 LLM 模型
     * @param systemPrompt 用户自定义 system prompt，可为空
     * @param question 自定义分析问题（来自聊天）；null 时使用标准全文总结 prompt
     * @return 只 emit 最终答案 token 的 Flow
     * @throws NoChunksException 文档无有效 chunk
     */
    suspend fun summarize(
        documentId: Long,
        model: String,
        systemPrompt: String = "",
        question: String? = null,
        onProgress: (FullDocumentProgress) -> Unit = {},
    ): Flow<String> = flow {
        // 1. 加载所有 chunk，过滤空白，排序
        val chunks = documentRepository.getChunks(documentId)
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.chunkIndex }, { it.pageIndex }, { it.id }))

        if (chunks.isEmpty()) {
            throw NoChunksException(documentId)
        }

        onProgress(FullDocumentProgress.Preparing)

        Timber.tag(TAG).d("Full-document summarize: %d chunks, budget=%d, model=%s",
            chunks.size, batchCharBudget, model)

        // 2. 按字符预算分批
        val batches = batchChunks(chunks)
        Timber.tag(TAG).d("Batched into %d batch(es)", batches.size)

        // 全程超时兜底，防止数十次调用累计耗时失控
        try {
            withTimeout(OVERALL_TIMEOUT_SECONDS * 1000L) {
                // 3. 处理
                if (batches.size == 1) {
                    summarizeSmallDoc(batches.first(), model, systemPrompt, question, onProgress)
                } else {
                    summarizeLargeDoc(batches, model, systemPrompt, question, onProgress)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw OverallTimeoutException(
                batches = batches.size,
                timeoutSeconds = OVERALL_TIMEOUT_SECONDS,
            )
        }
    }

    /** 小文档（单批）的一次性总结 */
    private suspend fun FlowCollector<String>.summarizeSmallDoc(
        batch: List<DocumentChunk>,
        model: String,
        systemPrompt: String,
        question: String?,
        onProgress: (FullDocumentProgress) -> Unit,
    ) {
        onProgress(FullDocumentProgress.Finalizing)
        val (prompt, messages) = buildFinalPrompt(
            chunks = batch,
            systemPrompt = systemPrompt,
            question = question,
        )
        var hasContent = false
        collectWithTimeout(model, messages, "small doc final").collect { token ->
            if (!hasContent && token.isNotBlank()) hasContent = true
            emit(token)
        }
        if (!hasContent) {
            throw EmptyFinalSummaryException()
        }
        onProgress(FullDocumentProgress.Completed)
    }

    /** 大文档的 Map-Reduce 流水线 */
    private suspend fun FlowCollector<String>.summarizeLargeDoc(
        batches: List<List<DocumentChunk>>,
        model: String,
        systemPrompt: String,
        question: String?,
        onProgress: (FullDocumentProgress) -> Unit,
    ) {
        val allSummaries = mutableListOf<String>()

        // Map 阶段：限流并发逐批生成内部摘要
        val completedCount = AtomicInteger(0)
        val results = arrayOfNulls<String>(batches.size)

        coroutineScope {
            val semaphore = Semaphore(mapConcurrency)
            batches.mapIndexed { i, batch ->
                async {
                    semaphore.withPermit {
                        val batchText = batch.joinToString("\n\n") { it.text }
                        val mapPrompt = PromptTemplates.batchSummary(
                            text = batchText,
                        )
                        val mapMessages = buildMessages(systemPrompt, mapPrompt)
                        var summary = collectFull(
                            collectWithTimeout(model, mapMessages, "batch $i/${batches.size}")
                        )
                        if (summary.isBlank()) {
                            Timber.tag(TAG).w("Batch %d empty, retrying with simple prompt", i + 1)
                            val retryPrompt = PromptTemplates.batchSummarySimple(batchText)
                            val retryMessages = buildMessages(systemPrompt, retryPrompt)
                            summary = collectFull(
                                collectWithTimeout(model, retryMessages, "batch $i retry")
                            )
                        }
                        if (summary.isBlank()) {
                            Timber.tag(TAG).w("Batch %d empty after retry, skipping", i + 1)
                        } else {
                            results[i] = summary
                        }
                        val done = completedCount.incrementAndGet()
                        onProgress(FullDocumentProgress.Mapping(done, batches.size))
                        Timber.tag(TAG).d("Batch %d/%d done (%d chars)%s",
                            i + 1, batches.size, summary.length,
                            if (summary.isBlank()) " [skipped]" else "")
                    }
                }
            }.forEach { it.await() }
        }

        val validSummaries = results.filterNotNull()
        if (validSummaries.isEmpty()) {
            throw AllBatchesEmptyException(batches.size)
        }
        allSummaries.addAll(validSummaries)

        // Reduce
        val finalInput = reduceRecursively(
            summaries = allSummaries,
            model = model,
            systemPrompt = systemPrompt,
            onProgress = onProgress,
            progress = ReduceProgress(),
        )

        // 最终分析
        onProgress(FullDocumentProgress.Finalizing)
        val (_, finalMessages) = buildFinalPrompt(
            inputText = finalInput,
            systemPrompt = systemPrompt,
            question = question,
        )
        var hasContent = false
        collectWithTimeout(model, finalMessages, "final reduce").collect { token ->
            if (!hasContent && token.isNotBlank()) hasContent = true
            emit(token)
        }
        if (!hasContent) {
            throw EmptyFinalSummaryException()
        }
        onProgress(FullDocumentProgress.Completed)
    }

    // ── Internal helpers ─────────────────────────────────

    /** 按字符预算分批 chunk */
    internal fun batchChunks(chunks: List<DocumentChunk>): List<List<DocumentChunk>> {
        val batches = mutableListOf<List<DocumentChunk>>()
        var currentBatch = mutableListOf<DocumentChunk>()
        var currentChars = 0

        for (chunk in chunks) {
            val chunkLen = chunk.text.length
            // 如果当前批非空且加入此 chunk 会超出预算，则开始新批
            if (currentBatch.isNotEmpty() && currentChars + chunkLen > batchCharBudget) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentChars = 0
            }
            currentBatch.add(chunk)
            currentChars += chunkLen
        }
        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }
        return batches
    }

    /** 递归合并摘要列表直到能放进一次请求 */
    private suspend fun reduceRecursively(
        summaries: List<String>,
        model: String,
        systemPrompt: String,
        onProgress: (FullDocumentProgress) -> Unit,
        progress: ReduceProgress,
    ): String {
        if (summaries.isEmpty()) return ""

        val totalChars = summaries.sumOf { it.length }
        if (totalChars <= batchCharBudget) {
            // 可以一次合并
            progress.total++
            onProgress(FullDocumentProgress.Reducing(progress.completed, progress.total))
            val mergePrompt = PromptTemplates.mergeSummaries(summaries)
            val messages = buildMessages(systemPrompt, mergePrompt)
            val merged = collectFull(collectWithTimeout(model, messages, "reduce merge"))
            if (merged.isBlank()) {
                throw EmptyMergeException(summaries.size)
            }
            progress.completed++
            onProgress(FullDocumentProgress.Reducing(progress.completed, progress.total))
            return merged
        }

        // 单摘要超预算：拆分为半预算字符块，确保每两块可在下一轮合批
        if (summaries.size == 1) {
            val text = summaries.first()
            // 用半预算拆分：任两块都能在下一轮放进同一批次，不会触发收敛失败
            val halfBudget = batchCharBudget / 2
            val chunks = text.chunked(halfBudget)
            Timber.tag(TAG).d("Single summary (%d chars) exceeds budget, splitting into %d chunks (half-budget=%d)",
                text.length, chunks.size, halfBudget)
            return reduceRecursively(chunks, model, systemPrompt, onProgress, progress)
        }

        // 超预算：将 summaries 按字符数分批再合并
        val metaBatches = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var currentChars = 0

        for (s in summaries) {
            if (current.isNotEmpty() && currentChars + s.length > batchCharBudget) {
                metaBatches.add(current)
                current = mutableListOf()
                currentChars = 0
            }
            current.add(s)
            currentChars += s.length
        }
        if (current.isNotEmpty()) metaBatches.add(current)

        // 收敛检测：分批后如果批数没有减少，说明无法收敛
        if (metaBatches.size >= summaries.size) {
            throw NoConvergenceException(
                summaryCount = summaries.size,
                batchCount = metaBatches.size,
                totalChars = totalChars,
                budget = batchCharBudget,
            )
        }

        Timber.tag(TAG).d("Recursive reduce: %d summaries → %d meta-batches",
            summaries.size, metaBatches.size)

        // 逐批合并
        progress.total += metaBatches.size
        onProgress(FullDocumentProgress.Reducing(progress.completed, progress.total))
        val mergedSummaries = metaBatches.map { batch ->
            val prompt = PromptTemplates.mergeSummaries(batch)
            val messages = buildMessages(systemPrompt, prompt)
            val merged = collectFull(collectWithTimeout(model, messages, "reduce meta-batch"))
            if (merged.isBlank()) {
                throw EmptyMergeException(batch.size)
            }
            progress.completed++
            onProgress(FullDocumentProgress.Reducing(progress.completed, progress.total))
            merged
        }

        // 递归直到合并完成
        return reduceRecursively(mergedSummaries, model, systemPrompt, onProgress, progress)
    }

    private data class ReduceProgress(
        var completed: Int = 0,
        var total: Int = 0,
    )

    /** 收集 Flow 为完整字符串（内部使用，不对外 emit） */
    private suspend fun collectFull(flow: Flow<String>): String {
        val sb = StringBuilder()
        flow.collect { sb.append(it) }
        return sb.toString()
    }

    /**
     * 包装单次 LLM 调用，附加 per-call 超时。
     * 超时或失败时记录日志，但仍向上抛出让 Map-Reduce 感知。
     */
    private fun collectWithTimeout(
        model: String,
        messages: List<ChatMessage>,
        label: String,
    ): Flow<String> {
        val stream = llmRepository.chatCompletionStream(model, messages)
        return flow {
            try {
                withTimeout(PER_CALL_TIMEOUT_SECONDS * 1000L) {
                    stream.collect { emit(it) }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.tag(TAG).w(e, "LLM call timed out after %ds: %s", PER_CALL_TIMEOUT_SECONDS, label)
                throw e
            }
        }
    }

    /** 构建 chat messages */
    private fun buildMessages(systemPrompt: String, userPrompt: String): List<ChatMessage> {
        return buildList {
            if (systemPrompt.isNotBlank()) {
                add(ChatMessage(ChatMessage.ROLE_SYSTEM, systemPrompt))
            }
            add(ChatMessage(ChatMessage.ROLE_USER, userPrompt))
        }
    }

    /**
     * 构建最终分析 prompt/messages。
     *
     * @param chunks 直接使用的 chunk 列表（单批小文档场景）
     * @param inputText 预处理的文本（Map-Reduce 场景）
     */
    private fun buildFinalPrompt(
        chunks: List<DocumentChunk>? = null,
        inputText: String? = null,
        systemPrompt: String,
        question: String?,
    ): Pair<String, List<ChatMessage>> {
        val prompt = if (question != null) {
            // 聊天场景：用用户自定义问题
            val context = inputText
                ?: chunks?.joinToString("\n\n") {
                    "第 ${it.pageIndex + 1} 页: ${it.text}"
                } ?: ""
            PromptTemplates.fullDocumentAnalysis(context, question)
        } else {
            // 摘要按钮：标准全文总结
            val pairs = if (inputText != null) {
                listOf("文档摘要" to inputText)
            } else {
                chunks?.map { "第 ${it.pageIndex + 1} 页" to it.text } ?: emptyList()
            }
            PromptTemplates.documentSummary(pairs)
        }
        return prompt to buildMessages(systemPrompt, prompt)
    }

}

/**
 * Map 阶段批次摘要结果为空。
 *
 * 不应该继续执行——空摘要意味着这部分文档内容在最终总结中缺失，
 * 会产生不完整的假全文总结。
 */
class EmptyBatchSummaryException(
    batchIndex: Int,
    totalBatches: Int,
    pageRange: String,
) : Exception("批次 $batchIndex/$totalBatches（$pageRange）摘要结果为空，模型可能出错")

/**
 * Reduce 阶段合并结果为空。
 */
class EmptyMergeException(
    batchSize: Int,
) : Exception("合并 $batchSize 个摘要时模型返回空结果")

/**
 * Map-Reduce 递归归并不收敛。
 *
 * 当 meta-batch 数量没有比输入减少时，说明模型在合并后没有缩短摘要，
 * 继续递归只会无限循环。此时应增大 [FullDocumentSummarizer.batchCharBudget]
 * 或检查模型输出是否异常。
 */
class NoConvergenceException(
    summaryCount: Int,
    batchCount: Int,
    totalChars: Int,
    budget: Int,
) : Exception(
    "归并不收敛：$summaryCount 个摘要（共 $totalChars 字符）分批后仍有 $batchCount 批，" +
    "预算 $budget 字符可能过小或模型合并没有缩短文本"
)

/**
 * 最终模型输出为空。
 *
 * 小文档单次调用或 Map-Reduce 最终合并后，LLM 没有返回任何实质性内容。
 * 不应该静默成功——界面可能显示空白的"完成"状态。
 */
class EmptyFinalSummaryException :
    Exception("模型返回了空内容，请重试或切换模型")

/**
 * 所有批次摘要均为空。
 *
 * 本地模型可能对每一批都返回空。全部跳过时报此错，
 * 提示用户切换模型或检查文档是否包含可读文本。
 */
class AllBatchesEmptyException(
    batchCount: Int,
) : Exception("全部 $batchCount 个批次摘要均为空，请检查模型是否正常或文档是否包含有效文本")

/**
 * 全文 Map-Reduce 整体超时。
 *
 * 当数十次 LLM 调用累计耗时超过 [FullDocumentSummarizer.OVERALL_TIMEOUT_SECONDS] 时抛出。
 * 可能原因：模型太慢、网络不稳定、或文档太大导致批次数过多。
 */
class OverallTimeoutException(
    batches: Int,
    timeoutSeconds: Long,
) : Exception(
    "全文摘要超时：${batches} 个批次在 ${timeoutSeconds}s 内未完成，" +
    "请减小文档或切换到更快的模型"
)
