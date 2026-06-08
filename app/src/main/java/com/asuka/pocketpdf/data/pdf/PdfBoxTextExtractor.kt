package com.asuka.pocketpdf.data.pdf

import com.asuka.pocketpdf.core.DispatcherProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PdfTextExtractor] 的 PdfBox-Android 实现。
 *
 * 实现要点：
 * - `PDDocument.load(file)` 自动判断格式 / 加密；不支持的 PDF 直接抛异常上抛
 * - 用 `PDFTextStripper` 按页设 startPage/endPage，每页一次性 getText——这是 PdfBox 官方推荐
 *   的"按页提取"做法（不是先全文 strip 再按页拆，那种做法对扫描件 / 多列布局会错乱）
 * - 全部用 `withContext(io)` 切到 IO 池：PDDocument.load + PDFTextStripper.getText 都是
 *   阻塞 IO + CPU 混合操作，主线程调用会卡 UI
 * - 必须用 try/finally 关 PDDocument，PdfBox 内部持有 RandomAccessFile，泄漏会撑爆 fd 限制
 *
 * 不在本类做 [com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init]——那是 Application 级
 * 一次性初始化，见 [com.asuka.pocketpdf.PocketPdfApp.onCreate]。
 *
 * 注意页码：PDFTextStripper 的 page 从 **1 开始**，循环 1..numberOfPages 而不是 0..n-1。
 */
@Singleton
class PdfBoxTextExtractor @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PdfTextExtractor {

    override suspend fun extractPagesText(file: File): List<String> = withContext(dispatchers.io) {
        val document = PDDocument.load(file)
        try {
            val total = document.numberOfPages
            if (total == 0) return@withContext emptyList()

            val stripper = PDFTextStripper()
            buildList(total) {
                for (page in 1..total) {
                    stripper.startPage = page
                    stripper.endPage = page
                    add(stripper.getText(document))
                }
            }
        } finally {
            document.close()
        }
    }

    override suspend fun extractPagesTextWithPositions(file: File): List<PageTextWithPositions> =
        withContext(dispatchers.io) {
            val document = PDDocument.load(file)
            try {
                val total = document.numberOfPages
                if (total == 0) return@withContext emptyList()
                buildList(total) {
                    for (page in 1..total) {
                        val positions = mutableListOf<PdfTextPosition>()
                        val stripper = object : PDFTextStripper() {
                            override fun writeString(
                                text: String,
                                textPositions: List<TextPosition>,
                            ) {
                                for (tp in textPositions) {
                                    positions.add(
                                        PdfTextPosition(
                                            text = tp.unicode,
                                            pageIndex = page - 1,
                                            x = tp.xDirAdj,
                                            y = tp.yDirAdj,
                                            width = tp.widthDirAdj,
                                            height = tp.heightDir,
                                        ),
                                    )
                                }
                                super.writeString(text, textPositions)
                            }
                        }
                        stripper.startPage = page
                        stripper.endPage = page
                        val fullText = stripper.getText(document)
                        add(PageTextWithPositions(page - 1, fullText, positions))
                    }
                }
            } finally {
                document.close()
            }
        }
}
