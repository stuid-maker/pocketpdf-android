package com.asuka.pocketpdf.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * RetrievalResult 数据类测试。
 */
class RetrievalResultTest {

    private val sampleChunk = DocumentChunk(
        id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 0,
        text = "sample text", embedding = floatArrayOf(1f),
    )

    @Test
    fun `constructs with chunk and score`() {
        val result = RetrievalResult(chunk = sampleChunk, score = 0.85f)
        assertEquals(sampleChunk, result.chunk)
        assertEquals(0.85f, result.score, 0.001f)
    }

    @Test
    fun `data class equality works`() {
        val a = RetrievalResult(chunk = sampleChunk, score = 0.5f)
        val b = RetrievalResult(chunk = sampleChunk, score = 0.5f)
        assertEquals(a, b)
    }

    @Test
    fun `data class equality differs on score`() {
        val a = RetrievalResult(chunk = sampleChunk, score = 0.5f)
        val b = RetrievalResult(chunk = sampleChunk, score = 0.9f)
        assertNotEquals(a, b)
    }

    @Test
    fun `data class equality differs on chunk`() {
        val otherChunk = sampleChunk.copy(id = 2L)
        val a = RetrievalResult(chunk = sampleChunk, score = 0.5f)
        val b = RetrievalResult(chunk = otherChunk, score = 0.5f)
        assertNotEquals(a, b)
    }

    @Test
    fun `score can be zero`() {
        val result = RetrievalResult(chunk = sampleChunk, score = 0f)
        assertEquals(0f, result.score, 0.001f)
    }

    @Test
    fun `score can be one`() {
        val result = RetrievalResult(chunk = sampleChunk, score = 1f)
        assertEquals(1f, result.score, 0.001f)
    }

    @Test
    fun `copy preserves chunk and score`() {
        val original = RetrievalResult(chunk = sampleChunk, score = 0.75f)
        val copied = original.copy(score = 0.8f)
        assertEquals(sampleChunk, copied.chunk)
        assertEquals(0.8f, copied.score, 0.001f)
    }
}
