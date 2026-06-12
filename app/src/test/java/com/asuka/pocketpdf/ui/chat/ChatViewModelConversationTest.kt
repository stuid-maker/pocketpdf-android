package com.asuka.pocketpdf.ui.chat

import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import com.asuka.pocketpdf.domain.repository.ChatRepository
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.usecase.AskDocumentUseCase
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelConversationTest {

    private val dispatcher = StandardTestDispatcher()
    private val askDocument: AskDocumentUseCase = mockk()
    private val settingsDataStore: SettingsDataStore = mockk()
    private val chatRepository: ChatRepository = mockk()
    private val documentRepository: DocumentRepository = mockk(relaxed = true)
    private val storedMessages = MutableStateFlow<List<StoredChatMessage>>(emptyList())
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsDataStore.modelName } returns flowOf("test-model")
        every { settingsDataStore.systemPrompt } returns flowOf("")
        every { chatRepository.observeMessages(any()) } returns storedMessages
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        coEvery { chatRepository.getHistorySnapshot(any()) } returns emptyList()
        coEvery {
            askDocument(any(), any(), any(), any(), any(), any(), any())
        } returns flowOf("answer")

        viewModel = ChatViewModel(
            askDocument,
            settingsDataStore,
            chatRepository,
            documentRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `send snapshots prior history before saving current question`() = runTest(dispatcher) {
        val priorHistory = listOf(
            StoredChatMessage(1L, StoredChatMessage.ROLE_USER, "earlier question"),
            StoredChatMessage(2L, StoredChatMessage.ROLE_ASSISTANT, "earlier answer"),
        )
        coEvery { chatRepository.getHistorySnapshot(7L) } returns priorHistory

        viewModel.load(7L)
        viewModel.onInputChanged("current question")
        viewModel.sendMessage()
        runCurrent()

        coVerifyOrder {
            chatRepository.getHistorySnapshot(7L)
            chatRepository.saveMessage(
                7L,
                ChatMessage(ChatMessage.ROLE_USER, "current question"),
            )
            askDocument(
                documentId = 7L,
                question = "current question",
                model = "test-model",
                topK = any(),
                systemPrompt = "",
                history = priorHistory,
                onProgress = any(),
            )
        }
    }

    @Test
    fun `retry selected assistant uses nearest preceding user question`() = runTest(dispatcher) {
        storedMessages.value = listOf(
            StoredChatMessage(10L, StoredChatMessage.ROLE_USER, "first question"),
            StoredChatMessage(11L, StoredChatMessage.ROLE_ASSISTANT, "first answer"),
            StoredChatMessage(12L, StoredChatMessage.ROLE_USER, "second question"),
            StoredChatMessage(13L, StoredChatMessage.ROLE_ASSISTANT, "second answer"),
        )

        viewModel.load(7L)
        runCurrent()
        viewModel.retry(11L)
        runCurrent()

        coVerifyOrder {
            chatRepository.getHistorySnapshot(7L)
            chatRepository.saveMessage(
                7L,
                ChatMessage(ChatMessage.ROLE_USER, "first question"),
            )
            askDocument(
                documentId = 7L,
                question = "first question",
                model = "test-model",
                topK = any(),
                systemPrompt = "",
                history = any(),
                onProgress = any(),
            )
        }
        assertTrue(viewModel.uiState.value.messages.any {
            it.role == ChatRole.USER && it.content == "first question" && it.id > 1_000_000_000L
        })
    }

    @Test
    fun `retry reports error when selected assistant has no preceding user`() = runTest(dispatcher) {
        storedMessages.value = listOf(
            StoredChatMessage(20L, StoredChatMessage.ROLE_ASSISTANT, "orphan answer"),
        )

        viewModel.load(7L)
        runCurrent()
        viewModel.retry(20L)
        runCurrent()

        assertEquals("找不到该回答对应的问题，无法重新生成", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `retry reports error when selected message is not an assistant response`() = runTest(dispatcher) {
        storedMessages.value = listOf(
            StoredChatMessage(30L, StoredChatMessage.ROLE_USER, "question"),
        )

        viewModel.load(7L)
        runCurrent()
        viewModel.retry(30L)
        runCurrent()

        assertEquals("找不到该回答对应的问题，无法重新生成", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `retry last failure resubmits only the failed question`() = runTest(dispatcher) {
        coEvery {
            askDocument(
                documentId = any(),
                question = "failed question",
                model = any(),
                topK = any(),
                systemPrompt = any(),
                history = any(),
                onProgress = any(),
            )
        } returns flow { throw IllegalStateException("boom") } andThen flowOf("recovered")

        viewModel.load(7L)
        viewModel.onInputChanged("failed question")
        viewModel.sendMessage()
        runCurrent()
        assertTrue(viewModel.uiState.value.error != null)

        viewModel.retryLastFailure()
        runCurrent()

        assertEquals(null, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.messages.any {
            it.role == ChatRole.ASSISTANT && it.content == "recovered"
        })
    }
}
