package com.asuka.pocketpdf.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationParserTest {

    @Test
    fun `parses standard format`() {
        val pages = CitationParser.parse("详见 [第3页] 的内容")
        assertEquals(listOf(2), pages) // 1-based → 0-based
    }

    @Test
    fun `parses source format`() {
        val pages = CitationParser.parse("参考 [来源: 第5页] 和 [来源：第7页]")
        assertEquals(listOf(4, 6), pages)
    }

    @Test
    fun `parses english format`() {
        val pages = CitationParser.parse("see [Page 2] for details")
        assertEquals(listOf(1), pages)
    }

    @Test
    fun `parses parenthetical format`() {
        val pages = CitationParser.parse("as shown (P.3) and (p.1)")
        assertEquals(listOf(0, 2), pages)
    }

    @Test
    fun `parses chinese bracket format`() {
        val pages = CitationParser.parse("实验详见【来源4】")
        assertEquals(listOf(3), pages)
    }

    @Test
    fun `deduplicates same page`() {
        val pages = CitationParser.parse("[第3页] 又参考 [第3页]")
        assertEquals(listOf(2), pages)
    }

    @Test
    fun `sorts results ascending`() {
        val pages = CitationParser.parse("[第5页] [第1页] [第3页]")
        assertEquals(listOf(0, 2, 4), pages)
    }

    @Test
    fun `ignores invalid page zero`() {
        val pages = CitationParser.parse("[第0页] 无意义 [第1页]")
        assertEquals(listOf(0), pages) // only page 1, skip 0
    }

    @Test
    fun `returns empty for no citations`() {
        val pages = CitationParser.parse("这是一段没有引用的纯文本。")
        assertTrue(pages.isEmpty())
    }

    @Test
    fun `parseWithRanges returns correct spans`() {
        val ranges = CitationParser.parseWithRanges("前文 [第3页] 后文")
        assertEquals(1, ranges.size)
        assertEquals(2, ranges[0].pageIndex) // 0-based
    }
}
