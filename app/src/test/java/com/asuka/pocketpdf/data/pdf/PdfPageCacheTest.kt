package com.asuka.pocketpdf.data.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.domain.pdf.PdfPageInfo
import com.asuka.pocketpdf.domain.pdf.PdfRenderRequest
import com.asuka.pocketpdf.domain.pdf.PdfSessionClosedException
import io.legere.pdfiumandroid.FindResult
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PdfPageCacheTest {

    // -------------------------------------------------------------------------
    // 1. pageCount 正确返回
    // -------------------------------------------------------------------------
    @Test
    fun `pageCount returns correct value from document`() {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        assertEquals(5, session.pageCount)
    }

    // -------------------------------------------------------------------------
    // 2. pageInfo 返回正确信息
    // -------------------------------------------------------------------------
    @Test
    fun `pageInfo returns correct page dimensions and rotation`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val mockPage = mockk<PdfPage>(relaxed = true)
        every { mockDocument.openPage(0) } returns mockPage
        every { mockPage.getPageWidthPoint() } returns 612
        every { mockPage.getPageHeightPoint() } returns 792
        every { mockPage.getPageRotation() } returns 0

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        val info = session.pageInfo(0)

        assertEquals(0, info.pageIndex)
        assertEquals(612f, info.widthPoints)
        assertEquals(792f, info.heightPoints)
        assertEquals(0, info.rotationDegrees)
    }

    @Test
    fun `pageInfo returns 90 degree rotation for pdfium rotation 1`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 3

        val mockPage = mockk<PdfPage>(relaxed = true)
        every { mockDocument.openPage(1) } returns mockPage
        every { mockPage.getPageWidthPoint() } returns 300
        every { mockPage.getPageHeightPoint() } returns 400
        every { mockPage.getPageRotation() } returns 1

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        val info = session.pageInfo(1)

        assertEquals(1, info.pageIndex)
        assertEquals(90, info.rotationDegrees)
        assertEquals(300f, info.widthPoints)
        assertEquals(400f, info.heightPoints)
    }

    // -------------------------------------------------------------------------
    // 3. render 创建 bitmap 且委托渲染
    // -------------------------------------------------------------------------
    @Test
    fun `render creates bitmap and delegates to pdfium`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val mockPage = mockk<PdfPage>(relaxed = true)
        every { mockDocument.openPage(0) } returns mockPage

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        val request = PdfRenderRequest(
            pageInfo = PdfPageInfo(pageIndex = 0, widthPoints = 612f, heightPoints = 792f),
            widthPx = 300,
            heightPx = 400,
            renderAnnotations = true,
        )

        val bitmap = session.render(request)

        assertNotNull("bitmap should not be null", bitmap)
        assertEquals("bitmap width should match request", 300, bitmap.width)
        assertEquals("bitmap height should match request", 400, bitmap.height)

        verify(exactly = 1) {
            mockPage.renderPageBitmap(
                bitmap = any(),
                startX = 0,
                startY = 0,
                drawSizeX = 300,
                drawSizeY = 400,
                renderAnnot = true,
            )
        }
    }

    // -------------------------------------------------------------------------
    // 4. extractText 返回文本
    // -------------------------------------------------------------------------
    @Test
    fun `extractText returns text from pdfium text page`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 3

        val mockPage = mockk<PdfPage>(relaxed = true)
        val mockTextPage = mockk<PdfTextPage>(relaxed = true)
        every { mockDocument.openPage(0) } returns mockPage
        every { mockPage.openTextPage() } returns mockTextPage
        every { mockTextPage.textPageCountChars() } returns 13
        every { mockTextPage.textPageGetText(0, 13) } returns "Hello, World!"

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        val result = session.extractText(0)

        assertEquals(0, result.pageIndex)
        assertEquals("Hello, World!", result.text)
    }

    // -------------------------------------------------------------------------
    // 5. searchPage 返回匹配结果
    // -------------------------------------------------------------------------
    @Test
    fun `searchPage returns search matches with rects`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val mockPage = mockk<PdfPage>(relaxed = true)
        val mockTextPage = mockk<PdfTextPage>(relaxed = true)
        val mockFinder = mockk<FindResult>(relaxed = true)

        every { mockDocument.openPage(0) } returns mockPage
        every { mockPage.openTextPage() } returns mockTextPage
        every { mockTextPage.textPageCountChars() } returns 12
        every { mockTextPage.textPageGetText(0, 12) } returns "hello world!"
        every { mockTextPage.findStart("hello", emptySet(), 0) } returns mockFinder
        every { mockFinder.findNext() } returns true andThen false
        every { mockFinder.getSchResultIndex() } returns 0
        every { mockFinder.getSchCount() } returns 5
        every { mockTextPage.textPageCountRects(0, 5) } returns 1
        every { mockTextPage.textPageGetRect(0) } returns RectF(10f, 20f, 30f, 40f)

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        val matches = session.searchPage(0, "hello")

        assertEquals("should find one match", 1, matches.size)
        val match = matches[0]
        assertEquals(0, match.pageIndex)
        assertEquals(0, match.startIndex)
        assertEquals(5, match.length)
        assertEquals("hello", match.text)
        assertEquals(1, match.rects.size)
        assertEquals(10f, match.rects[0].left)
        assertEquals(20f, match.rects[0].top)
        assertEquals(30f, match.rects[0].right)
        assertEquals(40f, match.rects[0].bottom)
    }

    // -------------------------------------------------------------------------
    // 6. close 后操作抛出 PdfSessionClosedException
    // -------------------------------------------------------------------------
    @Test
    fun `operations after close throw PdfSessionClosedException`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 3

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())
        session.close()

        assertThrows(PdfSessionClosedException::class.java) {
            kotlinx.coroutines.runBlocking { session.pageInfo(0) }
        }
        assertThrows(PdfSessionClosedException::class.java) {
            kotlinx.coroutines.runBlocking { session.extractText(0) }
        }
    }

    // -------------------------------------------------------------------------
    // 7. 重复 close 安全 + document.close() 只调用一次
    // -------------------------------------------------------------------------
    @Test
    fun `repeated close is idempotent`() {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 3

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        session.close()
        session.close()
        session.close()

        verify(exactly = 1) { mockDocument.close() }
    }

    // -------------------------------------------------------------------------
    // 8. 越界 pageIndex 抛出 IllegalArgumentException
    // -------------------------------------------------------------------------
    @Test
    fun `negative pageIndex throws IllegalArgumentException`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { session.pageInfo(-1) }
        }
    }

    @Test
    fun `pageIndex equal to pageCount throws IllegalArgumentException`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { session.pageInfo(5) }
        }
    }

    @Test
    fun `pageIndex above pageCount throws IllegalArgumentException`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { session.pageInfo(99) }
        }
    }

    // -------------------------------------------------------------------------
    // 9. 空查询 searchPage 返回空列表
    // -------------------------------------------------------------------------
    @Test
    fun `blank query returns empty list without calling openPage`() = runTest {
        val mockDocument = mockk<PdfDocument>(relaxed = true)
        every { mockDocument.getPageCount() } returns 5

        val session = PdfiumDocumentSession(mockDocument, TestDispatcherProvider())

        val blankResult = session.searchPage(0, "   ")
        assertTrue("blank query should return empty list", blankResult.isEmpty())

        val emptyResult = session.searchPage(0, "")
        assertTrue("empty query should return empty list", emptyResult.isEmpty())

        // blank query short-circuits, so openPage is never called
        verify(exactly = 0) { mockDocument.openPage(any()) }
    }

}
