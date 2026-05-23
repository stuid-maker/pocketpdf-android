package com.asuka.pocketpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asuka.pocketpdf.data.local.entity.ChunkEntity

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY pageIndex ASC, chunkIndex ASC")
    suspend fun getChunksByDocumentId(documentId: Long): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE documentId = :documentId AND pageIndex = :pageIndex ORDER BY chunkIndex ASC")
    suspend fun getChunksByDocumentIdAndPage(documentId: Long, pageIndex: Int): List<ChunkEntity>

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: Long)
}
