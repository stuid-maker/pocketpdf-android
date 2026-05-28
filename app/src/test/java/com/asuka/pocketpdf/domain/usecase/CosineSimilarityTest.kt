package com.asuka.pocketpdf.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * cosineSimilarity 辅助函数测试。
 */
class CosineSimilarityTest {

    @Test
    fun `identical vectors have similarity of 1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `opposite vectors have similarity of -1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `orthogonal vectors have similarity of 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `partial similarity is between 0 and 1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0.5f, 0.5f)
        val score = cosineSimilarity(a, b)
        assertTrue("score=$score should be > 0", score > 0f)
        assertTrue("score=$score should be < 1", score < 1f)
    }

    @Test
    fun `zero query vector returns 0`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `zero chunk vector returns 0`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(0f, 0f, 0f)
        assertEquals(0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `both zero vectors return 0`() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(0f, 0f)
        assertEquals(0f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test(expected = IllegalStateException::class)
    fun `dimension mismatch throws`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f)
        cosineSimilarity(a, b)
    }

    @Test
    fun `single element vectors work`() {
        val a = floatArrayOf(3f)
        val b = floatArrayOf(6f)
        assertEquals(1f, cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `specific known cosine value`() {
        val a = floatArrayOf(3f, 4f) // magnitude = 5
        val b = floatArrayOf(6f, 8f) // magnitude = 10, dot = 50
        // cos = 50 / (5 * 10) = 1.0
        assertEquals(1f, cosineSimilarity(a, b), 0.0001f)
    }
}
