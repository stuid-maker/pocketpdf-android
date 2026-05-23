package com.asuka.pocketpdf.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.usecase.AskDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val askDocument: AskDocumentUseCase,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messageIdCounter = 0L
    private var generateJob: Job? = null
    private var documentId: Long = -1L
    private var lastQuestion: String = ""

    fun load(documentId: Long) {
        if (this.documentId == documentId) return
        this.documentId = documentId
        _uiState.value = ChatUiState()
        messageIdCounter = 0
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating || documentId <= 0) return

        lastQuestion = text

        val userMsg = ChatDisplayMessage(
            id = ++messageIdCounter,
            role = ChatRole.USER,
            content = text,
        )
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            inputText = "",
            isGenerating = true,
            error = null,
        ) }

        val aiMsgId = ++messageIdCounter
        val placeholder = ChatDisplayMessage(
            id = aiMsgId,
            role = ChatRole.ASSISTANT,
            content = "",
            isStreaming = true,
        )
        _uiState.update { it.copy(messages = it.messages + placeholder) }

        generateJob = viewModelScope.launch {
            try {
                val model = settingsDataStore.modelName.first()
                val systemPrompt = settingsDataStore.systemPrompt.first()
                askDocument(documentId, text, model, systemPrompt = systemPrompt).collect { token ->
                    _uiState.update { state ->
                        val msgs = state.messages.map { msg ->
                            if (msg.id == aiMsgId) msg.copy(content = msg.content + token)
                            else msg
                        }
                        state.copy(messages = msgs)
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMsgId) msg.copy(isStreaming = false)
                            else msg
                        },
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMsgId) msg.copy(isStreaming = false)
                            else msg
                        },
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "chat generation failed")
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        error = e.message ?: "生成回答失败",
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMsgId) msg.copy(isStreaming = false)
                            else msg
                        },
                    )
                }
            }
        }
    }

    fun stopGenerating() {
        generateJob?.cancel()
        generateJob = null
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                messages = state.messages.map { msg ->
                    if (msg.isStreaming) msg.copy(isStreaming = false) else msg
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retry() {
        if (lastQuestion.isBlank()) return
        val q = lastQuestion
        lastQuestion = ""
        _uiState.update { it.copy(inputText = q, error = null) }
        sendMessage()
    }

    companion object {
        const val TAG = "ChatViewModel"
    }
}
