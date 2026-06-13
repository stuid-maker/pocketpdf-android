package com.asuka.pocketpdf.ui.chat

import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.Conversation
import com.asuka.pocketpdf.domain.repository.ChatRepository
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.usecase.AskDocumentUseCase
import com.asuka.pocketpdf.domain.usecase.FullDocumentProgress
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelProgressTest {

    private val dispatcher = StandardTestDispatcher()
    private val askDocument: AskDocumentUseCase = mockk()
    private val settingsDataStore: SettingsDataStore = mockk()
    private val chatRepository: ChatRepository = mockk()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsDataStore.modelName } returns flowOf("test-model")
        every { settingsDataStore.systemPrompt } returns flowOf("")
        val conversation = Conversation(CONV_ID, DOC_ID, "对话 1", 1L, 1L)
        every { chatRepository.observeConversations(DOC_ID) } returns flowOf(listOf(conversation))
        coEvery { chatRepository.getConversations(DOC_ID) } returns listOf(conversation)
        every { chatRepository.observeMessages(any()) } returns emptyFlow()
        coEvery { chatRepository.saveMessage(any(), any()) } returns Unit
        coEvery { chatRepository.getHistorySnapshot(any()) } returns emptyList()

        viewModel = ChatViewModel(askDocument, settingsDataStore, chatRepository, mockk(relaxed = true))
        viewModel.elapsedRealtime = { NOW_MS }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `full document chat passes onProgress to askDocument`() = runTest(dispatcher) {
        // onProgress is the 7th parameter (index 6) after history (index 5)
        var capturedCallback: ((FullDocumentProgress) -> Unit)? = null
        coEvery {
            askDocument(
                documentId = any(),
                question = any(),
                model = any(),
                topK = any(),
                systemPrompt = any(),
                history = any(),
                onProgress = any(),
            )
        } answers {
            capturedCallback = arg<(FullDocumentProgress) -> Unit>(6)
            capturedCallback?.invoke(FullDocumentProgress.Mapping(1, 3))
            flowOf("全文总结")
        }

        viewModel.load(DOC_ID)
        runCurrent()
        viewModel.onInputChanged("总结全文")
        viewModel.sendMessage()
        runCurrent()

        assertNotNull("onProgress callback should have been captured", capturedCallback)

        val messages = viewModel.uiState.value.messages
        val assistantMsg = messages.last { it.role == ChatRole.ASSISTANT }
        assertTrue("content should contain streamed tokens", assistantMsg.content.contains("全文总结"))
        assertTrue("streaming should be complete", !assistantMsg.isStreaming)
    }

    @Test
    fun `ordinary token stream works without progress events`() = runTest(dispatcher) {
        coEvery {
            askDocument(any(), any(), any(), any(), any(), any(), any())
        } returns flowOf("答案")

        viewModel.load(DOC_ID)
        runCurrent()
        viewModel.onInputChanged("金额是多少")
        viewModel.sendMessage()
        runCurrent()

        val messages = viewModel.uiState.value.messages
        val assistantMsg = messages.last { it.role == ChatRole.ASSISTANT }

        assertNull("progress should be null when no onProgress events", assistantMsg.progress)
        assertEquals("答案", assistantMsg.content)
    }

    @Test
    fun `stop clears active progress immediately`() = runTest(dispatcher) {
        coEvery {
            askDocument(any(), any(), any(), any(), any(), any(), any())
        } answers {
            val onProgress = arg<(FullDocumentProgress) -> Unit>(6)
            onProgress(FullDocumentProgress.Mapping(1, 3))
            flow { awaitCancellation() }
        }

        viewModel.load(DOC_ID)
        runCurrent()
        viewModel.onInputChanged("总结全文")
        viewModel.sendMessage()
        runCurrent()
        assertNotNull(viewModel.uiState.value.messages.last().progress)

        viewModel.stopGenerating()

        assertNull(viewModel.uiState.value.messages.last().progress)
    }

    private companion object {
        const val NOW_MS = 1_000_000L
        const val DOC_ID = 1L
        const val CONV_ID = 100L
    }
}
