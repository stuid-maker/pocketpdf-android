package com.asuka.pocketpdf.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PromptTemplates 测试：验证所有 prompt 模板产生预期的输出字符串。
 */
class PromptTemplatesTest {

    @Test
    fun `chunkSummary contains the chunk text and instructions`() {
        val result = PromptTemplates.chunkSummary("深度学习是机器学习的一个子集。")
        assertTrue(result.contains("深度学习是机器学习的一个子集。"))
        assertTrue(result.contains("中文"))
        assertTrue(result.contains("2-3 句话"))
    }

    @Test
    fun `chunkSummary wraps text with separators`() {
        val result = PromptTemplates.chunkSummary("核心内容")
        assertTrue(result.startsWith("请用一段中文"))
        assertTrue(result.contains("---"))
    }

    @Test
    fun `mergeSummaries lists all summaries`() {
        val summaries = listOf("片段一小结", "片段二小结", "片段三小结")
        val result = PromptTemplates.mergeSummaries(summaries)
        assertTrue(result.contains("[片段 1]"))
        assertTrue(result.contains("[片段 2]"))
        assertTrue(result.contains("[片段 3]"))
        assertTrue(result.contains("片段一小结"))
        assertTrue(result.contains("片段二小结"))
        assertTrue(result.contains("片段三小结"))
    }

    @Test
    fun `mergeSummaries handles single summary`() {
        val result = PromptTemplates.mergeSummaries(listOf("仅一个片段"))
        assertTrue(result.contains("[片段 1]"))
        assertTrue(result.contains("仅一个片段"))
    }

    @Test
    fun `mergeSummaries handles empty list`() {
        val result = PromptTemplates.mergeSummaries(emptyList())
        assertTrue(result.contains("2-3 段"))
        // 空列表时没有片段标记
        assertEquals(false, result.contains("[片段 1]"))
    }

    @Test
    fun `ragQuery builds prompt with context and question`() {
        val context = "[第1页] 机器学习基础..."
        val question = "什么是机器学习？"
        val result = PromptTemplates.ragQuery(context, question)
        assertTrue(result.contains(context))
        assertTrue(result.contains(question))
        assertTrue(result.contains("[第N页]"))
        assertTrue(result.contains("文档分析助手"))
    }

    @Test
    fun `ragQuery includes citation instruction`() {
        val result = PromptTemplates.ragQuery("ctx", "q?")
        assertTrue(result.contains("请仅根据以下文档内容回答问题"))
        assertTrue(result.contains("如果文档中没有相关信息，请如实说明"))
    }

    @Test
    fun `documentSummary builds full summary prompt`() {
        val chunks = listOf(
            "第 1 页" to "第一页内容...",
            "第 2 页" to "第二页内容...",
        )
        val result = PromptTemplates.documentSummary(chunks)
        assertTrue(result.contains("片段 1"))
        assertTrue(result.contains("片段 2"))
        assertTrue(result.contains("第一页内容..."))
        assertTrue(result.contains("第二页内容..."))
        assertTrue(result.contains("全文总结"))
        assertTrue(result.contains("总结："))
    }

    @Test
    fun `documentSummary handles single chunk`() {
        val chunks = listOf("第 1 页" to "仅此一页")
        val result = PromptTemplates.documentSummary(chunks)
        assertTrue(result.contains("片段 1（第 1 页）"))
        assertTrue(result.contains("仅此一页"))
        assertEquals(false, result.contains("片段 2"))
    }

    @Test
    fun `documentSummary handles many chunks`() {
        val chunks = (0 until 10).map { "第 ${it + 1} 页" to "内容$it" }
        val result = PromptTemplates.documentSummary(chunks)
        (0 until 10).forEach { i ->
            assertTrue("missing chunk $i", result.contains("内容$i"))
        }
    }

    @Test
    fun `documentSummary wraps with separators`() {
        val chunks = listOf("Page 1" to "Text")
        val result = PromptTemplates.documentSummary(chunks)
        assertTrue(result.contains("--- 片段 1（Page 1）---"))
    }
}
