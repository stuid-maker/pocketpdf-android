package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.prompt.PromptTemplates
import com.asuka.pocketpdf.domain.repository.LlmRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * 本地确定性意图分类器。
 *
 * 使用保守的中英文关键词规则判断查询属于 [QueryIntent.FULL_DOCUMENT] 还是
 * [QueryIntent.TOP_K]。无法确定时返回 `null`，由调用方回退到 LLM 分类器。
 *
 * 优先级：明确的全文措辞优先于局部关键词。例如 "总结全文中所有金额相关结论"
 * 应判定为 FULL_DOCUMENT 而非 TOP_K。
 */
class LocalIntentClassifier {

    /**
     * 尝试本地分类。高置信度时返回对应意图，否则返回 null。
     */
    fun classify(question: String): QueryIntent? {
        val q = question.trim().lowercase()
        if (q.isEmpty()) return null

        val isFullDocument = fullDocumentIndicators.any { q.contains(it) }
        val isTopK = topKIndicators.any { q.contains(it) }

        // 全文措辞优先于局部关键词
        if (isFullDocument) return QueryIntent.FULL_DOCUMENT

        // 仅局部关键词匹配时才返回 TOP_K
        if (isTopK) return QueryIntent.TOP_K

        return null
    }

    companion object {
        /** 全文/全局分析指标 — 匹配任一即判定为 FULL_DOCUMENT */
        private val fullDocumentIndicators = setOf(
            // 中文
            "全文总结", "全文", "概括整篇", "整篇", "整体结构", "整体",
            "主要观点", "核心观点", "核心思想", "核心论点",
            "全文分析", "整体分析", "整篇分析",
            "总结全文", "总结整篇", "概括全文",
            "梳理全文", "梳理整篇",
            // 英文
            "summarize the document", "overall summary",
            "comprehensive summary", "document overview",
            "summarize the whole", "summarise the document",
            "summarise the whole",
        )

        /** 局部/定点查询指标 — 仅在没有全文指标时才生效 */
        private val topKIndicators = setOf(
            // 中文
            "多少", "多少钱", "多少金额", "金额",
            "日期", "哪一天", "什么时候",
            "谁", "甲方", "乙方", "当事人",
            "在哪", "哪里", "哪一页", "第几页", "第几条",
            "条款", "定义", "什么是",
            "地点", "位置",
            // 英文
            "what is", "who is", "where", "when",
            "how much", "how many",
            "which page", "which clause",
            "define", "definition",
        )
    }
}

/**
 * LLM 意图分类器。
 *
 * 当本地规则无法确定时，调用已配置的模型进行轻量二分类。
 * 分类 prompt 要求模型只输出 FULL_DOCUMENT 或 TOP_K。
 * 分类失败、超时或输出非法时回退 TOP_K。
 */
class LlmIntentClassifier @Inject constructor(
    private val llmRepository: LlmRepository,
) {
    suspend fun classify(question: String, model: String): QueryIntent {
        val prompt = PromptTemplates.intentClassification(question)
        val messages = listOf(
            com.asuka.pocketpdf.domain.model.ChatMessage(
                com.asuka.pocketpdf.domain.model.ChatMessage.ROLE_USER,
                prompt
            )
        )

        return try {
            // 收集完整 SSE 流再解析，避免模型分段输出 "FULL" + "_DOCUMENT" 导致误判
            val sb = StringBuilder()
            llmRepository.chatCompletionStream(
                model = model,
                messages = messages,
                temperature = 0f,
                maxTokens = INTENT_MAX_TOKENS,
            ).collect { sb.append(it) }
            val raw = sb.toString().trim()

            parseResponse(raw)
        } catch (e: CancellationException) {
            // 协程取消必须重新抛出，不能吞掉
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "LLM intent classification failed, falling back to TOP_K")
            QueryIntent.TOP_K
        }
    }

    private fun parseResponse(raw: String): QueryIntent {
        val cleaned = raw.trim().uppercase()
        return when {
            cleaned == "FULL_DOCUMENT" -> {
                Timber.tag(TAG).d("Classifier returned FULL_DOCUMENT")
                QueryIntent.FULL_DOCUMENT
            }
            cleaned == "TOP_K" -> {
                Timber.tag(TAG).d("Classifier returned TOP_K")
                QueryIntent.TOP_K
            }
            else -> {
                Timber.tag(TAG).w("Invalid classifier output: '%s', falling back to TOP_K", raw)
                QueryIntent.TOP_K
            }
        }
    }

    companion object {
        private const val TAG = "LlmIntentClassifier"

        /** 意图分类只需要 "FULL_DOCUMENT" 或 "TOP_K" 两个单词，限制 token 节省成本和延迟 */
        private const val INTENT_MAX_TOKENS = 8
    }
}

/**
 * 混合意图路由器。
 *
 * 两阶段路由：
 * 1. 先用 [LocalIntentClassifier] 进行确定性本地分类
 * 2. 本地无法确定时，调用 [LlmIntentClassifier] 进行 LLM 分类
 * 3. LLM 分类失败或非法输出时回退 [QueryIntent.TOP_K]
 */
class QueryIntentRouter @Inject constructor(
    private val llmClassifier: LlmIntentClassifier,
) {
    private val localClassifier = LocalIntentClassifier()

    /**
     * 对用户问题返回 [QueryIntent.FULL_DOCUMENT] 或 [QueryIntent.TOP_K]。
     *
     * 路由逻辑：
     * - 明确的全文/全局措辞 → FULL_DOCUMENT（不调 LLM）
     * - 明确的定点问题 → TOP_K（不调 LLM）
     * - 模糊问题 → LLM 分类
     * - LLM 失败/非法 → TOP_K（安全回退）
     *
     * @param question 用户原始问题
     * @param model 用于 LLM 分类的模型（仅在本地无法确定时使用）
     */
    suspend fun route(question: String, model: String): QueryIntent {
        // Stage 1: local deterministic rules
        localClassifier.classify(question)?.let { intent ->
            Timber.tag(TAG).d("Local rule matched: %s for '%s'", intent, question)
            return intent
        }

        // Stage 2: LLM classifier
        Timber.tag(TAG).d("No local match — invoking LLM classifier for '%s'", question)
        return llmClassifier.classify(question, model)
    }

    /**
     * 同步版本 — 仅使用本地规则，不调 LLM。
     * 用于确定性场景（如 "总结全文" 按钮）。
     */
    fun routeLocal(question: String): QueryIntent {
        return localClassifier.classify(question) ?: QueryIntent.TOP_K
    }

    companion object {
        private const val TAG = "QueryIntentRouter"
    }
}
