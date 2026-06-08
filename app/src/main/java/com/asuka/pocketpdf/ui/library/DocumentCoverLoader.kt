package com.asuka.pocketpdf.ui.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import timber.log.Timber

interface DocumentCoverLoader {
    suspend fun load(document: Document, widthPx: Int, heightPx: Int): DocumentCover
}

interface PdfCoverRenderer {
    suspend fun renderFirstPage(uri: String, widthPx: Int, heightPx: Int): Bitmap
}

@Singleton
class PdfDocumentCoverLoader @Inject constructor(
    private val renderer: PdfCoverRenderer,
    private val dispatchers: DispatcherProvider,
) : DocumentCoverLoader {

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    override suspend fun load(
        document: Document,
        widthPx: Int,
        heightPx: Int,
    ): DocumentCover = withContext(dispatchers.io) {
        val key = "${document.id}:${document.uri}:$widthPx:$heightPx"
        cache.get(key)?.let { return@withContext DocumentCover.Thumbnail(it) }

        runCatching {
            renderer.renderFirstPage(document.uri, widthPx, heightPx)
        }.onSuccess { bitmap ->
            cache.put(key, bitmap)
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Cover render failed for document #%d", document.id)
        }.fold(
            onSuccess = { DocumentCover.Thumbnail(it) },
            onFailure = { fallbackCover(document.id, document.title) },
        )
    }

    private companion object {
        const val TAG = "DocumentCoverLoader"
        const val MAX_CACHE_KB = 8 * 1024
    }
}

@Singleton
class AndroidPdfCoverRenderer @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PdfCoverRenderer {

    override suspend fun renderFirstPage(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap = withContext(dispatchers.io) {
        ParcelFileDescriptor.open(File(uri), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount > 0) { "PDF has no pages" }
                renderer.openPage(0).use { page ->
                    val scale = maxOf(
                        widthPx / page.width.toFloat(),
                        heightPx / page.height.toFloat(),
                    )
                    val renderedWidth = (page.width * scale).toInt().coerceAtLeast(1)
                    val renderedHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    val rendered = Bitmap.createBitmap(
                        renderedWidth,
                        renderedHeight,
                        Bitmap.Config.ARGB_8888,
                    )
                    rendered.eraseColor(Color.WHITE)
                    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    Canvas(output).apply {
                        drawColor(Color.WHITE)
                        val left = (renderedWidth - widthPx) / 2
                        val top = (renderedHeight - heightPx) / 2
                        drawBitmap(
                            rendered,
                            Rect(left, top, left + widthPx, top + heightPx),
                            Rect(0, 0, widthPx, heightPx),
                            null,
                        )
                    }
                    rendered.recycle()
                    output
                }
            }
        }
    }
}
