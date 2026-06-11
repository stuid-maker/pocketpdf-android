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
    val confirmPresetId: String? = null,
    val confirmCloudPresetId: String? = null,
)

data class ModelPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val apiKey: String = "",
) {
    val apiKeyHint: String get() = PRESET_API_KEY_HINTS[id] ?: ""
    val baseUrlHint: String get() = PRESET_BASE_URL_HINTS[id] ?: ""
}

val MODEL_PRESETS = listOf(
    ModelPreset("custom", "自定义", "", ""),
    ModelPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", ""),
    ModelPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", ""),
    ModelPreset("lmstudio", "LM Studio（本地）", "http://localhost:1234/v1", ""),
)

private val PRESET_API_KEY_HINTS = mapOf(
    "lmstudio" to "本地无需 API Key，留空即可",
    "deepseek" to "请输入 DeepSeek API Key",
    "qwen" to "请输入通义千问 API Key",
    "custom" to "请输入服务器 API Key（如不需要可留空）",
)

private val PRESET_BASE_URL_HINTS = mapOf(
    "lmstudio" to "已自动填充本地 LM Studio 地址",
    "deepseek" to "已自动填充 DeepSeek 官方 API 地址",
    "qwen" to "已自动填充通义千问兼容地址",
    "custom" to "请输入 LLM 服务器地址\n注意：仅支持 localhost、127.0.0.1、10.0.2.2（或通过 adb reverse）",
)

fun ModelPreset.needsApiKey(): Boolean = id in listOf("deepseek", "qwen")

fun ModelPreset.isLocal(): Boolean = id == "lmstudio"

fun ModelPreset.isCloud(): Boolean = id in listOf("deepseek", "qwen")
