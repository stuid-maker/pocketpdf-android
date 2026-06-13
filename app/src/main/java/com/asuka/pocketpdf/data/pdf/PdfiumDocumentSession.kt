package com.asuka.pocketpdf.data.pdf

import android.graphics.Bitmap
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.pdf.PdfDocumentSession
import com.asuka.pocketpdf.domain.pdf.PdfPageInfo
import com.asuka.pocketpdf.domain.pdf.PdfPageRect
import com.asuka.pocketpdf.domain.pdf.PdfPageText
import com.asuka.pocketpdf.domain.pdf.PdfRenderRequest
import com.asuka.pocketpdf.domain.pdf.PdfSearchMatch
import com.asuka.pocketpdf.domain.pdf.PdfSessionClosedException
import io.legere.pdfiumandroid.PdfDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class PdfiumDocumentSession internal constructor(
    private val document: PdfDocument,
    private val dispatchers: DispatcherProvider,
) : PdfDocumentSession {

    private val operationMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val closeScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override val pageCount: Int = document.getPageCount()

    override suspend fun pageInfo(pageIndex: Int): PdfPageInfo = nativeOperation(pageIndex) {
        document.openPage(pageIndex).use { page ->
            PdfPageInfo(
                pageIndex = pageIndex,
                widthPoints = page.getPageWidthPoint().toFloat(),
                heightPoints = page.getPageHeightPoint().toFloat(),
                rotationDegrees = pdfiumRotationToDegrees(page.getPageRotation()),
            )
        }
    }

    override suspend fun render(request: PdfRenderRequest): Bitmap =
        nativeOperation(request.pageInfo.pageIndex) {
            val bitmap = Bitmap.createBitmap(
                request.widthPx,
                request.heightPx,
                Bitmap.Config.ARGB_8888,
            )
            try {
                document.openPage(request.pageInfo.pageIndex).use { page ->
                    page.renderPageBitmap(
                        bitmap = bitmap,
                        startX = 0,
                        startY = 0,
                        drawSizeX = request.widthPx,
                        drawSizeY = request.heightPx,
                        renderAnnot = request.renderAnnotations,
                    )
                }
                bitmap
            } catch (error: Throwable) {
                bitmap.recycle()
                throw error
            }
        }

    override suspend fun extractText(pageIndex: Int): PdfPageText = nativeOperation(pageIndex) {
        document.openPage(pageIndex).use { page ->
            page.openTextPage().use { textPage ->
                val characterCount = textPage.textPageCountChars().coerceAtLeast(0)
                PdfPageText(
                    pageIndex = pageIndex,
                    text = textPage.textPageGetText(0, characterCount).orEmpty(),
                )
            }
        }
    }

    override suspend fun searchPage(pageIndex: Int, query: String): List<PdfSearchMatch> {
        if (query.isBlank()) return emptyList()
        return nativeOperation(pageIndex) {
            document.openPage(pageIndex).use { page ->
                page.openTextPage().use { textPage ->
                    val fullText = textPage.textPageGetText(
                        startIndex = 0,
                        length = textPage.textPageCountChars().coerceAtLeast(0),
                    ).orEmpty()
                    val matches = mutableListOf<PdfSearchMatch>()
                    textPage.findStart(query, emptySet(), 0)?.use { finder ->
                        while (finder.findNext()) {
                            val startIndex = finder.getSchResultIndex()
                            val length = finder.getSchCount()
                            val rectCount = textPage.textPageCountRects(startIndex, length)
                            val rects = (0 until rectCount.coerceAtLeast(0)).mapNotNull { rectIndex ->
                                textPage.textPageGetRect(rectIndex)?.let { rect ->
                                    PdfPageRect(rect.left, rect.top, rect.right, rect.bottom)
                                }
                            }
                            if (length > 0 && rects.isNotEmpty()) {
                                matches += PdfSearchMatch(
                                    pageIndex = pageIndex,
                                    startIndex = startIndex,
                                    length = length,
                                    text = fullText.substringOrQuery(startIndex, length, query),
                                    rects = rects,
                                )
                            }
                        }
                    }
                    matches
                }
            }
        }
    }

    override suspend fun mapPageRectsToDevice(
        pageIndex: Int,
        rects: List<PdfPageRect>,
        widthPx: Int,
        heightPx: Int,
    ): List<PdfPageRect> {
        require(widthPx > 0 && heightPx > 0) { "device dimensions must be positive" }
        if (rects.isEmpty()) return emptyList()
        return nativeOperation(pageIndex) {
            document.openPage(pageIndex).use { page ->
                val rotation = page.getPageRotation()
                rects.map { rect ->
                    val points = listOf(
                        page.mapPageCoordsToDevice(0, 0, widthPx, heightPx, rotation, rect.left.toDouble(), rect.top.toDouble()),
                        page.mapPageCoordsToDevice(0, 0, widthPx, heightPx, rotation, rect.right.toDouble(), rect.top.toDouble()),
                        page.mapPageCoordsToDevice(0, 0, widthPx, heightPx, rotation, rect.left.toDouble(), rect.bottom.toDouble()),
                        page.mapPageCoordsToDevice(0, 0, widthPx, heightPx, rotation, rect.right.toDouble(), rect.bottom.toDouble()),
                    )
                    PdfPageRect(
                        left = points.minOf { it.x }.toFloat(),
                        top = points.minOf { it.y }.toFloat(),
                        right = points.maxOf { it.x }.toFloat(),
                        bottom = points.maxOf { it.y }.toFloat(),
                    )
                }
            }
        }
    }

    override fun close() {
        // 同步置位 closed，保证 close() 返回后新操作立即抛出 PdfSessionClosedException；
        // 真正的 native document.close() 调度到 IO 线程执行，避免在主线程 runBlocking。
        if (!closed.compareAndSet(false, true)) return
        closeScope.launch {
            try {
                operationMutex.withLock {
                    document.close()
                }
            } finally {
                closeScope.cancel()
            }
        }
    }

    private suspend fun <T> nativeOperation(pageIndex: Int, block: () -> T): T {
        require(pageIndex in 0 until pageCount) {
            "pageIndex $pageIndex is outside 0 until $pageCount"
        }
        return withContext(dispatchers.io) {
            operationMutex.withLock {
                if (closed.get()) throw PdfSessionClosedException()
                block()
            }
        }
    }

    private fun String.substringOrQuery(startIndex: Int, length: Int, query: String): String {
        val endIndex = startIndex + length
        return if (startIndex >= 0 && endIndex <= this.length) {
            substring(startIndex, endIndex)
        } else {
            query
        }
    }
}

internal fun pdfiumRotationToDegrees(rotation: Int): Int {
    require(rotation in 0..3) { "PDFium rotation must be in 0..3" }
    return rotation * 90
}
