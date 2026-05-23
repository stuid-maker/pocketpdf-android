package com.asuka.pocketpdf.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import com.asuka.pocketpdf.domain.repository.ChatRepository
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
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * 用于为尚未持久化的消息（新用户消息、AI 占位符）分配临时 ID。
     * 从 [LOCAL_ID_OFFSET] 递增以避免与 DB 自动生成的 ID（通常 < 10⁶）冲突。
     * 过滤时根据 ID >= LOCAL_ID_OFFSET 判断是否为本地消息。
     */
    private var localMessageCounter = LOCAL_ID_OFFSET
    private var generateJob: Job? = null
    private var historyJob: Job? = null
    private var documentId: Long = -1L
    private var lastQuestion: String = ""

    override fun onCleared() {
        generateJob?.cancel()
        historyJob?.cancel()
        super.onCleared()
    }

    fun load(documentId: Long) {
        if (this.documentId == documentId) return
        this.documentId = documentId
        localMessageCounter = LOCAL_ID_OFFSET

        // Cancel old history collection before starting new one
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            chatRepository.observeMessages(documentId).collect { dbMessages ->
                _uiState.update { state ->
                    val dbDisplayMessages = dbMessages.map { msg ->
                        ChatDisplayMessage(
                            id = msg.id,
                            role = if (msg.role == StoredChatMessage.ROLE_USER) ChatRole.USER else ChatRole.ASSISTANT,
                            content = msg.content,
                            isStreaming = false,
                        )
                    }
                    // Keep local-only messages (streaming placeholder, unsaved user msg)
                    // that don't yet have a DB counterpart
                    val dbIds = dbDisplayMessages.map { it.id }.toSet()
                    val localOnlyMessages = state.messages.filter { localMsg ->
                        localMsg.id !in dbIds &&
                        dbDisplayMessages.none { dbMsg ->
                            dbMsg.content == localMsg.content && dbMsg.role == localMsg.role
                        }
                    }
                    state.copy(messages = dbDisplayMessages + localOnlyMessages)
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating || documentId <= 0) return

        lastQuestion = text

        val userMsgId = ++localMessageCounter
        val userMsg = ChatDisplayMessage(
            id = userMsgId,
            role = ChatRole.USER,
            content = text,
        )
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            inputText = "",
            isGenerating = true,
            error = null,
        ) }

        // Save user message to DB with error handling
        viewModelScope.launch {
            try {
                chatRepository.saveMessage(documentId, ChatMessage(ChatMessage.ROLE_USER, text))
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "save user message failed")
                _uiState.update { it.copy(error = "保存消息失败: ${e.message}") }
            }
        }

        val aiMsgId = ++localMessageCounter
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
                saveAiToDb(aiMsgId)
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
                saveAiToDb(aiMsgId)
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
                saveAiToDb(aiMsgId)
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

    private fun saveAiToDb(aiMsgId: Long) {
        viewModelScope.launch {
            try {
                val aiContent = _uiState.value.messages.find { it.id == aiMsgId }?.content ?: ""
                if (aiContent.isNotBlank()) {
                    chatRepository.saveMessage(documentId, ChatMessage(ChatMessage.ROLE_ASSISTANT, aiContent))
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "save AI message failed")
                _uiState.update { it.copy(error = "保存AI回复失败: ${e.message}") }
            }
        }
    }

    companion object {
        const val TAG = "ChatViewModel"
        /** 本地临时消息的 ID 起始偏移。DB 自增 ID 通常 < 10⁶，此偏移保证不冲突。 */
        private const val LOCAL_ID_OFFSET = 1_000_000_000L
    }
}
