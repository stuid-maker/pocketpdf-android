package com.asuka.pocketpdf.domain.embedding

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EmbeddingEngine 接口契约测试。
 */
class EmbeddingEngineContractTest {

    private class FakeEmbeddingEngine : EmbeddingEngine {
        override suspend fun getEmbedding(text: String): FloatArray {
            // Return a fixed-size vector for any input
            return floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        }

        override suspend fun getEmbeddings(texts: List<String>): List<FloatArray> {
            return texts.map { getEmbedding(it) }
        }
    }

    @Test
    fun `getEmbedding returns non-empty array`() = runTest {
        val engine = FakeEmbeddingEngine()
        val vec = engine.getEmbedding("hello")
        assertTrue(vec.isNotEmpty())
    }

    @Test
    fun `getEmbedding returns consistent dimensions`() = runTest {
        val engine = FakeEmbeddingEngine()
        val vec1 = engine.getEmbedding("hello")
        val vec2 = engine.getEmbedding("world")
        assertEquals(vec1.size, vec2.size)
    }

    @Test
    fun `getEmbeddings returns same number of vectors`() = runTest {
        val engine = FakeEmbeddingEngine()
        val texts = listOf("a", "b", "c")
        val vectors = engine.getEmbeddings(texts)
        assertEquals(3, vectors.size)
    }

    @Test
    fun `getEmbeddings returns same dimension vectors`() = runTest {
        val engine = FakeEmbeddingEngine()
        val vectors = engine.getEmbeddings(listOf("x", "y"))
        assertEquals(vectors[0].size, vectors[1].size)
    }

    @Test
    fun `getEmbeddings handles empty list`() = runTest {
        val engine = FakeEmbeddingEngine()
        val vectors = engine.getEmbeddings(emptyList())
        assertTrue(vectors.isEmpty())
    }

    @Test
    fun `getEmbedding handles empty string`() = runTest {
        val engine = FakeEmbeddingEngine()
        val vec = engine.getEmbedding("")
        assertTrue(vec.isNotEmpty())
    }
}
