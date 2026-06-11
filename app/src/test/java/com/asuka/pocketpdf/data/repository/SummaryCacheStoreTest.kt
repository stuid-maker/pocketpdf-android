package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.domain.model.SummaryScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SummaryCacheStoreTest {

    private lateinit var store: SummaryCacheStore

    private val testAlgoVersion = 2
    private val testModel = "test-model"
    private val testSysPrompt = ""

    @Before
    fun setUp() {
        store = SummaryCacheStore(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = runTest {
        store.invalidateAll()
    }

    @Test
    fun `invalidate removes only summaries for target document`() = runTest {
        store.set(1L, SummaryScope.Full, testAlgoVersion, testModel, testSysPrompt, "doc1")
        store.set(2L, SummaryScope.Full, testAlgoVersion, testModel, testSysPrompt, "doc2")

        store.invalidate(1L)

        assertNull(store.get(1L, SummaryScope.Full, testAlgoVersion, testModel, testSysPrompt).first())
        assertEquals("doc2", store.get(2L, SummaryScope.Full, testAlgoVersion, testModel, testSysPrompt).first())
    }

    @Test
    fun `invalidateAll removes summaries for every document`() = runTest {
        store.set(1L, SummaryScope.Full, testAlgoVersion, testModel, testSysPrompt, "doc1")
        store.set(2L, SummaryScope.Page(3), testAlgoVersion, testModel, testSysPrompt, "doc2-page4")

        store.invalidateAll()

        assertNull(store.get(1L, SummaryScope.Full, testAlgoVersion, testModel, testSysPrompt).first())
        assertNull(store.get(2L, SummaryScope.Page(3), testAlgoVersion, testModel, testSysPrompt).first())
    }

    @Test
    fun `different algorithm versions use different cache keys`() = runTest {
        store.set(1L, SummaryScope.Full, 1, testModel, testSysPrompt, "v1-summary")
        store.set(1L, SummaryScope.Full, 2, testModel, testSysPrompt, "v2-summary")

        assertEquals("v1-summary",
            store.get(1L, SummaryScope.Full, 1, testModel, testSysPrompt).first())
        assertEquals("v2-summary",
            store.get(1L, SummaryScope.Full, 2, testModel, testSysPrompt).first())
    }

    @Test
    fun `different models use different cache keys`() = runTest {
        store.set(1L, SummaryScope.Full, testAlgoVersion, "model-a", testSysPrompt, "summary-a")
        store.set(1L, SummaryScope.Full, testAlgoVersion, "model-b", testSysPrompt, "summary-b")

        assertEquals("summary-a",
            store.get(1L, SummaryScope.Full, testAlgoVersion, "model-a", testSysPrompt).first())
        assertEquals("summary-b",
            store.get(1L, SummaryScope.Full, testAlgoVersion, "model-b", testSysPrompt).first())
    }

    @Test
    fun `different system prompts use different cache keys`() = runTest {
        store.set(1L, SummaryScope.Full, testAlgoVersion, testModel, "你是助手", "summary-sys1")
        store.set(1L, SummaryScope.Full, testAlgoVersion, testModel, "你是专家", "summary-sys2")

        assertEquals("summary-sys1",
            store.get(1L, SummaryScope.Full, testAlgoVersion, testModel, "你是助手").first())
        assertEquals("summary-sys2",
            store.get(1L, SummaryScope.Full, testAlgoVersion, testModel, "你是专家").first())
    }
}
