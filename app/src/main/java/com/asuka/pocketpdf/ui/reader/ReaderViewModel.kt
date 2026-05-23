package com.asuka.pocketpdf.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.usecase.GetDocumentUseCase
import com.asuka.pocketpdf.domain.usecase.NoChunksException
import com.asuka.pocketpdf.domain.usecase.NoChunksForPageException
import com.asuka.pocketpdf.domain.usecase.SummarizeDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getDocument: GetDocumentUseCase,
    private val summarizeDocument: SummarizeDocumentUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var summaryJob: Job? = null
    private var currentDocument: com.asuka.pocketpdf.domain.model.Document? = null

    fun load(documentId: Long) {
        if (documentId <= 0L) {
            _uiState.value = ReaderUiState.Error("Invalid document id: $documentId")
            return
        }
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                val state = withContext(dispatchers.io) {
                    val document = getDocument(documentId)
                        ?: return@withContext ReaderUiState.Error("Document #$documentId not found")
                    val pdfFile = File(document.uri)
                    if (!pdfFile.isFile) {
                        return@withContext ReaderUiState.Error("PDF file missing: ${document.title}")
                    }
                    ReaderUiState.Loaded(document)
                }
                currentDocument = (state as? ReaderUiState.Loaded)?.document
                _uiState.value = state
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                val msg = t.message ?: t.javaClass.simpleName
                Timber.tag(TAG).e(t, "reader load failed: id=%d %s", documentId, msg)
                _uiState.value = ReaderUiState.Error(msg)
            }
        }
    }

    fun summarizePage(pageIndex: Int) {
        val doc = currentDocument ?: return
        startSummary(doc.id, SummaryScope.Page(pageIndex))
    }

    fun summarizeFullDocument() {
        val doc = currentDocument ?: return
        startSummary(doc.id, SummaryScope.Full)
    }

    fun stopSummarizing() {
        summaryJob?.cancel()
        summaryJob = null
        updateSummaryState(SummaryState.Idle)
    }

    private fun startSummary(documentId: Long, scope: SummaryScope) {
        if (summaryJob?.isActive == true) return

        updateSummaryState(SummaryState.Loading)
        val accumulated = StringBuilder()
        summaryJob = viewModelScope.launch {
            val model = settingsDataStore.modelName.first()
            val systemPrompt = settingsDataStore.systemPrompt.first()
            try {
                summarizeDocument(
                    documentId = documentId,
                    model = model,
                    scope = scope,
                    systemPrompt = systemPrompt,
                ).collect { token ->
                    accumulated.append(token)
                    updateSummaryState(SummaryState.Streaming(accumulated.toString()))
                }
                updateSummaryState(SummaryState.Done(accumulated.toString()))
            } catch (e: CancellationException) {
                // 用户主动停止：保留已生成的文字
                val partial = accumulated.toString()
                if (partial.isNotBlank()) {
                    updateSummaryState(SummaryState.Done(partial))
                }
            } catch (e: NoChunksForPageException) {
                Timber.tag(TAG).w("no chunks for page")
                updateSummaryState(SummaryState.Error(e.message ?: "当前页无文本内容"))
            } catch (e: NoChunksException) {
                Timber.tag(TAG).w("no chunks at all")
                updateSummaryState(SummaryState.Error(e.message ?: "文档未索引"))
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "summary failed")
                updateSummaryState(SummaryState.Error(e.message ?: "摘要生成失败"))
            } finally {
                summaryJob = null
            }
        }
    }

    private fun updateSummaryState(summaryState: SummaryState) {
        val current = _uiState.value
        if (current is ReaderUiState.Loaded) {
            _uiState.value = current.copy(summaryState = summaryState)
        }
    }

    private companion object {
        const val TAG = "ReaderViewModel"
    }
}
