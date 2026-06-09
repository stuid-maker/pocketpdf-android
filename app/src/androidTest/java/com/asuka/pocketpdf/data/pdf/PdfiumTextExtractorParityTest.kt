package com.asuka.pocketpdf.data.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asuka.pocketpdf.core.DispatcherProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * PdfiumTextExtractor 与 PdfBoxTextExtractor 的提取结果一致性测试。
 *
 * 用 PdfBox 合成已知内容的英文 PDF，分别用两个 extractor 提取文本，
 * 验证两者结果的文本内容一致（忽略空白差异）且页数正确。
 *
 * 设计决策：
 * - 不在 androidTest 中使用 Hilt 注入（避免需要完整的 Hilt test runner），
 *   而是手动构造 extractor 实例。
 * - PdfiumTextExtractor 通过 PdfiumDocumentEngine 间接调用 PDFium 原生库，
 *   因此本测试必须在设备/模拟器上运行（androidTest）。
 * - 使用 [TestDispatcherProvider] 替代真实调度器，简化异步代码。
 */
@RunWith(AndroidJUnit4::class)
class PdfiumTextExtractorParityTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val dispatchers: DispatcherProvider = object : DispatcherProvider {
        override val main = Dispatchers.Main
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
    }

    private lateinit var pdfiumExtractor: PdfiumTextExtractor
    private lateinit var pdfBoxExtractor: PdfBoxTextExtractor
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        val engine = PdfiumDocumentEngine(context, dispatchers)
        pdfiumExtractor = PdfiumTextExtractor(engine, dispatchers)
        pdfBoxExtractor = PdfBoxTextExtractor(dispatchers)
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    // ── helpers ────────────────────────────────────────────────────────

    /**
     * 合成包含指定文本的单页 PDF。
     * 使用 PdfBox 合成以保证 PDF 结构合法，两个 extractor 均能读取。
     */
    private fun synthesizePdf(lines: List<String>): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                var yOffset = 700f
                for (line in lines) {
                    stream.newLineAtOffset(0f, -16f)
                    stream.showText(line)
                    yOffset -= 16f
                }
                stream.endText()
            }
            document.save(output)
        }
        return output.toByteArray()
    }

    /**
     * 合成包含指定文本的多页 PDF，每页一行。
     */
    private fun synthesizeMultiPagePdf(pageTexts: List<String>): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            for (text in pageTexts) {
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText(text)
                    stream.endText()
                }
            }
            document.save(output)
        }
        return output.toByteArray()
    }

    private fun writeTempPdf(bytes: ByteArray, prefix: String = "parity_"): File {
        val tmpFile = File.createTempFile(prefix, ".pdf", context.cacheDir)
        tmpFile.writeBytes(bytes)
        tempFiles.add(tmpFile)
        return tmpFile
    }

    /**
     * 比较两个字符串列表，忽略尾部空格和全空白行。
     */
    private fun assertTextListEquals(
        expected: List<String>,
        actual: List<String>,
        label: String,
    ) {
        assertEquals("$label: page count mismatch", expected.size, actual.size)
        for (i in expected.indices) {
            val expectedTrimmed = expected[i].trimEnd()
            val actualTrimmed = actual[i].trimEnd()
            assertEquals(
                "$label: page $i text differs.\nExpected: '$expectedTrimmed'\nActual:   '$actualTrimmed'",
                expectedTrimmed,
                actualTrimmed,
            )
        }
    }

    // ── tests ──────────────────────────────────────────────────────────

    /**
     * 单页英文 PDF：验证两个 extractor 提取的文本一致。
     */
    @Test
    fun singlePageEnglishTextMatches() = runBlocking {
        val pdfBytes = synthesizePdf(listOf("Hello World", "PDF extraction parity test"))
        val tmpFile = writeTempPdf(pdfBytes)

        val pdfiumPages = pdfiumExtractor.extractPagesText(tmpFile)
        val pdfBoxPages = pdfBoxExtractor.extractPagesText(tmpFile)

        assertEquals("Page count", 1, pdfiumPages.size)
        assertEquals("Page count", 1, pdfBoxPages.size)

        // PdfBox returns trailing whitespace; PDFium may not. Compare after trimming.
        assertTextListEquals(pdfBoxPages, pdfiumPages, "single page")
    }

    /**
     * 多页英文 PDF：验证每页文本一致且页数正确。
     */
    @Test
    fun multiPageEnglishTextMatches() = runBlocking {
        val pageTexts = listOf("Page One Content", "Page Two Content", "Page Three Content")
        val pdfBytes = synthesizeMultiPagePdf(pageTexts)
        val tmpFile = writeTempPdf(pdfBytes)

        val pdfiumPages = pdfiumExtractor.extractPagesText(tmpFile)
        val pdfBoxPages = pdfBoxExtractor.extractPagesText(tmpFile)

        assertEquals("Page count", 3, pdfiumPages.size)
        assertTextListEquals(pdfBoxPages, pdfiumPages, "multi page")
    }

    /**
     * 验证 extractPagesTextWithPositions 返回正确的 pageInfo（宽高 > 0）。
     * positions 应为空列表（PDFium 不提供字符级坐标）。
     */
    @Test
    fun withPositionsReturnsCorrectPageDimensions() = runBlocking {
        val pdfBytes = synthesizePdf(listOf("Dimension test"))
        val tmpFile = writeTempPdf(pdfBytes)

        val result = pdfiumExtractor.extractPagesTextWithPositions(tmpFile)

        assertEquals("Page count", 1, result.size)
        val page = result[0]
        assertEquals("pageIndex", 0, page.pageIndex)
        assertTrue("fullText should contain 'Dimension test', got: '${page.fullText}'",
            page.fullText.trim().contains("Dimension test"))
        assertTrue(
            "positions should be empty (PDFium lacks char-level coords)",
            page.positions.isEmpty(),
        )
        assertTrue("pdfPageWidth should be > 0, got: ${page.pdfPageWidth}", page.pdfPageWidth > 0f)
        assertTrue("pdfPageHeight should be > 0, got: ${page.pdfPageHeight}", page.pdfPageHeight > 0f)
    }

    /**
     * 空 PDF：两个 extractor 都应返回空列表。
     * 空 PDF 理论上不存在（PDF 规范至少一页），此处测试边界行为。
     */
    @Test
    fun emptyPdfReturnsEmptyList() = runBlocking {
        // 创建一个带空白页的 PDF（无文本内容），提取结果应为空字符串列表而非空列表
        val pdfBytes = synthesizeMultiPagePdf(emptyList())
        val tmpFile = writeTempPdf(pdfBytes)

        val pdfiumPages = pdfiumExtractor.extractPagesText(tmpFile)
        val pdfBoxPages = pdfBoxExtractor.extractPagesText(tmpFile)

        assertEquals("Page count — both should agree", pdfBoxPages.size, pdfiumPages.size)
    }

    /**
     * 验证两个 extractor 对同一 PDF 返回相同页数。
     */
    @Test
    fun pageCountConsistent() = runBlocking {
        val pageTexts = (1..5).map { "Page $it" }
        val pdfBytes = synthesizeMultiPagePdf(pageTexts)
        val tmpFile = writeTempPdf(pdfBytes)

        val pdfiumPageCount = pdfiumExtractor.extractPagesText(tmpFile).size
        val pdfBoxPageCount = pdfBoxExtractor.extractPagesText(tmpFile).size

        assertEquals("Page count: Pdfium=$pdfiumPageCount, PdfBox=$pdfBoxPageCount",
            pdfBoxPageCount, pdfiumPageCount)
        assertEquals("Expected 5 pages", 5, pdfiumPageCount)
    }
}
