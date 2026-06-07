package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.domain.model.SummaryScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        store.set(1L, SummaryScope.Full, "doc1")
        store.set(2L, SummaryScope.Full, "doc2")

        store.invalidate(1L)

        assertNull(store.get(1L, SummaryScope.Full).first())
        assertEquals("doc2", store.get(2L, SummaryScope.Full).first())
    }

    @Test
    fun `invalidateAll removes summaries for every document`() = runTest {
        store.set(1L, SummaryScope.Full, "doc1")
        store.set(2L, SummaryScope.Page(3), "doc2-page4")

        store.invalidateAll()

        assertNull(store.get(1L, SummaryScope.Full).first())
        assertNull(store.get(2L, SummaryScope.Page(3)).first())
    }
}
