package com.asuka.pocketpdf.ui.settings

data class SettingsUiState(
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val connectionTestResult: String? = null,
    val connectionTesting: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val error: String? = null,
)
