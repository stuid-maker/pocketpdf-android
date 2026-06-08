package com.asuka.pocketpdf.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import java.io.Closeable
import java.io.File
import kotlin.math.max
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderPageState(
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val bitmap: Bitmap? = null,
    val isRendering: Boolean = false,
    val error: String? = null,
)

interface PdfDocumentSession : Closeable {
    val pageCount: Int
    suspend fun render(pageIndex: Int, widthPx: Int): Bitmap
}

interface ReaderController : Closeable {
    val state: StateFlow<ReaderPageState>
    suspend fun open(document: Document, initialPage: Int)
    fun render(pageIndex: Int)
}

class PdfReaderController(
    private val sessionFactory: suspend (String) -> PdfDocumentSession,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val renderWidth: () -> Int,
) : ReaderController {

    private val mutableState = MutableStateFlow(ReaderPageState())
    override val state: StateFlow<ReaderPageState> = mutableState.asStateFlow()

    private var session: PdfDocumentSession? = null
    private var renderJob: Job? = null

    override suspend fun open(document: Document, initialPage: Int) {
        closeSession()
        val opened = withContext(dispatchers.io) { sessionFactory(document.uri) }
        session = opened
        renderNow(initialPage)
    }

    override fun render(pageIndex: Int) {
        renderJob?.cancel()
        renderJob = scope.launch { renderNow(pageIndex) }
    }

    private suspend fun renderNow(requestedIndex: Int) {
        val activeSession = session ?: return
        if (activeSession.pageCount <= 0) {
            mutableState.value = ReaderPageState(error = "此 PDF 无页面内容")
            return
        }
        val target = requestedIndex.coerceIn(0, activeSession.pageCount - 1)
        mutableState.value = mutableState.value.copy(
            pageIndex = target,
            pageCount = activeSession.pageCount,
            isRendering = true,
            error = null,
        )
        runCatching {
            withContext(dispatchers.io) {
                activeSession.render(target, renderWidth())
            }
        }.onSuccess { bitmap ->
            mutableState.value = ReaderPageState(
                pageIndex = target,
                pageCount = activeSession.pageCount,
                bitmap = bitmap,
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                isRendering = false,
                error = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    override fun close() {
        renderJob?.cancel()
        closeSession()
        mutableState.value = ReaderPageState()
    }

    private fun closeSession() {
        session?.close()
        session = null
    }
}

class AndroidPdfDocumentSession(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val dispatchers: DispatcherProvider,
) : PdfDocumentSession {

    override val pageCount: Int get() = renderer.pageCount

    override suspend fun render(pageIndex: Int, widthPx: Int): Bitmap =
        withContext(dispatchers.io) {
            renderer.openPage(pageIndex).use { page ->
                val height = max((widthPx * page.height.toFloat() / page.width).toInt(), 1)
                Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}

class ReaderControllerFactory @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {
    fun create(
        scope: CoroutineScope,
        renderWidth: () -> Int,
    ): ReaderController = PdfReaderController(
        sessionFactory = { uri ->
            val descriptor = ParcelFileDescriptor.open(
                File(uri),
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            AndroidPdfDocumentSession(
                descriptor = descriptor,
                renderer = PdfRenderer(descriptor),
                dispatchers = dispatchers,
            )
        },
        dispatchers = dispatchers,
        scope = scope,
        renderWidth = renderWidth,
    )
}
