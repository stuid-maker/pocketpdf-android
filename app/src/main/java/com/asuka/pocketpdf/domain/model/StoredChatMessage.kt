package com.asuka.pocketpdf.domain.model

/**
 * 已持久化的对话消息，包含数据库主键 id。
 * 与 [ChatMessage] 的区别：ChatMessage 用于 LLM API 请求/响应（无 id），
 * StoredChatMessage 用于从 DB 读取的消息（带稳定 id）。
 */
data class StoredChatMessage(
    val id: Long,
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
