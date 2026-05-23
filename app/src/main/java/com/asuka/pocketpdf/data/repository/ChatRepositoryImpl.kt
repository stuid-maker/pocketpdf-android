package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import com.asuka.pocketpdf.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao,
) : ChatRepository {

    override fun observeMessages(documentId: Long): Flow<List<StoredChatMessage>> =
        dao.observeByDocumentId(documentId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveMessage(documentId: Long, message: ChatMessage) {
        dao.insert(
            ChatMessageEntity(
                documentId = documentId,
                role = message.role,
                content = message.content,
            )
        )
    }

    override suspend fun clearHistory(documentId: Long) {
        dao.deleteByDocumentId(documentId)
    }

    private fun ChatMessageEntity.toDomain() = StoredChatMessage(
        id = id,
        role = role,
        content = content,
    )
}
