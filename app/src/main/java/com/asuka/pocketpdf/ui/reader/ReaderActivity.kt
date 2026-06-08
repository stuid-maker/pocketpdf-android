package com.asuka.pocketpdf.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.ui.chat.ChatActivity
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class ReaderActivity : ComponentActivity() {

    private val viewModel: ReaderViewModel by viewModels()

    @Inject
    lateinit var controllerFactory: ReaderControllerFactory

    private lateinit var readerController: ReaderController
    private var currentDocumentId: Long = PAGE_INDEX_NONE.toLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        readerController = controllerFactory.create(
            scope = lifecycleScope,
            renderWidth = {
                min(
                    max(resources.displayMetrics.widthPixels * 2, MIN_RENDER_WIDTH),
                    MAX_RENDER_WIDTH,
                )
            },
        )

        val documentId = intent.getLongExtra(EXTRA_DOCUMENT_ID, -1L)
        val initialPage = intent.getIntExtra(EXTRA_PAGE_INDEX, PAGE_INDEX_NONE)
        viewModel.load(documentId)

        setContent {
            PocketPDFTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val pageState by readerController.state.collectAsStateWithLifecycle()

                when (val readerState = uiState) {
                    ReaderUiState.Loading -> ReaderScreen(
                        title = "正在加载 PDF",
                        pageState = pageState.copy(isRendering = true),
                        summaryState = SummaryState.Idle,
                        isIndexed = false,
                        onBack = ::finish,
                        onPageRequested = readerController::render,
                        onSummarizePage = {},
                        onSummarizeDocument = {},
                        onStopSummary = viewModel::stopSummarizing,
                        onOpenChat = {},
                    )
                    is ReaderUiState.Error -> ReaderScreen(
                        title = "无法打开文档",
                        pageState = pageState.copy(error = readerState.message),
                        summaryState = SummaryState.Idle,
                        isIndexed = false,
                        onBack = ::finish,
                        onPageRequested = {},
                        onSummarizePage = {},
                        onSummarizeDocument = {},
                        onStopSummary = {},
                        onOpenChat = {},
                    )
                    is ReaderUiState.Loaded -> {
                        LaunchedEffect(readerState.document.id) {
                            if (currentDocumentId != readerState.document.id) {
                                currentDocumentId = readerState.document.id
                                readerController.open(
                                    document = readerState.document,
                                    initialPage = initialPage.takeIf { it >= 0 } ?: 0,
                                )
                            }
                        }
                        ReaderScreen(
                            title = readerState.document.title,
                            pageState = pageState,
                            summaryState = readerState.summaryState,
                            isIndexed = readerState.document.indexStatus == IndexStatus.INDEXED,
                            onBack = ::finish,
                            onPageRequested = readerController::render,
                            onSummarizePage = {
                                viewModel.summarizePage(pageState.pageIndex)
                            },
                            onSummarizeDocument = viewModel::summarizeFullDocument,
                            onStopSummary = viewModel::stopSummarizing,
                            onOpenChat = {
                                startActivity(ChatActivity.newIntent(this, readerState.document.id))
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        readerController.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DOCUMENT_ID = "com.asuka.pocketpdf.extra.DOCUMENT_ID"
        private const val EXTRA_PAGE_INDEX = "com.asuka.pocketpdf.extra.PAGE_INDEX"
        private const val PAGE_INDEX_NONE = -1
        private const val MIN_RENDER_WIDTH = 1200
        private const val MAX_RENDER_WIDTH = 2400

        fun newIntent(
            context: Context,
            documentId: Long,
            pageIndex: Int = PAGE_INDEX_NONE,
        ): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_DOCUMENT_ID, documentId)
            .putExtra(EXTRA_PAGE_INDEX, pageIndex)
    }
}
