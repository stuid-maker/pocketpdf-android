package com.asuka.pocketpdf.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.usecase.GetDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getDocument: GetDocumentUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

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

    private companion object {
        const val TAG = "ReaderViewModel"
    }
}
