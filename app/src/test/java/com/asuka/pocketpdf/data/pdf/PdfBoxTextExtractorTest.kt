package com.asuka.pocketpdf.data.pdf

import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * PdfBox-Android 资源加载器需要 Android Context 才能加载内置字体（Standard 14 的 AFM 表
 * 在 pdfbox-android 里搬去了 assets，非 jar resources）。所以本类在 JVM 上不能裸跑——
 * 走 Robolectric 拿到 shadow Application，再调 [PDFBoxResourceLoader.init]。
 *
 * Robolectric 选 SDK 26 与项目 minSdk 对齐；启动 +3-5s 是可接受代价。
 *
 * 测试策略：用 PdfBox 反向生成临时 PDF（写 [File.createTempFile] + deleteOnExit），
 * 不往仓库放二进制 sample.pdf 避免 git 膨胀。生成 / 解析路径同时覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PdfBoxTextExtractorTest {

    private lateinit var extractor: PdfBoxTextExtractor

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        extractor = PdfBoxTextExtractor(TestDispatcherProvider())
    }

    @Test
    fun `extract returns one string per page from a synthesized 3-page text pdf`() = runTest {
        val pdf = synthesizePdf(
            listOf(
                "Page one body line.",
                "Page two body line.",
                "Page three body line.",
            ),
        )

        val pages = extractor.extractPagesText(pdf)

        assertEquals("page count must match input", 3, pages.size)
        assertTrue(
            "page 1 text missing: '${pages[0]}'",
            pages[0].contains("Page one body line"),
        )
        assertTrue(
            "page 2 text missing: '${pages[1]}'",
            pages[1].contains("Page two body line"),
        )
        assertTrue(
            "page 3 text missing: '${pages[2]}'",
            pages[2].contains("Page three body line"),
        )
    }

    @Test
    fun `extract returns empty list for a pdf with zero pages`() = runTest {
        val emptyPdf = File.createTempFile("empty", ".pdf").apply { deleteOnExit() }
        PDDocument().use { it.save(emptyPdf) }

        val pages = extractor.extractPagesText(emptyPdf)

        assertEquals(emptyList<String>(), pages)
    }

    @Test
    fun `extract throws on corrupted pdf`() = runTest {
        val bogus = File.createTempFile("bogus", ".pdf").apply {
            writeText("this is not a pdf")
            deleteOnExit()
        }

        assertThrows(Throwable::class.java) {
            kotlinx.coroutines.runBlocking {
                extractor.extractPagesText(bogus)
            }
        }
    }

    private fun synthesizePdf(perPageText: List<String>): File {
        val file = File.createTempFile("synth", ".pdf").apply { deleteOnExit() }
        PDDocument().use { document ->
            perPageText.forEach { line ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText(line)
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }
}
