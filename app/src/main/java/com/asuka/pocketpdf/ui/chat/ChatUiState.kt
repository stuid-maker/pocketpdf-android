package com.asuka.pocketpdf.ui.chat

data class ChatUiState(
    val messages: List<ChatDisplayMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null,
)
