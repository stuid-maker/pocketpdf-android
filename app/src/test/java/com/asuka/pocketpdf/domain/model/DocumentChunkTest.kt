package com.asuka.pocketpdf.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DocumentChunk 数据类测试：重点覆盖 FloatArray 的 equals/hashCode。
 */
class DocumentChunkTest {

    @Test
    fun `equals returns true for identical chunks`() {
        val a = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.1f, 0.2f),
        )
        val b = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.1f, 0.2f),
        )
        assertEquals(a, b)
    }

    @Test
    fun `equals returns false when embedding differs`() {
        val a = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.1f, 0.2f),
        )
        val b = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.3f, 0.4f),
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `equals returns false when embedding is null vs non-null`() {
        val a = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = null,
        )
        val b = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.1f),
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `equals returns true when both embeddings are null`() {
        val a = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = null,
        )
        val b = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = null,
        )
        assertEquals(a, b)
    }

    @Test
    fun `hashCode is consistent for identical chunks`() {
        val a = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.1f, 0.2f),
        )
        val b = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.1f, 0.2f),
        )
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hashCode differs when embedding differs`() {
        val a = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.1f),
        )
        val b = DocumentChunk(
            id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3,
            text = "hello", embedding = floatArrayOf(0.2f),
        )
        assertFalse(a.hashCode() == b.hashCode() || a == b)
    }

    @Test
    fun `equals returns false when id differs`() {
        val a = DocumentChunk(id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3, text = "t")
        val b = DocumentChunk(id = 2L, documentId = 10L, pageIndex = 2, chunkIndex = 3, text = "t")
        assertNotEquals(a, b)
    }

    @Test
    fun `equals returns false when text differs`() {
        val a = DocumentChunk(id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3, text = "abc")
        val b = DocumentChunk(id = 1L, documentId = 10L, pageIndex = 2, chunkIndex = 3, text = "xyz")
        assertNotEquals(a, b)
    }

    @Test
    fun `copy preserves all fields`() {
        val original = DocumentChunk(
            id = 5L, documentId = 1L, pageIndex = 0, chunkIndex = 2,
            text = "original text", embedding = floatArrayOf(1f, 2f),
        )
        val copied = original.copy(text = "modified")
        assertEquals(5L, copied.id)
        assertEquals(1L, copied.documentId)
        assertEquals(0, copied.pageIndex)
        assertEquals(2, copied.chunkIndex)
        assertEquals("modified", copied.text)
        assertTrue(copied.embedding!!.contentEquals(floatArrayOf(1f, 2f)))
    }
}
