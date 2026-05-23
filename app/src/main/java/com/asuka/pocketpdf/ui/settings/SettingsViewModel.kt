package com.asuka.pocketpdf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.domain.repository.LlmRepository
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
                it.copy(baseUrl = baseUrl, modelName = modelName, apiKey = apiKey, systemPrompt = systemPrompt, chunkingStrategy = chunkingStrategy)
            }
        }
    }

    fun onPresetSelected(presetId: String) {
        val preset = MODEL_PRESETS.find { it.id == presetId } ?: return
        _uiState.update {
            it.copy(
                selectedPreset = presetId,
                baseUrl = preset.baseUrl,
                modelName = preset.modelName,
                apiKey = preset.apiKey,
                saveSuccess = false,
            )
        }
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
                settingsDataStore.setBaseUrl(_uiState.value.baseUrl)
                settingsDataStore.setModelName(_uiState.value.modelName)
                settingsDataStore.setApiKey(
                    _uiState.value.apiKey.ifBlank { null }
                )
                settingsDataStore.setSystemPrompt(_uiState.value.systemPrompt)
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
        if (url == lastTestedUrl && _uiState.value.connectionTestResult != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(connectionTesting = true, connectionTestResult = null) }
            lastTestedUrl = url
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
    }
}
