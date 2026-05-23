package com.asuka.pocketpdf.data.chunking

import com.asuka.pocketpdf.domain.model.DocumentChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphChunkerTest {

    private val chunker = ParagraphChunker()

    @Test
    fun `empty pages return empty chunks`() {
        val result = chunker.chunk(1L, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank pages are skipped`() {
        val pages = listOf("", "   ", "\n", "\t")
        val result = chunker.chunk(1L, pages)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single page single paragraph returns one chunk`() {
        val pages = listOf("Hello World")
        val result = chunker.chunk(1L, pages)

        assertEquals(1, result.size)
        assertEquals("Hello World", result[0].text)
        assertEquals(0, result[0].pageIndex)
        assertEquals(0, result[0].chunkIndex)
        assertEquals(1L, result[0].documentId)
    }

    @Test
    fun `single page multiple paragraphs returns corresponding number of chunks`() {
        val pages = listOf("First paragraph.\n\nSecond paragraph.\n\nThird paragraph.")
        val result = chunker.chunk(1L, pages)

        assertEquals(3, result.size)
        assertEquals("First paragraph.", result[0].text)
        assertEquals("Second paragraph.", result[1].text)
        assertEquals("Third paragraph.", result[2].text)
        assertEquals(0, result[0].pageIndex)
        assertEquals(0, result[0].chunkIndex)
        assertEquals(1, result[1].chunkIndex)
        assertEquals(2, result[2].chunkIndex)
    }

    @Test
    fun `paragraphs with extra whitespace between them are handled`() {
        val pages = listOf("First.\n\n  \n\nSecond.")
        val result = chunker.chunk(1L, pages)

        assertEquals(2, result.size)
        assertEquals("First.", result[0].text)
        assertEquals("Second.", result[1].text)
    }

    @Test
    fun `multiple pages each split independently`() {
        val pages = listOf(
            "Page1 para1.\n\nPage1 para2.",
            "Page2 para1.\n\nPage2 para2.\n\nPage2 para3.",
        )
        val result = chunker.chunk(1L, pages)

        // Page 0 -> 2 chunks, Page 1 -> 3 chunks, total = 5
        assertEquals(5, result.size)

        // Page 0 chunks
        assertEquals(0, result[0].pageIndex)
        assertEquals(0, result[0].chunkIndex)
        assertEquals("Page1 para1.", result[0].text)

        assertEquals(0, result[1].pageIndex)
        assertEquals(1, result[1].chunkIndex)
        assertEquals("Page1 para2.", result[1].text)

        // Page 1 chunks
        assertEquals(1, result[2].pageIndex)
        assertEquals(2, result[2].chunkIndex)
        assertEquals("Page2 para1.", result[2].text)

        assertEquals(1, result[3].pageIndex)
        assertEquals(3, result[3].chunkIndex)
        assertEquals("Page2 para2.", result[3].text)

        assertEquals(1, result[4].pageIndex)
        assertEquals(4, result[4].chunkIndex)
        assertEquals("Page2 para3.", result[4].text)
    }

    @Test
    fun `documentId is propagated to all chunks`() {
        val pages = listOf("A.\n\nB.", "C.")
        val result = chunker.chunk(42L, pages)

        assertEquals(3, result.size)
        result.forEach { chunk ->
            assertEquals(42L, chunk.documentId)
        }
    }

    @Test
    fun `paragraphs separated by double newline with surrounding spaces`() {
        val pages = listOf("  First  \n\n  Second  ")
        val result = chunker.chunk(1L, pages)

        assertEquals(2, result.size)
        assertEquals("First", result[0].text)
        assertEquals("Second", result[1].text)
    }

    @Test
    fun `mix of blank and non-blank pages`() {
        val pages = listOf("", "Content here.", "", "More content.")
        val result = chunker.chunk(1L, pages)

        assertEquals(2, result.size)
        assertEquals(1, result[0].pageIndex)
        assertEquals("Content here.", result[0].text)
        assertEquals(3, result[1].pageIndex)
        assertEquals("More content.", result[1].text)
    }

    // ── Long paragraph sliding-window tests ──────────────────────────

    @Test
    fun `long paragraph is split into multiple chunks`() {
        // maxChunkChars=50, chunkOverlap=10
        val smallChunker = ParagraphChunker(maxChunkChars = 50, chunkOverlap = 10)
        // 130 chars total → 3 windows: [0..50), [40..90), [80..130)
        val longPara = "A".repeat(40) + "B".repeat(40) + "C".repeat(40)  // 120 chars
        // Actually 120 chars with max=50 → windows: 0-50, 40-90, 80-120
        val result = smallChunker.chunk(1L, listOf(longPara))

        assertEquals(3, result.size)
        // Each chunk at most 50 chars
        result.forEach { assertTrue(it.text.length <= 50) }
        // All chunks belong to same page (pageIndex=0)
        result.forEach { assertEquals(0, it.pageIndex) }
        // Chunk indices are sequential
        assertEquals(0, result[0].chunkIndex)
        assertEquals(1, result[1].chunkIndex)
        assertEquals(2, result[2].chunkIndex)
    }

    @Test
    fun `long paragraph chunks preserve overlap`() {
        val smallChunker = ParagraphChunker(maxChunkChars = 50, chunkOverlap = 10)
        // Unique chars so we can verify overlap:
        // Positions: 0..49 = 'A'*50, 40..89 = 'B'*50, 80..119 = 'C'*40
        val text = "A".repeat(40) + "B".repeat(40) + "C".repeat(40)  // 120 chars

        val result = smallChunker.chunk(1L, listOf(text))

        // Chunk 0 ends at 50, chunk 1 starts at 40 → overlap is text[40..50) = 10 chars
        assertEquals("A".repeat(40) + "B".repeat(10), result[0].text)
        // Chunk 1 is text[40..90) = B(10) + B(30) + C(10)
        assertEquals( "B".repeat(10) + "B".repeat(30) + "C".repeat(10), result[1].text)
        // Chunk 2 is text[80..120) = "C" * 40
        assertEquals("C".repeat(40), result[2].text)
    }

    @Test
    fun `short paragraph just under limit is not split`() {
        val smallChunker = ParagraphChunker(maxChunkChars = 100, chunkOverlap = 10)
        val text = "A".repeat(100)

        val result = smallChunker.chunk(1L, listOf(text))

        assertEquals(1, result.size)
        assertEquals(text, result[0].text)
    }

    @Test
    fun `paragraph exactly at limit is not split`() {
        val smallChunker = ParagraphChunker(maxChunkChars = 50, chunkOverlap = 10)
        val text = "A".repeat(50)

        val result = smallChunker.chunk(1L, listOf(text))

        assertEquals(1, result.size)
        assertEquals(text, result[0].text)
    }

    @Test
    fun `multiple long paragraphs are each split independently`() {
        val smallChunker = ParagraphChunker(maxChunkChars = 30, chunkOverlap = 5)
        val para1 = "A".repeat(70)  // → splits into 3 windows: 0-30, 25-55, 50-70
        val para2 = "B".repeat(40)  // → splits into 2 windows: 0-30, 25-40

        val result = smallChunker.chunk(1L, listOf("$para1\n\n$para2"))

        assertEquals(5, result.size)
        // First 3 chunks from para1, next 2 from para2
        assertTrue(result[0].text.startsWith("A"))
        assertTrue(result[1].text.startsWith("A"))
        assertTrue(result[2].text.startsWith("A"))
        assertTrue(result[3].text.startsWith("B"))
        assertTrue(result[4].text.startsWith("B"))
        // Chunk indices sequential
        assertEquals(0, result[0].chunkIndex)
        assertEquals(1, result[1].chunkIndex)
        assertEquals(2, result[2].chunkIndex)
        assertEquals(3, result[3].chunkIndex)
        assertEquals(4, result[4].chunkIndex)
    }

    @Test
    fun `mixed short and long paragraphs`() {
        val smallChunker = ParagraphChunker(maxChunkChars = 30, chunkOverlap = 5)
        val shortPara = "Short paragraph."
        val longPara = "A".repeat(80)  // → 3 windows: 0-30, 25-55, 50-80

        val result = smallChunker.chunk(1L, listOf("$shortPara\n\n$longPara"))

        assertEquals(4, result.size)
        assertEquals(shortPara, result[0].text)
        assertTrue(result[1].text.startsWith("A"))
        assertTrue(result[3].text.startsWith("A"))
    }

    @Test
    fun `long paragraph across multiple pages`() {
        val smallChunker = ParagraphChunker(maxChunkChars = 50, chunkOverlap = 10)
        val longPara = "A".repeat(120)  // 120 chars → 3 windows

        val result = smallChunker.chunk(42L, listOf(longPara, longPara))

        assertEquals(6, result.size)
        // Page 0: chunks 0..2
        assertEquals(0, result[0].pageIndex)
        assertEquals(0, result[0].chunkIndex)
        assertEquals(0, result[1].pageIndex)
        assertEquals(1, result[1].chunkIndex)
        assertEquals(0, result[2].pageIndex)
        assertEquals(2, result[2].chunkIndex)
        // Page 1: chunks 3..5
        assertEquals(1, result[3].pageIndex)
        assertEquals(3, result[3].chunkIndex)
        assertEquals(1, result[4].pageIndex)
        assertEquals(4, result[4].chunkIndex)
        assertEquals(1, result[5].pageIndex)
        assertEquals(5, result[5].chunkIndex)
        // DocumentId
        result.forEach { assertEquals(42L, it.documentId) }
    }
}
