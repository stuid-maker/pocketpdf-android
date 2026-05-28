package com.asuka.pocketpdf.data.local.mapper

import com.asuka.pocketpdf.data.local.entity.ChunkEntity
import com.asuka.pocketpdf.domain.model.DocumentChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DocumentMappers 扩展测试：覆盖 ChunkEntity ↔ DocumentChunk 双向映射。
 */
class DocumentMappersChunkTest {

    @Test
    fun `chunk entity to domain preserves all fields`() {
        val entity = ChunkEntity(
            id = 42L,
            documentId = 10L,
            pageIndex = 3,
            chunkIndex = 1,
            text = "chunk content",
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
        )

        val domain = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals(10L, domain.documentId)
        assertEquals(3, domain.pageIndex)
        assertEquals(1, domain.chunkIndex)
        assertEquals("chunk content", domain.text)
        assertTrue(domain.embedding!!.contentEquals(floatArrayOf(0.1f, 0.2f, 0.3f)))
    }

    @Test
    fun `chunk domain to entity preserves all fields`() {
        val domain = DocumentChunk(
            id = 7L,
            documentId = 5L,
            pageIndex = 0,
            chunkIndex = 2,
            text = "domain text",
            embedding = floatArrayOf(0.5f),
        )

        val entity = domain.toEntity()

        assertEquals(7L, entity.id)
        assertEquals(5L, entity.documentId)
        assertEquals(0, entity.pageIndex)
        assertEquals(2, entity.chunkIndex)
        assertEquals("domain text", entity.text)
        assertTrue(entity.embedding!!.contentEquals(floatArrayOf(0.5f)))
    }

    @Test
    fun `chunk entity to domain round trip is lossless`() {
        val original = ChunkEntity(
            id = 1L,
            documentId = 1L,
            pageIndex = 2,
            chunkIndex = 3,
            text = "round trip content",
            embedding = floatArrayOf(1f, 2f, 3f, 4f),
        )

        val roundTripped = original.toDomain().toEntity()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.documentId, roundTripped.documentId)
        assertEquals(original.pageIndex, roundTripped.pageIndex)
        assertEquals(original.chunkIndex, roundTripped.chunkIndex)
        assertEquals(original.text, roundTripped.text)
        assertTrue(original.embedding!!.contentEquals(roundTripped.embedding!!))
    }

    @Test
    fun `chunk entity with null embedding round-trips correctly`() {
        val original = ChunkEntity(
            id = 0L,
            documentId = 1L,
            pageIndex = 0,
            chunkIndex = 0,
            text = "no vector yet",
            embedding = null,
        )

        val domain = original.toDomain()
        assertNull(domain.embedding)

        val entity = domain.toEntity()
        assertNull(entity.embedding)
    }

    @Test
    fun `chunk entity to domain preserves empty embedding`() {
        val entity = ChunkEntity(
            id = 1L, documentId = 1L, pageIndex = 0, chunkIndex = 0,
            text = "empty vec", embedding = floatArrayOf(),
        )

        val domain = entity.toDomain()
        assertTrue(domain.embedding!!.isEmpty())
    }
}
