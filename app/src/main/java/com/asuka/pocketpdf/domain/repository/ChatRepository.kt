package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.Conversation
import com.asuka.pocketpdf.domain.model.StoredChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    /** 观察某文档下的全部会话，按最近更新排序。 */
    fun observeConversations(documentId: Long): Flow<List<Conversation>>

    /** 获取某文档下会话快照（用于解析"活跃会话"）。 */
    suspend fun getConversations(documentId: Long): List<Conversation>

    /** 新建会话，返回新会话 id。 */
    suspend fun createConversation(documentId: Long, title: String): Long

    suspend fun renameConversation(conversationId: Long, title: String)

    suspend fun deleteConversation(conversationId: Long)

    fun observeMessages(conversationId: Long): Flow<List<StoredChatMessage>>

    suspend fun saveMessage(conversationId: Long, message: ChatMessage)

    /** 清空某会话的全部消息（保留会话本身）。 */
    suspend fun clearHistory(conversationId: Long)

    /** 获取会话历史快照（用于 AskDocumentUseCase 构建上下文）。 */
    suspend fun getHistorySnapshot(conversationId: Long): List<StoredChatMessage>
}
