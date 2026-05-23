package com.asuka.pocketpdf.ui.settings

data class SettingsUiState(
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val systemPrompt: String = "",
    val selectedPreset: String = "custom",
    val chunkingStrategy: String = "sliding_window",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val connectionTestResult: String? = null,
    val connectionTesting: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val error: String? = null,
)

data class ModelPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val modelName: String,
    val apiKey: String = "",
)

val MODEL_PRESETS = listOf(
    ModelPreset("lmstudio", "LM Studio（本地）", "http://localhost:1234/v1", "gemma-3-4b"),
    ModelPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    ModelPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    ModelPreset("custom", "自定义", "", "", ""),
)
