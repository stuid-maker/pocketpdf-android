package com.asuka.pocketpdf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val llmRepository: LlmRepository,
    private val summaryCacheRepository: SummaryCacheRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var lastTestedUrl: String? = null

    init {
        viewModelScope.launch {
            val baseUrl = settingsDataStore.baseUrl.first()
            val modelName = settingsDataStore.modelName.first()
            val apiKey = settingsDataStore.apiKey.first() ?: ""
            val systemPrompt = settingsDataStore.systemPrompt.first()
            val chunkingStrategy = settingsDataStore.chunkingStrategy.first()
            _uiState.update {
                it.copy(
                    baseUrl = baseUrl,
                    modelName = modelName,
                    apiKey = apiKey,
                    systemPrompt = systemPrompt,
                    chunkingStrategy = chunkingStrategy,
                    selectedPreset = inferPresetId(baseUrl),
                )
            }
        }
    }

    fun onPresetSelected(presetId: String) {
        val preset = MODEL_PRESETS.find { it.id == presetId } ?: return
        if (preset.id == "custom") {
            _uiState.update {
                it.copy(selectedPreset = "custom", saveSuccess = false, confirmPresetId = null)
            }
            return
        }
        val current = _uiState.value
        // 从 custom 切换到其他预设，且用户改了 URL → 弹确认
        if (current.selectedPreset == "custom" && current.baseUrl.isNotBlank() && current.baseUrl != preset.baseUrl) {
            _uiState.update { it.copy(confirmPresetId = presetId) }
            return
        }
        applyPreset(preset)
    }

    private fun applyPreset(preset: ModelPreset) {
        _uiState.update {
            it.copy(
                selectedPreset = preset.id,
                baseUrl = preset.baseUrl,
                // modelName not touched — user's previous selection preserved
                apiKey = preset.apiKey,
                saveSuccess = false,
                confirmPresetId = null,
            )
        }
    }

    fun confirmPresetOverride() {
        val presetId = _uiState.value.confirmPresetId ?: return
        val preset = MODEL_PRESETS.find { it.id == presetId } ?: return
        applyPreset(preset)
    }

    fun cancelPresetOverride() {
        _uiState.update { it.copy(confirmPresetId = null) }
    }

    fun onBaseUrlChanged(url: String) {
        _uiState.update { it.copy(baseUrl = url, saveSuccess = false, connectionTestResult = null, selectedPreset = "custom") }
    }

    fun onModelNameChanged(name: String) {
        _uiState.update { it.copy(modelName = name, saveSuccess = false) }
    }

    fun onApiKeyChanged(key: String) {
        _uiState.update { it.copy(apiKey = key, saveSuccess = false, selectedPreset = "custom") }
    }

    fun onSystemPromptChanged(prompt: String) {
        _uiState.update { it.copy(systemPrompt = prompt, saveSuccess = false) }
    }

    fun onChunkingStrategyChanged(strategy: String) {
        _uiState.update { it.copy(chunkingStrategy = strategy, saveSuccess = false) }
    }

    fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val previousChunkingStrategy = settingsDataStore.chunkingStrategy.first()
                settingsDataStore.setBaseUrl(_uiState.value.baseUrl)
                settingsDataStore.setModelName(_uiState.value.modelName)
                settingsDataStore.setApiKey(
                    _uiState.value.apiKey.ifBlank { null }
                )
                settingsDataStore.setSystemPrompt(_uiState.value.systemPrompt)
                if (previousChunkingStrategy != _uiState.value.chunkingStrategy) {
                    summaryCacheRepository.invalidateAll()
                }
                settingsDataStore.setChunkingStrategy(_uiState.value.chunkingStrategy)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "save settings failed")
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "保存失败")
                }
            }
        }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            try {
                settingsDataStore.resetDefaults()
                _uiState.update {
                    SettingsUiState(
                        baseUrl = SettingsDataStore.DEFAULT_BASE_URL,
                        modelName = SettingsDataStore.DEFAULT_MODEL_NAME,
                        apiKey = "",
                        saveSuccess = true,
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "reset settings failed")
                _uiState.update { it.copy(error = e.message ?: "恢复默认失败") }
            }
        }
    }

    fun testConnection() {
        val url = _uiState.value.baseUrl
        viewModelScope.launch {
            _uiState.update { it.copy(connectionTesting = true, connectionTestResult = null) }
            try {
                when (val result = llmRepository.testConnection(url)) {
                    is Result.Success -> {
                        val names = result.data.map { it.id }
                        _uiState.update {
                            it.copy(
                                connectionTesting = false,
                                connectionTestResult = "✅ 连接成功 · ${names.size} 个模型",
                                availableModels = names,
                            )
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update {
                            it.copy(
                                connectionTesting = false,
                                connectionTestResult = "❌ ${result.error.message}",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionTesting = false,
                        connectionTestResult = "❌ 连接失败: ${e.message}",
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"

        private fun inferPresetId(baseUrl: String): String =
            MODEL_PRESETS.firstOrNull { preset ->
                preset.id != "custom" && preset.baseUrl.equals(baseUrl, ignoreCase = true)
            }?.id ?: "custom"
    }
}
