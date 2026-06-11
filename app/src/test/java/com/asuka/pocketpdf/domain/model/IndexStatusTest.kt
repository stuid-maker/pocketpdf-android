package com.asuka.pocketpdf.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * IndexStatus 枚举测试：验证所有枚举值。
 */
class IndexStatusTest {

    @Test
    fun `has all index lifecycle values`() {
        assertEquals(
            listOf(
                IndexStatus.NOT_INDEXED,
                IndexStatus.INDEXING,
                IndexStatus.INDEXED,
                IndexStatus.FAILED,
                IndexStatus.NEEDS_OCR,
            ),
            IndexStatus.entries,
        )
    }

    @Test
    fun `NOT_INDEXED is the first entry`() {
        assertEquals(IndexStatus.NOT_INDEXED, IndexStatus.entries[0])
    }

    @Test
    fun `INDEXING is the second entry`() {
        assertEquals(IndexStatus.INDEXING, IndexStatus.entries[1])
    }

    @Test
    fun `INDEXED is the third entry`() {
        assertEquals(IndexStatus.INDEXED, IndexStatus.entries[2])
    }

    @Test
    fun `FAILED is the fourth entry`() {
        assertEquals(IndexStatus.FAILED, IndexStatus.entries[3])
    }

    @Test
    fun `name returns uppercase string`() {
        assertEquals("NOT_INDEXED", IndexStatus.NOT_INDEXED.name)
        assertEquals("INDEXING", IndexStatus.INDEXING.name)
        assertEquals("INDEXED", IndexStatus.INDEXED.name)
        assertEquals("FAILED", IndexStatus.FAILED.name)
        assertEquals("NEEDS_OCR", IndexStatus.NEEDS_OCR.name)
    }

    @Test
    fun `valueOf round-trips correctly`() {
        IndexStatus.entries.forEach { status ->
            assertEquals(status, IndexStatus.valueOf(status.name))
        }
    }

    @Test
    fun `ordinals are sequential`() {
        assertEquals(0, IndexStatus.NOT_INDEXED.ordinal)
        assertEquals(1, IndexStatus.INDEXING.ordinal)
        assertEquals(2, IndexStatus.INDEXED.ordinal)
        assertEquals(3, IndexStatus.FAILED.ordinal)
        assertEquals(4, IndexStatus.NEEDS_OCR.ordinal)
    }
}
