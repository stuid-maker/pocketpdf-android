package com.asuka.pocketpdf.ui.settings

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.model.LlmModel
import com.asuka.pocketpdf.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

/**
 * [SettingsViewModel] 单元测试，覆盖预设切换、测试连接等关键路径。
 *
 * 使用 StandardTestDispatcher 精确控制协程时序，不依赖 Robolectric。
 * SettingsDataStore 和 LlmRepository 均通过 mockk 注入。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dataStore: SettingsDataStore
    private lateinit var llmRepository: LlmRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        dataStore = mockk<SettingsDataStore>()
        llmRepository = mockk()

        every { dataStore.baseUrl } returns flowOf("http://localhost:1234/v1")
        every { dataStore.modelName } returns flowOf("gemma-3-4b")
        every { dataStore.apiKey } returns flowOf("")
        every { dataStore.systemPrompt } returns flowOf("")
        every { dataStore.chunkingStrategy } returns flowOf("sliding_window")

        viewModel = SettingsViewModel(dataStore, llmRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 每个测试前等待 ViewModel.init 协程完成。
     * runTest(dispatcher) 内部调用。
     */
    private fun TestScope.awaitInit() {
        runCurrent()
    }

    // ──────────────────────────────────────────────
    // 预设切换
    // ──────────────────────────────────────────────

    @Test
    fun `preset switch preserves modelName`() = runTest(dispatcher) {
        awaitInit()
        // 先给 modelName 设一个自定义值
        viewModel.onModelNameChanged("my-model")
        assertEquals("my-model", viewModel.uiState.value.modelName)

        // 切到 lmstudio 预设
        viewModel.onPresetSelected("lmstudio")
        runCurrent()

        // modelName 不应被覆盖（applyPreset 不 touch modelName）
        assertEquals("my-model", viewModel.uiState.value.modelName)
    }

    @Test
    fun `preset switch fills baseUrl automatically`() = runTest(dispatcher) {
        awaitInit()
        // 选中 lmstudio 预设 → baseUrl 应设为预设的 baseUrl
        viewModel.onPresetSelected("lmstudio")
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("lmstudio", state.selectedPreset)
        assertEquals("http://localhost:1234/v1", state.baseUrl)
    }

    @Test
    fun `preset switch preserves user apiKey`() = runTest(dispatcher) {
        awaitInit()
        // 用户已经填了 key
        viewModel.onApiKeyChanged("sk-my-secret-key")
        assertEquals("sk-my-secret-key", viewModel.uiState.value.apiKey)

        // 切到 deepseek（预设默认 apiKey 为空）
        viewModel.onPresetSelected("deepseek")
        runCurrent()

        // 用户填的 key 不应该被覆盖
        assertEquals("sk-my-secret-key", viewModel.uiState.value.apiKey)
    }

    @Test
    fun `custom preset with modified URL shows confirmation dialog`() = runTest(dispatcher) {
        awaitInit()
        // init 后 selectedPreset 为 "custom"（默认值）
        // 改 baseUrl 使其不同于目标预设的 baseUrl
        viewModel.onBaseUrlChanged("http://my-custom-url:8080/v1")
        runCurrent()
        assertEquals("custom", viewModel.uiState.value.selectedPreset)

        // 切到 deepseek → 目标 baseUrl = "https://api.deepseek.com/v1"，
        // 当前 baseUrl = "http://my-custom-url:8080/v1" 且 selectedPreset = "custom"
        // → 应弹确认窗，confirmPresetId 非空
        viewModel.onPresetSelected("deepseek")
        runCurrent()

        assertNotNull("confirmPresetId should be non-null", viewModel.uiState.value.confirmPresetId)
        assertEquals("deepseek", viewModel.uiState.value.confirmPresetId)
        // baseUrl 尚未被覆盖
        assertEquals("http://my-custom-url:8080/v1", viewModel.uiState.value.baseUrl)
    }

    @Test
    fun `direct preset switch without URL modification skips confirmation`() = runTest(dispatcher) {
        awaitInit()
        // 先在 lmstudio 预设下（直接切，不是从 custom 手动改 URL）
        viewModel.onPresetSelected("lmstudio")
        runCurrent()
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)

        // 直接切到 deepseek → 当前 selectedPreset = "lmstudio"（不是 "custom"）
        // → 不会触发确认，直接应用
        viewModel.onPresetSelected("deepseek")
        runCurrent()

        assertNull("confirmPresetId should be null", viewModel.uiState.value.confirmPresetId)
        assertEquals("deepseek", viewModel.uiState.value.selectedPreset)
        assertEquals("https://api.deepseek.com/v1", viewModel.uiState.value.baseUrl)
    }

    @Test
    fun `confirmPresetOverride applies the preset`() = runTest(dispatcher) {
        awaitInit()
        // 先模拟触发确认弹窗：custom 状态下改 URL 再切预设
        viewModel.onBaseUrlChanged("http://my-custom-url:8080/v1")
        runCurrent()
        viewModel.onPresetSelected("deepseek")
        runCurrent()
        assertEquals("deepseek", viewModel.uiState.value.confirmPresetId)

        // 确认覆写
        viewModel.confirmPresetOverride()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("deepseek", state.selectedPreset)
        assertEquals("https://api.deepseek.com/v1", state.baseUrl)
        assertNull("confirmPresetId should be cleared after confirm", state.confirmPresetId)
    }

    @Test
    fun `cancelPresetOverride clears confirmPresetId`() = runTest(dispatcher) {
        awaitInit()
        // 触发确认弹窗
        viewModel.onBaseUrlChanged("http://my-custom-url:8080/v1")
        runCurrent()
        viewModel.onPresetSelected("deepseek")
        runCurrent()
        assertNotNull(viewModel.uiState.value.confirmPresetId)

        // 取消覆写
        viewModel.cancelPresetOverride()
        runCurrent()

        assertNull("confirmPresetId should be cleared after cancel", viewModel.uiState.value.confirmPresetId)
        // selectedPreset 应仍为 custom，baseUrl 不变
        assertEquals("custom", viewModel.uiState.value.selectedPreset)
        assertEquals("http://my-custom-url:8080/v1", viewModel.uiState.value.baseUrl)
    }

    // ──────────────────────────────────────────────
    // baseUrl / modelName 变更
    // ──────────────────────────────────────────────

    @Test
    fun `onBaseUrlChanged clears preset and connection result`() = runTest(dispatcher) {
        awaitInit()
        // 先切到 lmstudio（baseUrl 与 init 值相同，不弹确认）
        viewModel.onPresetSelected("lmstudio")
        runCurrent()
        assertEquals("lmstudio", viewModel.uiState.value.selectedPreset)
        // 再从 lmstudio 切到 deepseek（selectedPreset != "custom"，不弹确认）
        viewModel.onPresetSelected("deepseek")
        runCurrent()
        assertEquals("deepseek", viewModel.uiState.value.selectedPreset)

        // 调用 testConnection 设一个连接结果（mock 成功）
        coEvery { llmRepository.testConnection(any()) } returns Result.Success(
            listOf(LlmModel("gemma-3-4b", "google"))
        )
        viewModel.testConnection()
        runCurrent()
        assertNotNull("connectionTestResult should be set", viewModel.uiState.value.connectionTestResult)

        // 改 baseUrl → selectedPreset 应重置为 "custom"，connectionTestResult 清空
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
