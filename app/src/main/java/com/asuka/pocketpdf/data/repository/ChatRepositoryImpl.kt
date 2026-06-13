package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.data.local.dao.ChatMessageDao
import com.asuka.pocketpdf.data.local.dao.ConversationDao
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import com.asuka.pocketpdf.data.local.entity.ConversationEntity
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.Conversation
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import com.asuka.pocketpdf.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao,
    private val conversationDao: ConversationDao,
) : ChatRepository {

    override fun observeConversations(documentId: Long): Flow<List<Conversation>> =
        conversationDao.observeByDocumentId(documentId).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getConversations(documentId: Long): List<Conversation> =
        conversationDao.getByDocumentId(documentId).map { it.toDomain() }

    override suspend fun createConversation(documentId: Long, title: String): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insert(
            ConversationEntity(
                documentId = documentId,
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        conversationDao.updateTitle(conversationId, title)
    }

    override suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteById(conversationId)
    }

    override fun observeMessages(conversationId: Long): Flow<List<StoredChatMessage>> =
        dao.observeByConversationId(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveMessage(conversationId: Long, message: ChatMessage) {
        val conversation = checkNotNull(conversationDao.getById(conversationId)) {
            "Cannot save message: conversation $conversationId not found"
        }
        dao.insert(
            ChatMessageEntity(
                documentId = conversation.documentId,
                conversationId = conversationId,
                role = message.role,
                content = message.content,
            )
        )
        conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())
    }

    override suspend fun clearHistory(conversationId: Long) {
        dao.deleteByConversationId(conversationId)
    }

    override suspend fun getHistorySnapshot(conversationId: Long): List<StoredChatMessage> {
        return dao.getByConversationId(conversationId).map { it.toDomain() }
    }

    private fun ChatMessageEntity.toDomain() = StoredChatMessage(
        id = id,
        role = role,
        content = content,
    )

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        documentId = documentId,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
