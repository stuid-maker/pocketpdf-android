package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(documentId: Long): Flow<List<StoredChatMessage>>
    suspend fun saveMessage(documentId: Long, message: ChatMessage)
    suspend fun clearHistory(documentId: Long)
}
