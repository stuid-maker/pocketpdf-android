package com.asuka.pocketpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.asuka.pocketpdf.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE documentId = :documentId ORDER BY updatedAt DESC, id DESC")
    fun observeByDocumentId(documentId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE documentId = :documentId ORDER BY updatedAt DESC, id DESC")
    suspend fun getByDocumentId(documentId: Long): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTimestamp(id: Long, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE documentId = :documentId")
    suspend fun countByDocumentId(documentId: Long): Int
}
