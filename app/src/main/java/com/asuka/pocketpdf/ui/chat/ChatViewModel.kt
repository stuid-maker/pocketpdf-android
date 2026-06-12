package com.asuka.pocketpdf.ui.chat

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import com.asuka.pocketpdf.domain.repository.ChatRepository
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.usecase.AskDocumentUseCase
import com.asuka.pocketpdf.ui.ai.GenerationProgressEstimator
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
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    /** Overridable in tests to control progress timing. Default: realtime clock. */
    var elapsedRealtime: () -> Long = SystemClock::elapsedRealtime

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
    private var lastFailedQuestion: String? = null

    override fun onCleared() {
        generateJob?.cancel()
        historyJob?.cancel()
        super.onCleared()
    }

    fun load(documentId: Long) {
        if (this.documentId == documentId) return
        this.documentId = documentId
        localMessageCounter = LOCAL_ID_OFFSET

        // Load page count for citation validation
        viewModelScope.launch {
            val doc = documentRepository.getDocument(documentId)
            _uiState.update { it.copy(pageCount = doc?.pageCount ?: 0) }
        }

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

        val aiMsgId = ++localMessageCounter
        val placeholder = ChatDisplayMessage(
            id = aiMsgId,
            role = ChatRole.ASSISTANT,
            content = "",
            isStreaming = true,
        )
        _uiState.update { it.copy(messages = it.messages + placeholder) }

        generateJob = viewModelScope.launch {
            val history = chatRepository.getHistorySnapshot(documentId)
            try {
                chatRepository.saveMessage(documentId, ChatMessage(ChatMessage.ROLE_USER, text))
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "save user message failed")
            }
            val progressEstimator = GenerationProgressEstimator()
            try {
                val model = settingsDataStore.modelName.first()
                val systemPrompt = settingsDataStore.systemPrompt.first()
                askDocument(
                    documentId, text, model,
                    systemPrompt = systemPrompt,
                    history = history,
                    onProgress = { event ->
                        val display = progressEstimator.update(event, elapsedRealtime())
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == aiMsgId && msg.isStreaming)
                                    msg.copy(progress = display)
                                else msg
                            })
                        }
                    },
                ).collect { token ->
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
                            if (msg.id == aiMsgId) msg.copy(isStreaming = false, progress = null)
                            else msg
                        },
                    )
                }
                lastFailedQuestion = null
                saveAiToDb(aiMsgId)
            } catch (e: CancellationException) {
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMsgId) msg.copy(isStreaming = false, progress = null)
                            else msg
                        },
                    )
                }
                saveAiToDb(aiMsgId)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "chat generation failed")
                lastFailedQuestion = text
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        error = userFriendlyError(e),
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMsgId) msg.copy(isStreaming = false, progress = null)
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
                    if (msg.isStreaming) {
                        msg.copy(isStreaming = false, progress = null)
                    } else {
                        msg
                    }
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retry(assistantMessageId: Long) {
        val messages = _uiState.value.messages
        val assistantIndex = messages.indexOfFirst {
            it.id == assistantMessageId && it.role == ChatRole.ASSISTANT
        }
        val question = if (assistantIndex > 0) {
            messages.subList(0, assistantIndex)
                .lastOrNull { it.role == ChatRole.USER }
                ?.content
        } else {
            null
        }
        if (question.isNullOrBlank()) {
            _uiState.update { it.copy(error = REGENERATE_QUESTION_NOT_FOUND_ERROR) }
            return
        }

        _uiState.update { it.copy(inputText = question, error = null) }
        sendMessage()
    }

    fun retryLastFailure() {
        val question = lastFailedQuestion ?: return
        lastFailedQuestion = null
        _uiState.update { it.copy(inputText = question, error = null) }
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
                _uiState.update { it.copy(error = "保存AI回复失败") }
            }
        }
    }

    companion object {
        const val TAG = "ChatViewModel"
        const val REGENERATE_QUESTION_NOT_FOUND_ERROR = "找不到该回答对应的问题，无法重新生成"
        /** 本地临时消息的 ID 起始偏移。DB 自增 ID 通常 < 10⁶，此偏移保证不冲突。 */
        private const val LOCAL_ID_OFFSET = 1_000_000_000L

        /**
         * 将异常转换为用户可读的错误文案。
         * 避免把 HTTP 响应 JSON、堆栈信息等直接展示给用户。
         */
        private fun userFriendlyError(e: Exception): String = when {
            e is CancellationException -> "已取消"
            e is java.net.SocketTimeoutException -> "请求超时，请检查网络"
            e is java.net.ConnectException -> "无法连接服务器，请检查地址和网络"
            e is java.net.UnknownHostException -> "无法解析服务器地址"
            e is javax.net.ssl.SSLException -> "SSL 连接失败，请检查证书配置"
            e.message?.contains("HTTP 4") == true -> "请求错误（4xx），请检查 API Key"
            e.message?.contains("HTTP 5") == true -> "服务器错误（5xx），请稍后重试"
            else -> "生成回答失败，请重试"
        }
    }
}
