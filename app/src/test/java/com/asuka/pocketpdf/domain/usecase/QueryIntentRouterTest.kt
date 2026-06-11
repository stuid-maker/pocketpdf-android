package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.repository.LlmRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeoutException

class QueryIntentRouterTest {

    private val llmRepository = mockk<LlmRepository>(relaxed = true)
    private val llmClassifier = LlmIntentClassifier(llmRepository)
    private val router = QueryIntentRouter(llmClassifier)

    private val testModel = "test-model"

    // ── Local rules: FULL_DOCUMENT ──────────────────────

    @Test
    fun `explicit global query routes locally to FULL_DOCUMENT`() = runTest {
        val queries = listOf(
            "全文总结",
            "概括整篇文档的主要内容",
            "这篇文章的主要观点是什么",
            "总结全文",
            "分析整体结构",
            "核心思想是什么",
            "summarize the document",
            "请对全文进行分析",
            "整篇文章的核心论点",
        )
        queries.forEach { query ->
            assertEquals(
                "Query '$query' should route to FULL_DOCUMENT",
                QueryIntent.FULL_DOCUMENT,
                router.route(query, testModel)
            )
        }
    }

    // ── Local rules: TOP_K ─────────────────────────────

    @Test
    fun `explicit focused query routes locally to TOP_K`() = runTest {
        val queries = listOf(
            "这份合同的总金额是多少",
            "合同日期是哪一天",
            "甲方是谁",
            "违约金在哪一页",
            "赔偿金额是多少",
            "what is the payment deadline",
            "where is the venue",
            "who is the guarantor",
        )
        queries.forEach { query ->
            assertEquals(
                "Query '$query' should route to TOP_K",
                QueryIntent.TOP_K,
                router.route(query, testModel)
            )
        }
    }

    // ── Global wording wins over focused keywords ──────

    @Test
    fun `global wording wins over focused keywords`() = runTest {
        val queries = listOf(
            "总结全文中所有金额相关结论",
            "分析整篇文档里的日期条款",
            "概括全文并列出所有涉及金额的地方",
            "总结全文，重点分析甲方是谁",
        )
        queries.forEach { query ->
            assertEquals(
                "Query '$query' has global wording — should route to FULL_DOCUMENT",
                QueryIntent.FULL_DOCUMENT,
                router.route(query, testModel)
            )
        }
    }

    // ── Ambiguous queries invoke LLM classifier ────────

    @Test
    fun `ambiguous query invokes LLM classifier and returns FULL_DOCUMENT`() =
        runTest {
            every {
                llmRepository.chatCompletionStream(
                    model = any(), messages = any(), temperature = any(), maxTokens = any()
                )
            } returns flowOf("FULL_DOCUMENT")

            val result = router.route("这个文档讲了什么", testModel)
            assertEquals(QueryIntent.FULL_DOCUMENT, result)
        }

    @Test
    fun `ambiguous query invokes LLM classifier and returns TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flowOf("TOP_K")

        val result = router.route("解释一下里面的概念", testModel)
        assertEquals(QueryIntent.TOP_K, result)
    }

    @Test
    fun `ambiguous query with surrounding text invokes classifier`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flowOf("FULL_DOCUMENT")

        val result = router.route("分析一下", testModel)
        assertEquals(QueryIntent.FULL_DOCUMENT, result)
    }

    // ── Invalid classifier output falls back to TOP_K ──

    @Test
    fun `invalid classifier UNKNOWN falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flowOf("UNKNOWN")
        assertEquals(QueryIntent.TOP_K, router.route("模糊查询", testModel))
    }

    @Test
    fun `invalid classifier empty string falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flowOf("")
        assertEquals(QueryIntent.TOP_K, router.route("模糊查询", testModel))
    }

    @Test
    fun `invalid classifier whitespace falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flowOf("   ")
        assertEquals(QueryIntent.TOP_K, router.route("模糊查询", testModel))
    }

    @Test
    fun `invalid classifier JSON falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flowOf("""{"intent":"full"}""")
        assertEquals(QueryIntent.TOP_K, router.route("模糊查询", testModel))
    }

    @Test
    fun `invalid classifier extra text falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flowOf("我想想...FULL_DOCUMENT")
        assertEquals(QueryIntent.TOP_K, router.route("模糊查询", testModel))
    }

    // ── Classifier failure falls back to TOP_K ─────────

    @Test
    fun `classifier IOException falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flow { throw IOException("network error") }

        val result = router.route("模糊查询", testModel)
        assertEquals(QueryIntent.TOP_K, result)
    }

    @Test
    fun `classifier timeout falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flow { throw TimeoutException("timeout") }

        val result = router.route("模糊查询", testModel)
        assertEquals(QueryIntent.TOP_K, result)
    }

    @Test
    fun `classifier generic exception falls back to TOP_K`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flow { throw RuntimeException("unknown error") }

        val result = router.route("模糊查询", testModel)
        assertEquals(QueryIntent.TOP_K, result)
    }

    @Test
    fun `classifier CancellationException is re-thrown not swallowed`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flow { throw CancellationException("cancelled") }

        try {
            router.route("模糊查询", testModel)
            fail("Expected CancellationException to be re-thrown")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    // ── Local rules take priority, no LLM call ─────────

    @Test
    fun `local FULL_DOCUMENT match does NOT call LLM`() = runTest {
        val result = router.route("全文总结", testModel)
        assertEquals(QueryIntent.FULL_DOCUMENT, result)
    }

    @Test
    fun `local TOP_K match does NOT call LLM`() = runTest {
        val result = router.route("金额是多少", testModel)
        assertEquals(QueryIntent.TOP_K, result)
    }

    // ── SSE multi-token response ────────────────────────

    @Test
    fun `classifier collects multi-token SSE response before parsing`() = runTest {
        // 模型分段返回 "FUL", "L_DO", "CUMENT" — firstOrNull 会误判
        // collect 完整流后应正确拼接为 "FULL_DOCUMENT"
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flow {
            emit("FUL")
            emit("L_DO")
            emit("CUMENT")
        }

        val result = router.route("模糊查询", testModel)
        assertEquals(QueryIntent.FULL_DOCUMENT, result)
    }

    @Test
    fun `classifier collects multi-token TOP_K response`() = runTest {
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any(), maxTokens = any()
            )
        } returns flow {
            emit("TO")
            emit("P_")
            emit("K")
        }

        val result = router.route("模糊查询", testModel)
        assertEquals(QueryIntent.TOP_K, result)
    }
}
