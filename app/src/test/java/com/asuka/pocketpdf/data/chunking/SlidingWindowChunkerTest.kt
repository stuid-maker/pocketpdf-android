package com.asuka.pocketpdf.data.chunking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidingWindowChunkerTest {

    @Test
    fun `small text becomes a single chunk`() {
        val chunker = SlidingWindowChunker(chunkSize = 100, chunkOverlap = 10)
        val pages = listOf("Hello World")
        val result = chunker.chunk(1L, pages)

        assertEquals(1, result.size)
        assertEquals("Hello World", result[0].text)
        assertEquals(0, result[0].pageIndex)
    }

    @Test
    fun `long text is split with overlap`() {
        // Size 10, Overlap 4
        // Chunk 1: 0..10 (length 10)
        // Chunk 2: (10-4).. (6..16)
        val chunker = SlidingWindowChunker(chunkSize = 10, chunkOverlap = 4)
        val pages = listOf("0123456789ABCDEF") 
        val result = chunker.chunk(1L, pages)

        // Expected:
        // C1: "0123456789" (len 10)
        // C2: "6789ABCDEF" (len 10)
        assertEquals(2, result.size)
        assertEquals("0123456789", result[0].text)
        assertEquals("6789ABCDEF", result[1].text)
        
        // 验证重叠部分
        assertTrue(result[1].text.startsWith(result[0].text.substring(6)))
    }

    @Test
    fun `multi-page document maintains page indices`() {
        val chunker = SlidingWindowChunker(chunkSize = 10, chunkOverlap = 2)
        val pages = listOf("Page1Text", "Page2LongText")
        val result = chunker.chunk(1L, pages)

        // Page 0: "Page1Text" (len 9) -> 1 chunk
        // Page 1: "Page2LongText" (len 13) -> 
        //   C1: "Page2LongT" (len 10)
        //   C2: "ngText" (len 6, start at 10-2=8)
        assertEquals(3, result.size)
        assertEquals(0, result[0].pageIndex)
        assertEquals(1, result[1].pageIndex)
        assertEquals(1, result[2].pageIndex)
        
        assertEquals(0, result[0].chunkIndex)
        assertEquals(1, result[1].chunkIndex)
        assertEquals(2, result[2].chunkIndex)
    }

    @Test
    fun `whitespace is cleaned`() {
        val chunker = SlidingWindowChunker(chunkSize = 100)
        val pages = listOf("  Hello   \n  World  ")
        val result = chunker.chunk(1L, pages)

        assertEquals(1, result.size)
        assertEquals("Hello World", result[0].text)
    }
    
    @Test
    fun `empty pages are ignored`() {
        val chunker = SlidingWindowChunker()
        val pages = listOf("", "   ", "\n")
        val result = chunker.chunk(1L, pages)

        assertTrue(result.isEmpty())
    }
}
