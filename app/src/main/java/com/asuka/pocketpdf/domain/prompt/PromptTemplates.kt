package com.asuka.pocketpdf.domain.prompt

/**
 * LLM 提示词模板。
 *
 * 集中管理所有 prompt，W4 问答模板也放这里。
 * 所有模板要求中文输出，保持一致性。
 */
object PromptTemplates {

    /**
     * Map 阶段：要求 LLM 对单个文本片段给出简短中文小结。
     */
    fun chunkSummary(chunkText: String): String = buildString {
        appendLine("请用一段中文简洁地总结以下文本片段的核心内容，控制在 2-3 句话：")
        appendLine("---")
        append(chunkText)
        appendLine()
        append("---")
    }

    /**
     * Reduce 阶段：将多个片段小结合并为一段通顺的全文总结。
     */
    fun mergeSummaries(summaries: List<String>): String = buildString {
        appendLine("以下是对一份文档不同片段的摘要列表。请将它们整合为一段通顺的全文总结（2-3 段），按照原文的逻辑顺序组织：")
        appendLine("---")
        summaries.forEachIndexed { index, summary ->
            appendLine("[片段 ${index + 1}] $summary")
        }
        append("---")
    }

    /**
     * RAG 问答 prompt（W4）。
     *
     * @param context 检索到的文档上下文（含页码标记）
     * @param question 用户问题
     */
    fun ragQuery(context: String, question: String): String = buildString {
        appendLine("你是一个文档分析助手。请仅根据以下文档内容回答问题。")
        appendLine("如果文档中没有相关信息，请如实说明。")
        appendLine()
        appendLine("重要：回答中引用文档原文时，请使用 [第N页] 格式标注页码。")
        appendLine()
        appendLine("--- 文档内容 ---")
        append(context)
        appendLine("---")
        appendLine()
        append("问题：$question")
    }

    /**
     * 文档摘要 prompt（W3 简化版）。
     *
     * 将 chunk 列表拼成上下文，要求 LLM 生成中文全文总结。
     *
     * @param chunks 文档片段列表，每项 Pair(pageLabel, text)
     */
    fun documentSummary(chunks: List<Pair<String, String>>): String {
        val context = buildString {
            chunks.forEachIndexed { index, (label, text) ->
                appendLine("--- 片段 ${index + 1}（$label）---")
                appendLine(text)
                appendLine()
            }
        }
        return buildString {
            appendLine("请用中文对以下文档片段进行全文总结（2-3 段），提取核心观点并按原文逻辑组织：")
            appendLine()
            append(context)
            append("---")
            appendLine()
            append("总结：")
        }
    }
}
