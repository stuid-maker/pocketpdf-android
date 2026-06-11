package com.asuka.pocketpdf.ui.chat

import com.asuka.pocketpdf.ui.ai.GenerationProgressDisplay

data class ChatDisplayMessage(
    val id: Long,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val progress: GenerationProgressDisplay? = null,
)

object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}
