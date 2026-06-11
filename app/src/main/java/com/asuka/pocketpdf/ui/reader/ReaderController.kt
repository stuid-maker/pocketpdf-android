package com.asuka.pocketpdf.ui.reader

import android.graphics.Bitmap
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfDocumentSession
import com.asuka.pocketpdf.domain.pdf.PdfRenderRequest
import java.io.Closeable
import java.io.File
import kotlin.math.max
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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

interface ReaderController : Closeable {
    val state: StateFlow<ReaderPageState>
    suspend fun open(document: Document, initialPage: Int)
    fun render(pageIndex: Int)
}

class PdfReaderController(
    private val documentEngine: PdfDocumentEngine,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val renderWidth: () -> Int,
) : ReaderController {

    private val mutableState = MutableStateFlow(ReaderPageState())
    override val state: StateFlow<ReaderPageState> = mutableState.asStateFlow()

    private var session: PdfDocumentSession? = null
    private var renderJob: Job? = null
    private var renderGeneration: Long = 0

    override suspend fun open(document: Document, initialPage: Int) {
        renderJob?.cancel()
        renderGeneration++
        closeSession()
        val opened = documentEngine.open(File(document.uri))
        session = opened
        renderNow(initialPage, renderGeneration)
    }

    override fun render(pageIndex: Int) {
        renderJob?.cancel()
        val generation = ++renderGeneration
        renderJob = scope.launch { renderNow(pageIndex, generation) }
    }

    private suspend fun renderNow(requestedIndex: Int, generation: Long) {
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
        try {
            val bitmap = withContext(dispatchers.io) {
                val pageInfo = activeSession.pageInfo(target)
                val width = max(renderWidth(), 1)
                val height = max(
                    (width * pageInfo.heightPoints / pageInfo.widthPoints).toInt(),
                    1,
                )
                activeSession.render(
                    PdfRenderRequest(
                        pageInfo = pageInfo,
                        widthPx = width,
                        heightPx = height,
                    ),
                )
            }
            if (generation != renderGeneration || activeSession !== session) {
                bitmap.recycle()
                return
            }
            mutableState.value.bitmap?.recycle()
            mutableState.value = ReaderPageState(
                pageIndex = target,
                pageCount = activeSession.pageCount,
                bitmap = bitmap,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (generation != renderGeneration || activeSession !== session) return
            mutableState.value = mutableState.value.copy(
                isRendering = false,
                error = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    override fun close() {
        renderJob?.cancel()
        renderGeneration++
        closeSession()
        mutableState.value = ReaderPageState()
    }

    private fun closeSession() {
        session?.close()
        session = null
    }
}

class ReaderControllerFactory @Inject constructor(
    private val documentEngine: PdfDocumentEngine,
    private val dispatchers: DispatcherProvider,
) {
    fun create(
        scope: CoroutineScope,
        renderWidth: () -> Int,
    ): ReaderController = PdfReaderController(
        documentEngine = documentEngine,
        dispatchers = dispatchers,
        scope = scope,
        renderWidth = renderWidth,
    )
}
