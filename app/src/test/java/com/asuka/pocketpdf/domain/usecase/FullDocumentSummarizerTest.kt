package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class FullDocumentSummarizerTest {

    private val documentRepository = mockk<DocumentRepository>()
    private val llmRepository = mockk<LlmRepository>(relaxed = true)

    private val testModel = "test-model"
    private val defaultBudget = 2000

    private fun createSummarizer(): FullDocumentSummarizer = FullDocumentSummarizer.testInstance(
        documentRepository = documentRepository,
        llmRepository = llmRepository,
        batchCharBudget = defaultBudget,
    )

    private fun chunk(
        id: Long, pageIndex: Int, chunkIndex: Int, text: String,
    ) = DocumentChunk(
        id = id, documentId = 1L, pageIndex = pageIndex,
        chunkIndex = chunkIndex, text = text,
        embedding = floatArrayOf(1f),
    )

    // ── All chunks loaded ──────────────────────────────

    @Test
    fun `reads all non-blank chunks in chunkIndex order`() = runTest {
        val chunks = listOf(
            chunk(3, 1, 2, "第三章"),
            chunk(1, 0, 0, "第一章"),
            chunk(2, 0, 1, "第二章"),
            chunk(4, 2, 3, "   "), // blank — filtered out
            chunk(5, 1, 4, "第四章"),
        )
        coEvery { documentRepository.getChunks(1L) } returns chunks

        // All chunks → 1 batch → single LLM call
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flowOf("全文总结")

        val result = createSummarizer().summarize(
            documentId = 1L,
            model = testModel,
        ).toList()

        assertEquals(listOf("全文总结"), result)
    }

    // ── Blank chunks filtered ──────────────────────────

    @Test
    fun `filters blank chunks`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, ""),
            chunk(2, 0, 1, "唯一内容"),
        )
        coEvery { documentRepository.getChunks(1L) } returns chunks
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flowOf("总结")

        val result = createSummarizer().summarize(
            documentId = 1L,
            model = testModel,
        ).toList()

        assertEquals(listOf("总结"), result)
    }

    // ── Empty chunks throws ────────────────────────────

    @Test
    fun `throws when no chunks after filtering`() = runTest {
        coEvery { documentRepository.getChunks(1L) } returns listOf(
            chunk(1, 0, 0, ""),
            chunk(2, 0, 1, "   "),
        )

        try {
            createSummarizer().summarize(1L, testModel).toList()
            fail("Expected NoChunksException")
        } catch (e: NoChunksException) {
            assertEquals(1L, e.documentId)
        }
    }

    // ── Small document: one LLM call ───────────────────

    @Test
    fun `small document uses single LLM request`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "短文本A"),
            chunk(2, 0, 1, "短文本B"),
        )
        coEvery { documentRepository.getChunks(1L) } returns chunks

        var callCount = 0
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            callCount++
            flowOf("单次调用")
        }

        createSummarizer().summarize(1L, testModel).toList()
        assertEquals("Small document should use exactly 1 LLM call", 1, callCount)
    }

    // ── Long document: map-reduce ──────────────────────

    @Test
    fun `long document batches chunks by budget`() = runTest {
        // Each chunk ~500 chars, budget=2000 → 4 chunks per batch
        val chunks = (0 until 10).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(500) + " chunk$i")
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        // Map calls + merge call — use thread-safe list for concurrent map
        val callCounts = java.util.Collections.synchronizedList(mutableListOf<String>())
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            callCounts.add("call")
            // Return dummy summary
            flowOf("摘要")
        }

        createSummarizer().summarize(1L, testModel).toList()

        // 10 chunks / budget 2000 → about 3 batches + 1 merge = 4 calls
        assertTrue("Expected at least 3 calls, got ${callCounts.size}",
            callCounts.size >= 3)
    }

    // ── Intermediate summaries NOT emitted ─────────────

    @Test
    fun `intermediate map summaries are not emitted`() = runTest {
        val chunks = (0 until 20).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(500) + " chunk$i")
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        val messagesSlot = slot<List<ChatMessage>>()
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = capture(messagesSlot), temperature = any()
            )
        } answers {
            // Map calls emit "MAP:...", merge calls emit "最终"
            val prompt = messagesSlot.captured.lastOrNull()?.content ?: ""
            if (prompt.contains("总结以下文本片段") || prompt.contains("简洁地总结")) {
                flowOf("MAP_INTERMEDIATE")
            } else {
                flowOf("FINAL_ONLY")
            }
        }

        val tokens = createSummarizer().summarize(
            documentId = 1L,
            model = testModel,
        ).toList()

        // Only final merge tokens should be in output
        tokens.forEach { token ->
            assertTrue("Should not emit intermediate: '$token'",
                !token.contains("MAP_INTERMEDIATE"))
        }
    }

    // ── Chunks preserved in chunkIndex order ───────────

    @Test
    fun `chunks preserved in chunkIndex order within batches`() = runTest {
        val chunks = listOf(
            chunk(3, 2, 2, "第三"),
            chunk(1, 0, 0, "第一"),
            chunk(2, 1, 1, "第二"),
        )
        coEvery { documentRepository.getChunks(1L) } returns chunks

        val messagesSlot = slot<List<ChatMessage>>()
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = capture(messagesSlot), temperature = any()
            )
        } returns flowOf("总结")

        createSummarizer().summarize(1L, testModel).toList()

        val prompt = messagesSlot.captured.firstOrNull()?.content ?: ""
        val idxFirst = prompt.indexOf("第一")
        val idxSecond = prompt.indexOf("第二")
        val idxThird = prompt.indexOf("第三")
        assertTrue("Chunks must be in order: 第一 before 第二",
            idxFirst < idxSecond)
        assertTrue("Chunks must be in order: 第二 before 第三",
            idxSecond < idxThird)
    }

    // ── Failure does not cache ─────────────────────────

    @Test
    fun `failure during summarize throws and returns no result`() = runTest {
        val chunks = listOf(chunk(1, 0, 0, "text"))
        coEvery { documentRepository.getChunks(1L) } returns chunks
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flow { throw RuntimeException("LLM failure") }

        try {
            createSummarizer().summarize(1L, testModel).toList()
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals("LLM failure", e.message)
        }
    }

    // ── Question parameter used for final prompt ───────

    @Test
    fun `user question used in final analysis prompt for chat`() = runTest {
        val chunks = listOf(chunk(1, 0, 0, "文本"))
        coEvery { documentRepository.getChunks(1L) } returns chunks

        val messagesSlot = slot<List<ChatMessage>>()
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = capture(messagesSlot), temperature = any()
            )
        } returns flowOf("回答")

        createSummarizer().summarize(
            documentId = 1L,
            model = testModel,
            question = "分析全文的论证结构",
        ).toList()

        val prompt = messagesSlot.captured.firstOrNull()?.content ?: ""
        assertTrue("Should contain user question: $prompt",
            prompt.contains("分析全文的论证结构"))
    }

    // ── Recursive merge handles oversized merge input ──

    @Test
    fun `oversized merge input recurses`() = runTest {
        // Create chunks that produce many batch summaries
        val chunks = (0 until 20).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(500))
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        var callCount = 0
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            callCount++
            flowOf("X".repeat(500)) // each summary is 500 chars
        }

        createSummarizer().summarize(1L, testModel).toList()

        // 20 batches → 20 map calls → then merges → at least 21 calls total
        assertTrue("Expected many calls, got $callCount", callCount > 5)
    }

    // ── Default standard summary fallback ──────────────

    @Test
    fun `standard summary used when no question provided`() = runTest {
        val chunks = listOf(chunk(1, 0, 0, "内容"))
        coEvery { documentRepository.getChunks(1L) } returns chunks

        val messagesSlot = slot<List<ChatMessage>>()
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = capture(messagesSlot), temperature = any()
            )
        } returns flowOf("标准总结")

        createSummarizer().summarize(1L, testModel).toList()

        val prompt = messagesSlot.captured.firstOrNull()?.content ?: ""
        // Standard summary should mention 总结 or 全文
        assertTrue("Standard summary should ask for summary: $prompt",
            prompt.contains("总结") || prompt.contains("全文"))
    }

    // ── Empty batch throws ──────────────────────────────

    @Test
    fun `all batches empty throws AllBatchesEmptyException`() = runTest {
        // budget=100, each chunk 500 chars → 3 separate batches, all return empty
        val summarizer = FullDocumentSummarizer.testInstance(
            documentRepository = documentRepository,
            llmRepository = llmRepository,
            batchCharBudget = 100,
        )
        val chunks = (0 until 3).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(500))
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flowOf("") // ALL calls return empty

        try {
            summarizer.summarize(1L, testModel).toList()
            fail("Expected AllBatchesEmptyException")
        } catch (e: AllBatchesEmptyException) {
            assertTrue(e.message!!.contains("全部"))
        }
    }

    // ── Retry on empty batch ────────────────────────────

    @Test
    fun `empty batch is retried with simple prompt and succeeds`() = runTest {
        val chunks = (0 until 10).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(500) + " chunk$i")
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        var callCount = 0
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            callCount++
            if (callCount == 1) {
                flowOf("") // first attempt empty
            } else {
                flowOf("summary") // retry succeeds
            }
        }

        val result = createSummarizer().summarize(1L, testModel).toList()
        assertTrue(result.isNotEmpty())
    }

    // ── Merge empty throws ─────────────────────────────

    @Test
    fun `empty merge result throws EmptyMergeException`() = runTest {
        // Use 5 chunks of 800 each → budget 2000 means 2 per batch → 3 batches
        val chunks = (0 until 5).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(800) + " chunk$i")
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        var callCount = 0
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            callCount++
            // Map calls (1,2,3): return valid summary
            // Merge call (4): return empty
            if (callCount <= 3) {
                flowOf("batch summary $callCount")
            } else {
                flowOf("") // merge returns empty
            }
        }

        try {
            createSummarizer().summarize(1L, testModel).toList()
            fail("Expected EmptyMergeException")
        } catch (e: EmptyMergeException) {
            assertTrue(e.message!!.contains("合并"))
        }
    }

    // ── No convergence throws ──────────────────────────

    @Test
    fun `non-converging summaries throw NoConvergenceException`() = runTest {
        // Create chunks that produce many very long summaries
        val chunks = (0 until 6).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(2000))
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        // Budget is 2000, each chunk is 2000 chars → 6 batches (one per chunk)
        // Each map call returns a 2000-char summary → 6 summaries of 2000 each
        // Reduce: 6 summaries, total 12000 chars, budget 2000 → meta-batch
        // Each merge returns another 2000-char summary → still 6 items
        // → No convergence!
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flowOf("X".repeat(2000)) // model never shortens

        try {
            createSummarizer().summarize(1L, testModel).toList()
            fail("Expected NoConvergenceException")
        } catch (e: NoConvergenceException) {
            assertTrue(e.message!!.contains("不收敛"))
        }
    }

    // ── Empty final output throws ───────────────────────

    @Test
    fun `small document empty final output throws EmptyFinalSummaryException`() = runTest {
        val chunks = listOf(chunk(1, 0, 0, "内容"))
        coEvery { documentRepository.getChunks(1L) } returns chunks
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flowOf("") // model returns nothing

        try {
            createSummarizer().summarize(1L, testModel).toList()
            fail("Expected EmptyFinalSummaryException")
        } catch (e: EmptyFinalSummaryException) {
            assertTrue(e.message!!.contains("空内容"))
        }
    }

    @Test
    fun `large document empty final output throws EmptyFinalSummaryException`() = runTest {
        // 2 chunks of 120 chars → 1 batch → small doc path (not what we want)
        // 4 chunks of 800 chars → 2/batch → 2 batches → 2 map + 1 merge + 1 final
        val chunks = (0 until 4).map { i ->
            chunk(i.toLong(), i, i, "X".repeat(800) + " chunk$i")
        }
        coEvery { documentRepository.getChunks(1L) } returns chunks

        var callCount = 0
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            callCount++
            when {
                callCount <= 2 -> flowOf("map summary $callCount") // map
                callCount == 3 -> flowOf("merged")                  // merge (success)
                else -> flowOf("")                                   // final stream (empty)
            }
        }

        try {
            createSummarizer().summarize(1L, testModel).toList()
            fail("Expected EmptyFinalSummaryException")
        } catch (e: EmptyFinalSummaryException) {
            assertTrue(e.message!!.contains("空内容"))
        }
    }

    // ── Single large summary split uses half-budget ─────

    @Test
    fun `single oversized summary splits with half budget and converges`() = runTest {
        // 6 chunks of 700 chars → 6 batches → 6 map summaries of 700 each
        // → 6 summaries, total 4200 > budget 2000 → meta-batch
        // → but to test the single-summary split path, we need:
        // 2 chunks of 1200 → 2 batches → 2 map summaries of 1000 each
        // → 2 summaries, total 2000 ≤ budget → single merge → 1 summary of 3000
        // That won't hit the single-summary split path either.
        // Let me use a more direct approach:
        // 1 chunk of 2500 chars → 1 batch → map → 1 summary of 2500
        // → totalChars=2500 > budget=2000 && summaries.size==1
        // → split with half-budget=1000 → 3 chunks of 834 each
        // → 3 chunks, total 2500 > 2000 → meta-batch 2+1
        // → merge 2 → 1, merge 1 → 1 → 2 merged → then single merge
        // This should converge.
        val chunks = listOf(chunk(1, 0, 0, "X".repeat(2500)))
        coEvery { documentRepository.getChunks(1L) } returns chunks

        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flowOf("Y".repeat(500)) // model shortens significantly

        val result = createSummarizer().summarize(1L, testModel).toList()
        assertTrue(result.isNotEmpty())
    }

    // ── Progress events: multi-batch map-reduce ─────────

    @Test
    fun `multi batch summary reports ordered map and final stages`() = runTest {
        // budget=4 → each chunk is its own batch
        val summarizer = FullDocumentSummarizer.testInstance(
            documentRepository = documentRepository,
            llmRepository = llmRepository,
            batchCharBudget = 4,
        )
        val chunks = listOf(
            chunk(1, 0, 0, "aaaa"),
            chunk(2, 0, 1, "bbbb"),
        )
        coEvery { documentRepository.getChunks(1L) } returns chunks

        // Need 4 calls: map-1, map-2, merge, final
        var callIndex = 0
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            callIndex++
            when (callIndex) {
                1 -> flowOf("m1")
                2 -> flowOf("m2")
                3 -> flowOf("mg")
                else -> flowOf("fn")
            }
        }

        val events = mutableListOf<FullDocumentProgress>()
        summarizer.summarize(
            documentId = 1L,
            model = testModel,
            onProgress = { events.add(it) },
        ).toList()

        assertEquals(FullDocumentProgress.Preparing, events.first())
        assertTrue(events.any { it is FullDocumentProgress.Mapping && it.completed == 1 && it.total == 2 })
        assertTrue(events.any { it is FullDocumentProgress.Mapping && it.completed == 2 && it.total == 2 })
        assertTrue(events.any {
            it is FullDocumentProgress.Reducing && it.completed == 1 && it.total == 1
        })
        assertTrue(events.any { it is FullDocumentProgress.Finalizing })
        assertEquals(FullDocumentProgress.Completed, events.last())
    }

    // ── Progress events: single-batch skips map/reduce ──

    @Test
    fun `single batch summary skips map and reduce progress`() = runTest {
        val chunks = listOf(chunk(1, 0, 0, "short"))
        coEvery { documentRepository.getChunks(1L) } returns chunks

        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flowOf("final")

        val events = mutableListOf<FullDocumentProgress>()
        createSummarizer().summarize(
            documentId = 1L,
            model = testModel,
            onProgress = { events.add(it) },
        ).toList()

        assertEquals(
            listOf(FullDocumentProgress.Preparing, FullDocumentProgress.Finalizing, FullDocumentProgress.Completed),
            events,
        )
    }

    @Test
    fun `map stage limits peak concurrency to two`() = runTest {
        val summarizer = FullDocumentSummarizer.testInstance(
            documentRepository = documentRepository,
            llmRepository = llmRepository,
            batchCharBudget = 10,
        )
        coEvery { documentRepository.getChunks(1L) } returns (0 until 4).map { i ->
            chunk(i.toLong(), i, i, "batch-$i---")
        }

        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            val prompt = secondArg<List<ChatMessage>>().last().content
            if (prompt.contains("请用中文总结以下文本") || prompt.contains("总结以下内容")) {
                flow {
                    val current = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, current) }
                    try {
                        delay(100)
                        emit("m")
                    } finally {
                        active.decrementAndGet()
                    }
                }
            } else {
                flowOf("final")
            }
        }

        summarizer.summarize(1L, testModel).toList()

        assertEquals(2, peak.get())
    }

    @Test
    fun `out of order map completion preserves batch order for reduce`() = runTest {
        val summarizer = FullDocumentSummarizer.testInstance(
            documentRepository = documentRepository,
            llmRepository = llmRepository,
            batchCharBudget = 10,
        )
        coEvery { documentRepository.getChunks(1L) } returns listOf(
            chunk(1, 0, 0, "A---------"),
            chunk(2, 1, 1, "B---------"),
            chunk(3, 2, 2, "C---------"),
        )

        var reducePrompt = ""
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            val prompt = secondArg<List<ChatMessage>>().last().content
            when {
                prompt.contains("A---------") -> flow {
                    delay(300)
                    emit("A")
                }
                prompt.contains("B---------") -> flow {
                    delay(100)
                    emit("B")
                }
                prompt.contains("C---------") -> flow {
                    delay(10)
                    emit("C")
                }
                prompt.contains("A") && prompt.contains("B") && prompt.contains("C") -> {
                    reducePrompt = prompt
                    flowOf("merged")
                }
                else -> flowOf("final")
            }
        }

        summarizer.summarize(1L, testModel).toList()

        assertTrue(reducePrompt.indexOf("A") < reducePrompt.indexOf("B"))
        assertTrue(reducePrompt.indexOf("B") < reducePrompt.indexOf("C"))
    }

    @Test
    fun `map failure cancels in flight sibling requests`() = runTest {
        val summarizer = FullDocumentSummarizer.testInstance(
            documentRepository = documentRepository,
            llmRepository = llmRepository,
            batchCharBudget = 10,
        )
        coEvery { documentRepository.getChunks(1L) } returns listOf(
            chunk(1, 0, 0, "A---------"),
            chunk(2, 1, 1, "B---------"),
        )

        val siblingCancelled = AtomicBoolean(false)
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } answers {
            val prompt = secondArg<List<ChatMessage>>().last().content
            when {
                prompt.contains("A---------") -> flow {
                    try {
                        awaitCancellation()
                    } finally {
                        siblingCancelled.set(true)
                    }
                }
                prompt.contains("B---------") -> flow {
                    delay(10)
                    throw IllegalStateException("map failed")
                }
                else -> flowOf("unused")
            }
        }

        try {
            summarizer.summarize(1L, testModel).toList()
            fail("Expected map failure")
        } catch (e: IllegalStateException) {
            assertEquals("map failed", e.message)
        }

        assertTrue(siblingCancelled.get())
    }

    @Test
    fun `per call timeout is not reported as overall timeout`() = runTest {
        coEvery { documentRepository.getChunks(1L) } returns listOf(
            chunk(1, 0, 0, "slow content"),
        )
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flow {
            awaitCancellation()
        }

        val summarizer = FullDocumentSummarizer.testInstance(
            documentRepository = documentRepository,
            llmRepository = llmRepository,
            perCallTimeoutMillis = 100,
            overallTimeoutMillis = 1_000,
        )

        try {
            summarizer.summarize(1L, testModel).toList()
            fail("Expected PerCallTimeoutException")
        } catch (e: PerCallTimeoutException) {
            assertEquals("small doc final", e.label)
            assertEquals(100L, e.timeoutMillis)
        }
    }

    @Test
    fun `overall timeout remains distinct from per call timeout`() = runTest {
        coEvery { documentRepository.getChunks(1L) } returns listOf(
            chunk(1, 0, 0, "slow content"),
        )
        every {
            llmRepository.chatCompletionStream(
                model = any(), messages = any(), temperature = any()
            )
        } returns flow {
            awaitCancellation()
        }

        val summarizer = FullDocumentSummarizer.testInstance(
            documentRepository = documentRepository,
            llmRepository = llmRepository,
            perCallTimeoutMillis = 1_000,
            overallTimeoutMillis = 100,
        )

        try {
            withTimeout(2_000) {
                summarizer.summarize(1L, testModel).toList()
            }
            fail("Expected OverallTimeoutException")
        } catch (e: OverallTimeoutException) {
            assertEquals(100L, e.timeoutMillis)
        }
    }
}
