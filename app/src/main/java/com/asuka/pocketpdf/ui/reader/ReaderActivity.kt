package com.asuka.pocketpdf.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private var summaryDialog: BottomSheetDialog? = null
    private var dialogTextView: TextView? = null
    private var dialogScrollView: ScrollView? = null
    private var dialogProgress: View? = null
    private var currentSummaryScope: SummaryScope = SummaryScope.Full

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.toolbarReader.setNavigationOnClickListener { finish() }
        binding.btnReaderPrevious.setOnClickListener { renderPage(currentPageIndex - 1) }
        binding.btnReaderNext.setOnClickListener { renderPage(currentPageIndex + 1) }

        // Summary buttons
        binding.btnSummarizePage.setOnClickListener {
            currentSummaryScope = SummaryScope.Page(currentPageIndex)
            viewModel.summarizePage(currentPageIndex)
        }
        binding.btnSummarizeFull.setOnClickListener {
            currentSummaryScope = SummaryScope.Full
            val state = viewModel.uiState.value
            val doc = (state as? ReaderUiState.Loaded)?.document
            if (doc != null && doc.indexStatus != IndexStatus.INDEXED) {
                Toast.makeText(this, R.string.reader_summary_indexing, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.summarizeFullDocument()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderState(it) }
            }
        }

        viewModel.load(intent.getLongExtra(EXTRA_DOCUMENT_ID, -1L))
    }

    override fun onDestroy() {
        summaryDialog?.dismiss()
        closeRenderer()
        super.onDestroy()
    }

    private fun renderState(state: ReaderUiState) {
        when (state) {
            ReaderUiState.Loading -> {
                showLoading()
            }
            is ReaderUiState.Error -> showError(state.message)
            is ReaderUiState.Loaded -> {
                openDocument(state.document)
                handleSummaryState(state.summaryState)
                // 更新摘要按钮状态
                val isIndexed = state.document.indexStatus == IndexStatus.INDEXED
                val isSummarizing = state.summaryState is SummaryState.Loading ||
                    state.summaryState is SummaryState.Streaming
                binding.btnSummarizeFull.isEnabled = isIndexed && !isSummarizing
                binding.btnSummarizePage.isEnabled = !isSummarizing
            }
        }
    }

    private fun showLoading() {
        binding.progressReaderLoad.isVisible = true
        binding.groupReaderError.isVisible = false
        binding.pdfPageView.isVisible = false
        binding.readerControls.isVisible = false
        binding.readerSummaryBar.isVisible = false
    }

    private fun openDocument(document: Document) {
        if (currentDocumentId == document.id && pdfRenderer != null) return
        closeRenderer()
        binding.toolbarReader.title = document.title
        binding.progressReaderLoad.isVisible = false
        binding.groupReaderError.isVisible = false
        binding.pdfPageView.isVisible = true
        binding.readerControls.isVisible = true
        binding.readerSummaryBar.isVisible = true
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

    // ── Summary UI ─────────────────────────────────────────

    private fun handleSummaryState(summaryState: SummaryState) {
        when (summaryState) {
            SummaryState.Idle -> dismissSummaryDialog()
            SummaryState.Loading -> showSummaryDialog(summaryText = null)
            is SummaryState.Streaming -> showSummaryDialog(summaryText = summaryState.tokens)
            is SummaryState.Done -> showSummaryDialog(summaryText = summaryState.fullText, done = true)
            is SummaryState.Error -> showSummaryDialog(
                summaryText = summaryState.message,
                isError = true,
            )
        }
    }

    private fun showSummaryDialog(
        summaryText: String?,
        done: Boolean = false,
        isError: Boolean = false,
    ) {
        if (summaryDialog == null) {
            val sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_summary, null)
            val tvTitle = sheetView.findViewById<TextView>(R.id.tv_summary_title)
            tvTitle.text = when (currentSummaryScope) {
                is SummaryScope.Full -> getString(R.string.reader_summary_title_full)
                is SummaryScope.Page -> getString(R.string.reader_summary_title_page)
            }
            dialogScrollView = sheetView.findViewById(R.id.scroll_summary)
            dialogTextView = sheetView.findViewById(R.id.tv_summary_content)
            dialogProgress = sheetView.findViewById(R.id.progress_summary)
            val btnStop: Button = sheetView.findViewById(R.id.btn_summary_stop)
            val btnCopy: Button = sheetView.findViewById(R.id.btn_summary_copy)
            val btnClose: View = sheetView.findViewById(R.id.btn_summary_close)

            btnStop.setOnClickListener { viewModel.stopSummarizing() }
            btnClose.setOnClickListener { viewModel.stopSummarizing() }
            btnCopy.setOnClickListener {
                val text = dialogTextView?.text?.toString() ?: return@setOnClickListener
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("summary", text))
                Toast.makeText(this, R.string.reader_summary_copied, Toast.LENGTH_SHORT).show()
            }

            summaryDialog = BottomSheetDialog(this).apply {
                setContentView(sheetView)
                setOnDismissListener {
                    viewModel.stopSummarizing()
                    summaryDialog = null
                }
            }
            summaryDialog?.show()
        }

        // Loading: show progress, hide text. Error: hide progress, show text centered.
        dialogProgress?.isVisible = summaryText == null && !done && !isError
        dialogTextView?.gravity = if (isError) android.view.Gravity.CENTER else android.view.Gravity.NO_GRAVITY

        summaryText?.let { text ->
            dialogTextView?.text = text
            if (!isError) {
                dialogScrollView?.post {
                    dialogScrollView?.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        // 控制按钮可见性
        val dialog = summaryDialog ?: return
        val sheetView = dialog.findViewById<View>(R.id.summary_root) ?: return
        val btnStop = sheetView.findViewById<Button>(R.id.btn_summary_stop)
        val btnCopy = sheetView.findViewById<Button>(R.id.btn_summary_copy)

        btnStop.isVisible = !done && !isError
        btnCopy.isVisible = done && !isError
    }

    private fun dismissSummaryDialog() {
        summaryDialog?.dismiss()
        summaryDialog = null
    }

    // ── PDF rendering ──────────────────────────────────────

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
        dismissSummaryDialog()
        binding.progressReaderLoad.isVisible = false
        binding.pdfPageView.isVisible = false
        binding.readerControls.isVisible = false
        binding.readerSummaryBar.isVisible = false
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
