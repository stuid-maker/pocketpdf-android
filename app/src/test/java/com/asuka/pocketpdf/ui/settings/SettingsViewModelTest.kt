package com.asuka.pocketpdf.ui.settings

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.LlmModel
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: SettingsDataStore
    private lateinit var llmRepository: LlmRepository
    private lateinit var summaryCacheRepository: SummaryCacheRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        dataStore = mockk<SettingsDataStore>()
        llmRepository = mockk()
        summaryCacheRepository = mockk(relaxUnitFun = true)

        every { dataStore.baseUrl } returns flowOf("http://localhost:1234/v1")
        every { dataStore.modelName } returns flowOf("gemma-3-4b")
        every { dataStore.apiKey } returns flowOf("")
        every { dataStore.systemPrompt } returns flowOf("")
        every { dataStore.chunkingStrategy } returns flowOf("sliding_window")

        viewModel = SettingsViewModel(dataStore, llmRepository, summaryCacheRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.awaitInit() {
        runCurrent()
    }

    // ──────────────────────────────────────────────
    // 预设切换
    // ──────────────────────────────────────────────

    @Test
    fun `preset switch preserves modelName`() = runTest(dispatcher) {
        awaitInit()
        viewModel.onModelNameChanged("my-model")
        assertEquals("my-model", viewModel.uiState.value.modelName)

        viewModel.onPresetSelected("lmstudio")
        runCurrent()

        assertEquals("my-model", viewModel.uiState.value.modelName)
    }

    @Test
    fun `preset switch fills baseUrl automatically`() = runTest(dispatcher) {
        awaitInit()
        viewModel.onPresetSelected("lmstudio")
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("lmstudio", state.selectedPreset)
        assertEquals("http://localhost:1234/v1", state.baseUrl)
    }

    @Test
    fun `preset switch preserves user apiKey`() = runTest(dispatcher) {
        awaitInit()
        viewModel.onApiKeyChanged("***")
        assertEquals("***", viewModel.uiState.value.apiKey)

        // deepseek is a cloud preset — will trigger cloud confirmation dialog
        viewModel.onPresetSelected("deepseek")
        runCurrent()

        // cloud preset: confirmCloudPresetId should be set, baseUrl not changed yet
        assertEquals("deepseek", viewModel.uiState.value.confirmCloudPresetId)
        assertEquals("***", viewModel.uiState.value.apiKey)
    }

    @Test
    fun `cloud preset shows cloud confirmation dialog`() = runTest(dispatcher) {
        awaitInit()
        // ViewModel initializes to lmstudio because baseUrl matches
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)

        viewModel.onPresetSelected("deepseek")
        runCurrent()

        assertNotNull("confirmCloudPresetId should be non-null", viewModel.uiState.value.confirmCloudPresetId)
        assertEquals("deepseek", viewModel.uiState.value.confirmCloudPresetId)
        // baseUrl not overwritten until confirmed
        assertEquals("http://localhost:1234/v1", viewModel.uiState.value.baseUrl)
    }

    @Test
    fun `local preset switch skips cloud confirmation`() = runTest(dispatcher) {
        awaitInit()
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)
        // Already lmstudio, switch to lmstudio again
        viewModel.onPresetSelected("lmstudio")
        runCurrent()

        assertNull("confirmCloudPresetId should be null for local presets", viewModel.uiState.value.confirmCloudPresetId)
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)
    }

    @Test
    fun `confirmCloudPreset applies the cloud preset`() = runTest(dispatcher) {
        awaitInit()
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)
        viewModel.onPresetSelected("deepseek")
        runCurrent()
        assertEquals("deepseek", viewModel.uiState.value.confirmCloudPresetId)

        viewModel.confirmCloudPreset()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("deepseek", state.selectedPreset)
        assertEquals("https://api.deepseek.com/v1", state.baseUrl)
        assertNull("confirmCloudPresetId should be cleared", state.confirmCloudPresetId)
    }

    @Test
    fun `cancelCloudPreset clears confirmCloudPresetId`() = runTest(dispatcher) {
        awaitInit()
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)
        viewModel.onPresetSelected("deepseek")
        runCurrent()
        assertNotNull(viewModel.uiState.value.confirmCloudPresetId)

        viewModel.cancelCloudPreset()
        runCurrent()

        assertNull("confirmCloudPresetId should be cleared", viewModel.uiState.value.confirmCloudPresetId)
        // selectedPreset should still be lmstudio (unchanged by cancel)
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)
    }

    @Test
    fun `custom preset with modified URL shows confirm dialog for non-cloud preset`() = runTest(dispatcher) {
        awaitInit()
        viewModel.onBaseUrlChanged("http://my-custom-url:8080/v1")
        runCurrent()
        assertEquals("custom", viewModel.uiState.value.selectedPreset)

        // Switch to lmstudio (local, non-cloud) with different URL → confirmPresetId
        viewModel.onPresetSelected("lmstudio")
        runCurrent()

        assertNotNull("confirmPresetId should be non-null after URL mismatch", viewModel.uiState.value.confirmPresetId)
        assertEquals("lmstudio", viewModel.uiState.value.confirmPresetId)
        assertEquals("http://my-custom-url:8080/v1", viewModel.uiState.value.baseUrl)
    }

    @Test
    fun `confirmPresetOverride applies the preset`() = runTest(dispatcher) {
        awaitInit()
        viewModel.onBaseUrlChanged("http://my-custom-url:8080/v1")
        runCurrent()
        viewModel.onPresetSelected("lmstudio")
        runCurrent()
        assertEquals("lmstudio", viewModel.uiState.value.confirmPresetId)

        viewModel.confirmPresetOverride()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("lmstudio", state.selectedPreset)
        assertEquals("http://localhost:1234/v1", state.baseUrl)
        assertNull("confirmPresetId should be cleared", state.confirmPresetId)
    }

    @Test
    fun `cancelPresetOverride clears confirmPresetId`() = runTest(dispatcher) {
        awaitInit()
        viewModel.onBaseUrlChanged("http://my-custom-url:8080/v1")
        runCurrent()
        viewModel.onPresetSelected("lmstudio")
        runCurrent()
        assertNotNull(viewModel.uiState.value.confirmPresetId)

        viewModel.cancelPresetOverride()
        runCurrent()

        assertNull("confirmPresetId should be cleared", viewModel.uiState.value.confirmPresetId)
        assertEquals("custom", viewModel.uiState.value.selectedPreset)
        assertEquals("http://my-custom-url:8080/v1", viewModel.uiState.value.baseUrl)
    }

    // ──────────────────────────────────────────────
    // baseUrl / modelName 变更
    // ──────────────────────────────────────────────

    @Test
    fun `onBaseUrlChanged clears preset and connection result`() = runTest(dispatcher) {
        awaitInit()
        // Use lmstudio (local) to avoid cloud confirmation
        viewModel.onPresetSelected("lmstudio")
        runCurrent()
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)

        coEvery { llmRepository.testConnection(any()) } returns Result.Success(
            listOf(LlmModel("gemma-3-4b", "google"))
        )
        viewModel.testConnection()
        runCurrent()
        assertNotNull("connectionTestResult should be set", viewModel.uiState.value.connectionTestResult)

        viewModel.onBaseUrlChanged("new-url")
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("new-url", state.baseUrl)
        assertEquals("custom", state.selectedPreset)
        assertNull("connectionTestResult should be cleared", state.connectionTestResult)
    }

    @Test
    fun `onModelNameChanged updates modelName`() = runTest(dispatcher) {
        awaitInit()
        assertEquals("gemma-3-4b", viewModel.uiState.value.modelName)

        viewModel.onModelNameChanged("llama-3-8b")
        runCurrent()

        assertEquals("llama-3-8b", viewModel.uiState.value.modelName)
    }

    @Test
    fun `save invalidates summaries when chunking strategy changes`() = runTest(dispatcher) {
        awaitInit()
        coEvery { dataStore.setBaseUrl(any()) } returns Unit
        coEvery { dataStore.setModelName(any()) } returns Unit
        coEvery { dataStore.setApiKey(any()) } returns Unit
        coEvery { dataStore.setSystemPrompt(any()) } returns Unit
        coEvery { dataStore.setChunkingStrategy(any()) } returns Unit
        viewModel.onChunkingStrategyChanged(SettingsDataStore.STRATEGY_PARAGRAPH)

        viewModel.save()
        runCurrent()

        coVerify(exactly = 1) { summaryCacheRepository.invalidateAll() }
    }

    @Test
    fun `save preserves summaries when chunking strategy is unchanged`() = runTest(dispatcher) {
        awaitInit()
        coEvery { dataStore.setBaseUrl(any()) } returns Unit
        coEvery { dataStore.setModelName(any()) } returns Unit
        coEvery { dataStore.setApiKey(any()) } returns Unit
        coEvery { dataStore.setSystemPrompt(any()) } returns Unit
        coEvery { dataStore.setChunkingStrategy(any()) } returns Unit

        viewModel.save()
        runCurrent()

        coVerify(exactly = 0) { summaryCacheRepository.invalidateAll() }
    }

    // ──────────────────────────────────────────────
    // 测试连接
    // ──────────────────────────────────────────────

    @Test
    fun `testConnection success populates availableModels`() = runTest(dispatcher) {
        awaitInit()
        val models = listOf(
            LlmModel("gemma-3-4b", "google"),
            LlmModel("deepseek-coder", "deepseek"),
        )
        coEvery { llmRepository.testConnection(any()) } returns Result.Success(models)

        viewModel.testConnection()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf("gemma-3-4b", "deepseek-coder"), state.availableModels)
        assertEquals("✅ 连接成功 · 2 个模型", state.connectionTestResult)
        assertEquals(false, state.connectionTesting)
    }

    @Test
    fun `testConnection failure sets error result`() = runTest(dispatcher) {
        awaitInit()
        coEvery { llmRepository.testConnection(any()) } returns Result.Failure(
            Exception("Connection refused")
        )

        viewModel.testConnection()
        runCurrent()

        val result = viewModel.uiState.value.connectionTestResult
        assertNotNull("connectionTestResult should not be null", result)
        assertTrue("result should start with ❌", result!!.startsWith("❌"))
        assertTrue("result should contain error message", result.contains("Connection refused"))
        assertEquals(false, viewModel.uiState.value.connectionTesting)
    }
}
