package com.asuka.pocketpdf.domain.chunking

import com.asuka.pocketpdf.domain.model.DocumentChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TextChunker 接口契约测试。
 */
class TextChunkerContractTest {

    private class FakeTextChunker : TextChunker {
        override fun chunk(documentId: Long, pages: List<String>): List<DocumentChunk> {
            return pages.flatMapIndexed { pageIndex, text ->
                if (text.isBlank()) emptyList()
                else listOf(
                    DocumentChunk(
                        documentId = documentId,
                        pageIndex = pageIndex,
                        chunkIndex = 0,
                        text = text,
                    ),
                )
            }
        }
    }

    @Test
    fun `chunk splits pages into chunks`() {
        val chunker = FakeTextChunker()
        val pages = listOf("Page 1 content", "Page 2 content", "Page 3 content")
        val chunks = chunker.chunk(documentId = 1L, pages = pages)
        assertEquals(3, chunks.size)
    }

    @Test
    fun `chunk assigns correct documentId`() {
        val chunker = FakeTextChunker()
        val chunks = chunker.chunk(documentId = 42L, pages = listOf("text"))
        assertEquals(42L, chunks[0].documentId)
    }

    @Test
    fun `chunk assigns correct pageIndex`() {
        val chunker = FakeTextChunker()
        val chunks = chunker.chunk(documentId = 1L, pages = listOf("p0", "p1", "p2"))
        assertEquals(0, chunks[0].pageIndex)
        assertEquals(1, chunks[1].pageIndex)
        assertEquals(2, chunks[2].pageIndex)
    }

    @Test
    fun `chunk handles empty pages list`() {
        val chunker = FakeTextChunker()
        val chunks = chunker.chunk(documentId = 1L, pages = emptyList())
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `chunk handles blank pages`() {
        val chunker = FakeTextChunker()
        val pages = listOf("valid", "", "  ", "also valid")
        val chunks = chunker.chunk(documentId = 1L, pages = pages)
        assertEquals(2, chunks.size)
    }

    @Test
    fun `chunk handles single page`() {
        val chunker = FakeTextChunker()
        val chunks = chunker.chunk(documentId = 1L, pages = listOf("Single page"))
        assertEquals(1, chunks.size)
        assertEquals("Single page", chunks[0].text)
    }
}
