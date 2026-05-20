package com.asuka.pocketpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

/**
 * 文档库的 DAO。
 *
 * 设计要点：
 * - [observeAll] 返回 [Flow]：Room 的 Flow DAO 内部已挂表变更监听，
 *   任何 insert / delete / update 都会自动重发——UI 不必手动刷新。
 * - 单查 [getById] 走 suspend：一次性请求场景。
 * - [insert] 返回新行 rowId（= 自增 id），Repository 用它把内存 Document 的 id 补齐。
 * - [deleteById] 返回受影响行数，方便 Repository 区分"删除成功 vs id 根本不存在"。
 */
@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(entity: DocumentEntity): Long

    @Update
    suspend fun update(entity: DocumentEntity): Int

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
