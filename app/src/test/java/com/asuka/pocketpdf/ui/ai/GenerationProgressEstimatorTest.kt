package com.asuka.pocketpdf.ui.ai

import com.asuka.pocketpdf.domain.usecase.FullDocumentProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationProgressEstimatorTest {

    private val estimator = GenerationProgressEstimator()

    @Test
    fun `eta is unavailable before a completed work unit`() {
        val display = estimator.update(FullDocumentProgress.Mapping(0, 4), nowMs = 1000)
        assertNull(display.remainingSeconds)
        assertEquals("正在总结第 1 / 4 部分", display.stageLabel)
    }

    @Test
    fun `completed units produce rounded approximate eta`() {
        estimator.update(FullDocumentProgress.Preparing, nowMs = 0)
        estimator.update(FullDocumentProgress.Mapping(1, 4), nowMs = 20000)
        val display = estimator.update(FullDocumentProgress.Mapping(2, 4), nowMs = 40000)
        assertEquals(40L, display.remainingSeconds)
        assertEquals("约剩 40秒", display.etaLabel)
    }

    @Test
    fun `discovered reduce work never moves progress backwards`() {
        val before = estimator.update(FullDocumentProgress.Reducing(1, 1), nowMs = 20000)
        val after = estimator.update(FullDocumentProgress.Reducing(1, 3), nowMs = 21000)
        assertTrue(after.fraction != null && before.fraction != null)
        assertTrue(after.fraction!! >= before.fraction!!)
    }

    @Test
    fun `finalizing omits countdown when estimate is not credible`() {
        val display = estimator.update(FullDocumentProgress.Finalizing, nowMs = 5000)
        assertNull(display.remainingSeconds)
        assertEquals("正在撰写最终总结", display.stageLabel)
    }

    @Test
    fun `reduce phase records durations independently from mapping counts`() {
        estimator.update(FullDocumentProgress.Preparing, nowMs = 0)
        estimator.update(FullDocumentProgress.Mapping(1, 3), nowMs = 10_000)
        estimator.update(FullDocumentProgress.Mapping(2, 3), nowMs = 20_000)
        estimator.update(FullDocumentProgress.Mapping(3, 3), nowMs = 30_000)
        estimator.update(FullDocumentProgress.Reducing(0, 2), nowMs = 30_000)

        val display = estimator.update(
            FullDocumentProgress.Reducing(1, 2),
            nowMs = 50_000,
        )

        assertEquals(20L, display.remainingSeconds)
    }
}
