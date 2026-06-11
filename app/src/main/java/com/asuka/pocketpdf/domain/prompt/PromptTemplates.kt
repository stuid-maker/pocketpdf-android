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
     * 查询意图分类 prompt。
     *
     * 要求模型将用户问题分类为 FULL_DOCUMENT（全文分析/总结）或 TOP_K（定点查找）。
     * 使用 0 温度获得确定性输出，要求只返回两个枚举值之一。
     */
    fun intentClassification(question: String): String = buildString {
        appendLine("你是一个查询意图分类器。请判断以下用户问题属于哪一类：")
        appendLine()
        appendLine("- FULL_DOCUMENT: 需要理解文档全文、整体结构、核心观点、论证逻辑、")
        appendLine("  主题分析、概括总结等全局性问题")
        appendLine("- TOP_K: 需要查找具体信息，如日期、金额、人名、地名、条款、定义、")
        appendLine("  数字、特定事实等局部问题")
        appendLine()
        appendLine("重要规则：")
        appendLine("1. 如果问题包含「全文」「整体」「概括」「总结全文」等词 → FULL_DOCUMENT")
        appendLine("2. 如果问题只问具体数据/事实/人物/地点 → TOP_K")
        appendLine("3. 如果问题同时包含全文和局部措辞（如「总结全文中所有金额」） → FULL_DOCUMENT")
        appendLine()
        appendLine("只输出 FULL_DOCUMENT 或 TOP_K，不要输出任何其他文字。")
        appendLine()
        appendLine("--- 用户问题 ---")
        append(question)
        appendLine()
        appendLine("---")
        append("分类结果：")
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

    /**
     * Map 阶段批次摘要 prompt。
     *
     * 要求 LLM 对一批相邻 chunk 生成连贯摘要，保留关键事实和逻辑关系。
     */
    fun batchSummary(
        text: String,
    ): String = buildString {
        appendLine("请用中文总结以下文本的核心内容（100-200字）：")
        appendLine("---")
        append(text)
        appendLine()
        append("---")
    }

    /**
     * 简化版批次摘要 prompt（用于首次失败后的重试）。
     * 去掉角色描述和字数限制，对小模型更友好。
     */
    fun batchSummarySimple(text: String): String = buildString {
        appendLine("总结以下内容：")
        appendLine("---")
        append(text)
        appendLine()
        append("---")
    }

    /**
     * 全文分析 prompt（用于聊天场景的自定义分析）。
     *
     * 将 Map-Reduce 得到的内容作为上下文，回答用户的全局分析问题。
     */
    fun fullDocumentAnalysis(context: String, question: String): String = buildString {
        appendLine("你是文档分析助手。以下是对一份文档的摘要内容。")
        appendLine("请根据该内容回答用户的问题，分析应覆盖文档全局。")
        appendLine()
        appendLine("--- 文档摘要 ---")
        append(context)
        appendLine()
        appendLine("---")
        appendLine()
        append("用户问题：$question")
    }
}
