package com.asuka.pocketpdf.domain.model

/**
 * 一个文档下的聊天会话元数据（v1.3.0 多会话）。
 *
 * 消息按 [id] 关联到具体会话；[updatedAt] 用于会话列表排序与"最近会话"恢复。
 */
data class Conversation(
    val id: Long,
    val documentId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)
