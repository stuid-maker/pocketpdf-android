package com.asuka.pocketpdf.domain.model

/**
 * LLM 对话消息，领域层纯 Kotlin data class。
 *
 * 不依赖任何 Android / 网络框架类型。
 * DTO 映射在 [com.asuka.pocketpdf.data.remote.repository.LlmRepositoryImpl] 完成。
 */
data class ChatMessage(
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}
