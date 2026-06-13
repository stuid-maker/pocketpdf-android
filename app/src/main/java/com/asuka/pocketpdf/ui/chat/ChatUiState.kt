package com.asuka.pocketpdf.ui.chat

import com.asuka.pocketpdf.domain.model.Conversation

data class ChatUiState(
    val messages: List<ChatDisplayMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null,
    val pageCount: Int = 0,
    val conversationId: Long = -1L,
    val conversations: List<Conversation> = emptyList(),
)
