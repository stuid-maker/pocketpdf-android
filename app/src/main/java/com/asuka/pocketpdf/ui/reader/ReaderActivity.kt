package com.asuka.pocketpdf.ui.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.databinding.ActivityReaderBinding
import com.asuka.pocketpdf.domain.model.Document
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()

    private val rendererLock = Any()
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var renderJob: Job? = null
    private var currentDocumentId: Long = -1L
    private var currentPageIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.toolbarReader.setNavigationOnClickListener { finish() }
        binding.btnReaderPrevious.setOnClickListener { renderPage(currentPageIndex - 1) }
        binding.btnReaderNext.setOnClickListener { renderPage(currentPageIndex + 1) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderState(it) }
            }
        }

        viewModel.load(intent.getLongExtra(EXTRA_DOCUMENT_ID, -1L))
    }

    override fun onDestroy() {
        closeRenderer()
        super.onDestroy()
    }

    private fun renderState(state: ReaderUiState) {
        when (state) {
            ReaderUiState.Loading -> {
                binding.progressReaderLoad.isVisible = true
                binding.groupReaderError.isVisible = false
                binding.pdfPageView.isVisible = false
                binding.readerControls.isVisible = false
            }
            is ReaderUiState.Error -> showError(state.message)
            is ReaderUiState.Loaded -> openDocument(state.document)
        }
    }

    private fun openDocument(document: Document) {
        if (currentDocumentId == document.id && pdfRenderer != null) return
        closeRenderer()
        binding.toolbarReader.title = document.title
        binding.progressReaderLoad.isVisible = false
        binding.groupReaderError.isVisible = false
        binding.pdfPageView.isVisible = true
        binding.readerControls.isVisible = true
        try {
            val file = File(document.uri)
            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(requireNotNull(parcelFileDescriptor))
            currentDocumentId = document.id
            currentPageIndex = 0
            Timber.tag(TAG).i("open reader: id=%d title=%s pages=%d", document.id, document.title, pdfRenderer?.pageCount ?: 0)
            renderPage(0)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "open reader failed: id=%d", document.id)
            showError(getString(R.string.reader_error_open_failed, t.message ?: t.javaClass.simpleName))
        }
    }

    private fun renderPage(requestedIndex: Int) {
        val renderer = pdfRenderer ?: return
        val pageCount = renderer.pageCount
        if (pageCount <= 0) {
            showError(getString(R.string.reader_error_empty_pdf))
            return
        }
        val targetIndex = requestedIndex.coerceIn(0, pageCount - 1)
        renderJob?.cancel()
        binding.progressReaderLoad.isVisible = true
        binding.tvReaderPageIndicator.text = getString(
            R.string.reader_page_indicator,
            targetIndex + 1,
            pageCount,
        )
        binding.btnReaderPrevious.isEnabled = targetIndex > 0
        binding.btnReaderNext.isEnabled = targetIndex < pageCount - 1

        val renderWidth = min(max(resources.displayMetrics.widthPixels * 2, MIN_RENDER_WIDTH), MAX_RENDER_WIDTH)
        renderJob = lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    renderBitmap(targetIndex, renderWidth)
                }
                binding.pdfPageView.setBitmap(bitmap)
                currentPageIndex = targetIndex
                bitmap.recycle()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "render page failed: page=%d", targetIndex + 1)
                showError(getString(R.string.reader_error_render_failed, t.message ?: t.javaClass.simpleName))
            } finally {
                binding.progressReaderLoad.isVisible = false
            }
        }
    }

    private fun renderBitmap(pageIndex: Int, renderWidth: Int): Bitmap = synchronized(rendererLock) {
        val renderer = pdfRenderer ?: error("PDF renderer is closed")
        renderer.openPage(pageIndex).use { page ->
            val ratio = page.height.toFloat() / page.width.toFloat()
            val renderHeight = max((renderWidth * ratio).toInt(), 1)
            Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    private fun showError(message: String) {
        closeRenderer()
        binding.progressReaderLoad.isVisible = false
        binding.pdfPageView.isVisible = false
        binding.readerControls.isVisible = false
        binding.groupReaderError.isVisible = true
        binding.tvReaderError.text = message
    }

    private fun closeRenderer() {
        renderJob?.cancel()
        renderJob = null
        if (::binding.isInitialized) {
            binding.pdfPageView.setBitmap(null)
        }
        synchronized(rendererLock) {
            pdfRenderer?.close()
            pdfRenderer = null
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
        }
        currentDocumentId = -1L
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    companion object {
        private const val EXTRA_DOCUMENT_ID = "com.asuka.pocketpdf.extra.DOCUMENT_ID"
        private const val TAG = "ReaderActivity"
        private const val MIN_RENDER_WIDTH = 1200
        private const val MAX_RENDER_WIDTH = 2400

        fun newIntent(context: Context, documentId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_DOCUMENT_ID, documentId)
    }
}
