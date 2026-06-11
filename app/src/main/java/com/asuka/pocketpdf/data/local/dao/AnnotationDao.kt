package com.asuka.pocketpdf.data.local.dao

import androidx.room.*
import com.asuka.pocketpdf.data.local.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :documentId AND pageIndex = :pageIndex ORDER BY createdAt ASC")
    fun observeByPage(documentId: Long, pageIndex: Int): Flow<List<AnnotationEntity>>

    @Insert
    suspend fun insert(annotation: AnnotationEntity): Long

    @Delete
    suspend fun delete(annotation: AnnotationEntity)

    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY pageIndex ASC, createdAt ASC")
    suspend fun getByDocument(documentId: Long): List<AnnotationEntity>
}
