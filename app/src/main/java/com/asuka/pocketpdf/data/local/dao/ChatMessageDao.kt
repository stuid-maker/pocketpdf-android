package com.asuka.pocketpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asuka.pocketpdf.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeByConversationId(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getByConversationId(conversationId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: Long)
}
