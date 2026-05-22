package com.asuka.pocketpdf.ui.chat

data class ChatDisplayMessage(
    val id: Long,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
)

object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}
