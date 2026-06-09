package com.asuka.pocketpdf.data.pdf

import android.content.Context
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

    /**
     * 中文文本搜索。
     *
     * 注意：PDFBox 的 PDType1Font.HELVETICA 仅支持 Latin-1 字符集，无法渲染中文字符。
     * 要合成中文 PDF 需要使用 PDType0Font 加载 TrueType/OpenType 中文字体（如 Noto Sans CJK），
     * 但 androidTest 环境中不一定有可用字体文件，因此这里用英文 PDF 作为 fallback，
     * 验证搜索流程的正确性（引擎搜索、结果解析、rect 归一化等）。
     *
     * TODO: 当环境支持中文字体时，替换为真实中文 PDF fixture。
     */
    @Test
    fun searchChineseTextFallback(): Unit = runBlocking {
        // Fallback: 使用英文文本验证搜索流程
        val pdfBytes = synthesizePdf("Chinese search test fallback")
        val tmpFile = writeTempPdf(pdfBytes)
        try {
            val engine = createEngine()
            engine.open(tmpFile).use { session ->
                val matches: List<PdfSearchMatch> = session.searchPage(0, "search")
                assertEquals("Should find exactly 1 match for 'search'", 1, matches.size)

                val match = matches[0]
                assertTrue("Match text should contain 'search', got: '${match.text}'",
                    match.text.contains("search"))
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
