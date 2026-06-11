package com.asuka.pocketpdf.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.pdf.PdfSearchMatch
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * PDF 搜索功能的多语言 fixture 测试。
 *
 * 通过 PDFBox 合成带文本的 PDF，然后经 PdfiumDocumentEngine → session.searchPage()
 * 搜索，验证搜索结果的数量、内容和矩形区域。
 */
@RunWith(AndroidJUnit4::class)
class PdfiumSearchFixtureTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Main
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
    }

    // ── helpers ────────────────────────────────────────────────────────

    private fun synthesizePdf(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText(text)
                stream.endText()
            }
            document.save(output)
        }
        return output.toByteArray()
    }

    private fun writeTempPdf(bytes: ByteArray): File {
        val tmpFile = File.createTempFile("search_fixture_", ".pdf", context.cacheDir)
        tmpFile.writeBytes(bytes)
        return tmpFile
    }

    private fun synthesizeAndroidPdf(text: String): File {
        val file = File.createTempFile("search_android_", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 800, 1).create())
            page.canvas.drawText(
                text,
                50f,
                120f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 32f
                },
            )
            document.finishPage(page)
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }

    private fun createEngine(): PdfiumDocumentEngine {
        PDFBoxResourceLoader.init(context)
        return PdfiumDocumentEngine(context, dispatchers)
    }

    // ── tests ──────────────────────────────────────────────────────────

    /**
     * 英文简单文本：合成 "Hello World PDF Search Test"，搜索 "World"。
     * 期望：1 个结果，match.text 包含 "World"，rects 非空。
     */
    @Test
    fun searchEnglishSimpleText(): Unit = runBlocking {
        val pdfBytes = synthesizePdf("Hello World PDF Search Test")
        val tmpFile = writeTempPdf(pdfBytes)
        try {
            val engine = createEngine()
            engine.open(tmpFile).use { session ->
                val matches: List<PdfSearchMatch> = session.searchPage(0, "World")
                assertEquals("Should find exactly 1 match for 'World'", 1, matches.size)

                val match = matches[0]
                assertEquals("Match pageIndex", 0, match.pageIndex)
                assertTrue("Match text should contain 'World', got: '${match.text}'",
                    match.text.contains("World"))
                assertTrue("Match length should be > 0", match.length > 0)
                assertTrue("Match startIndex should be >= 0", match.startIndex >= 0)
                assertTrue("rects should not be empty", match.rects.isNotEmpty())
                match.rects.forEach { rect ->
                    assertTrue("rect left=${rect.left}, right=${rect.right}", rect.left < rect.right)
                    assertTrue("rect top=${rect.top}, bottom=${rect.bottom}", rect.top < rect.bottom)
                }
            }
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun searchChineseText(): Unit = runBlocking {
        val tmpFile = synthesizeAndroidPdf("这是中文搜索关键词测试")
        try {
            val engine = createEngine()
            engine.open(tmpFile).use { session ->
                val matches: List<PdfSearchMatch> = session.searchPage(0, "搜索关键词")
                assertEquals("Should find exactly 1 Chinese match", 1, matches.size)

                val match = matches[0]
                assertTrue(
                    "Match text should contain Chinese keyword, got: '${match.text}'",
                    match.text.contains("搜索关键词"),
                )
                assertTrue("rects should not be empty", match.rects.isNotEmpty())

                // 验证 rect 坐标归一化：left < right, top < bottom
                match.rects.forEach { rect ->
                    assertTrue("rect left=${rect.left} < right=${rect.right}",
                        rect.left < rect.right)
                    assertTrue("rect top=${rect.top} < bottom=${rect.bottom}",
                        rect.top < rect.bottom)
                }
            }
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun mappedHighlightIntersectsRenderedGlyphs(): Unit = runBlocking {
        val tmpFile = writeTempPdf(synthesizePdf("Hello World PDF Search Test"))
        try {
            val engine = createEngine()
            engine.open(tmpFile).use { session ->
                val pageInfo = session.pageInfo(0)
                val match = session.searchPage(0, "World").single()
                val mapped = session.mapPageRectsToDevice(
                    pageIndex = 0,
                    rects = match.rects,
                    widthPx = 612,
                    heightPx = 792,
                ).single()
                val bitmap = session.render(
                    com.asuka.pocketpdf.domain.pdf.PdfRenderRequest(
                        pageInfo = pageInfo,
                        widthPx = 612,
                        heightPx = 792,
                    ),
                )
                try {
                    assertTrue(
                        "Mapped highlight must cover rendered ink: $mapped",
                        countDarkPixels(bitmap, mapped) > 0,
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            tmpFile.delete()
        }
    }

    private fun countDarkPixels(
        bitmap: Bitmap,
        rect: com.asuka.pocketpdf.domain.pdf.PdfPageRect,
    ): Int {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val color = bitmap.getPixel(x, y)
                if (Color.red(color) < 220 || Color.green(color) < 220 || Color.blue(color) < 220) {
                    count++
                }
            }
        }
        return count
    }

    /**
     * 重复词搜索：合成 "test the test with test again"，搜索 "test"。
     * 期望：3 个结果，每个 rects 非空。
     */
    @Test
    fun searchRepeatedWord(): Unit = runBlocking {
        val pdfBytes = synthesizePdf("test the test with test again")
        val tmpFile = writeTempPdf(pdfBytes)
        try {
            val engine = createEngine()
            engine.open(tmpFile).use { session ->
                val matches: List<PdfSearchMatch> = session.searchPage(0, "test")
                assertEquals("Should find exactly 3 matches for 'test'", 3, matches.size)

                matches.forEachIndexed { index, match ->
                    assertEquals("Match[$index] pageIndex", 0, match.pageIndex)
                    assertTrue("Match[$index] text should contain 'test', got: '${match.text}'",
                        match.text.contains("test"))
                    assertTrue("Match[$index] length should be > 0", match.length > 0)
                    assertTrue("Match[$index] startIndex should be >= 0", match.startIndex >= 0)
                    assertNotNull("Match[$index] rects should not be null", match.rects)
                    assertTrue("Match[$index] rects should not be empty", match.rects.isNotEmpty())
                    match.rects.forEach { rect ->
                        assertTrue("rect left=${rect.left} < right=${rect.right}",
                            rect.left < rect.right)
                        assertTrue("rect top=${rect.top} < bottom=${rect.bottom}",
                            rect.top < rect.bottom)
                    }
                }
            }
        } finally {
            tmpFile.delete()
        }
    }

    /**
     * 空查询 / 空白查询：搜索 "" 或 "   " 应返回空列表。
     */
    @Test
    fun searchEmptyQuery(): Unit = runBlocking {
        val pdfBytes = synthesizePdf("Some content for empty query test")
        val tmpFile = writeTempPdf(pdfBytes)
        try {
            val engine = createEngine()
            engine.open(tmpFile).use { session ->
                val matchesEmpty = session.searchPage(0, "")
                assertTrue("Empty string query should return empty list",
                    matchesEmpty.isEmpty())

                val matchesBlank = session.searchPage(0, "   ")
                assertTrue("Blank query should return empty list",
                    matchesBlank.isEmpty())
            }
        } finally {
            tmpFile.delete()
        }
    }

    /**
     * 不存在的词：搜索 "xyznotfound" 应返回空列表。
     */
    @Test
    fun searchNonExistentWord(): Unit = runBlocking {
        val pdfBytes = synthesizePdf("Hello World PDF Search Test")
        val tmpFile = writeTempPdf(pdfBytes)
        try {
            val engine = createEngine()
            engine.open(tmpFile).use { session ->
                val matches = session.searchPage(0, "xyznotfound")
                assertNotNull("Result should not be null", matches)
                assertTrue("Search for non-existent word should return empty list",
                    matches.isEmpty())
            }
        } finally {
            tmpFile.delete()
        }
    }
}
