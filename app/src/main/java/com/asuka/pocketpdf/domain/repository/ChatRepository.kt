package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(documentId: Long): Flow<List<StoredChatMessage>>
    suspend fun saveMessage(documentId: Long, message: ChatMessage)
    suspend fun clearHistory(documentId: Long)
    /** 获取对话历史的快照（用于 AskDocumentUseCase 构建上下文） */
    suspend fun getHistorySnapshot(documentId: Long): List<StoredChatMessage>
}
