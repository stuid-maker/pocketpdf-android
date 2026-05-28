package com.asuka.pocketpdf.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SummaryScope 测试：验证 sealed class 的两种实例。
 */
class SummaryScopeTest {

    @Test
    fun `Full is a singleton data object`() {
        val a = SummaryScope.Full
        val b = SummaryScope.Full
        assertTrue(a === b) // 单例，同一引用
    }

    @Test
    fun `Page has correct pageIndex`() {
        val page = SummaryScope.Page(pageIndex = 3)
        assertEquals(3, page.pageIndex)
    }

    @Test
    fun `Page can be zero based`() {
        val page = SummaryScope.Page(pageIndex = 0)
        assertEquals(0, page.pageIndex)
    }

    @Test
    fun `Page data class equality`() {
        val a = SummaryScope.Page(5)
        val b = SummaryScope.Page(5)
        assertEquals(a, b)
    }

    @Test
    fun `Page data class inequality`() {
        val a = SummaryScope.Page(1)
        val b = SummaryScope.Page(2)
        assertEquals(false, a == b)
    }

    @Test
    fun `Full and Page are different types`() {
        val full: SummaryScope = SummaryScope.Full
        val page: SummaryScope = SummaryScope.Page(0)
        assertEquals(true, full is SummaryScope.Full)
        assertEquals(true, page is SummaryScope.Page)
        assertEquals(false, full is SummaryScope.Page)
        assertEquals(false, page is SummaryScope.Full)
    }

    @Test
    fun `Full toString is descriptive`() {
        assertEquals(true, SummaryScope.Full.toString().contains("Full"))
    }

    @Test
    fun `Page toString contains index`() {
        assertEquals(true, SummaryScope.Page(3).toString().contains("3"))
    }
}
