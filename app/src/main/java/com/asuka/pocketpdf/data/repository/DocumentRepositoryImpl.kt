package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.resultOf
import com.asuka.pocketpdf.data.local.dao.ChunkDao
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.data.local.mapper.toDomain
import com.asuka.pocketpdf.data.local.mapper.toEntity
import com.asuka.pocketpdf.data.pdf.PdfTextExtractor
import com.asuka.pocketpdf.data.storage.FileStorage
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * [DocumentRepository] 的真实现。
 *
 * 调度策略：
 * - observeDocuments 的 Flow 不显式切 dispatcher——Room Flow DAO 内部已经在 IO 池上执行
 * - 其他 suspend 操作显式 withContext(dispatchers.io)，DAO 即便有 Internal 调度也保险一道
 *
 * importDocument 编排顺序（W1 Day 2 真实现）：
 *   1. FileStorage.copyToInternal  → 拿到 File
 *   2. PdfTextExtractor.extractPagesText → 拿 pageCount = pages.size
 *   3. DocumentDao.insert → 拿到自增 id
 *   4. 用 id + pageCount 拼出 Document 返回 Success
 *
 * **失败回滚**（决策 5）：步骤 2 / 3 抛异常时删掉步骤 1 落地的文件——保证磁盘干净。
 * 步骤 1 自身抛异常时 [FileStorage.copyToInternal] 已经自己删了半成品，这里不再二次删。
 */
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val fileStorage: FileStorage,
    private val pdfTextExtractor: PdfTextExtractor,
    private val dispatchers: DispatcherProvider,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getDocument(id: Long): Document? = withContext(dispatchers.io) {
        dao.getById(id)?.toDomain()
    }
    
    override suspend fun updateDocument(document: Document): Result<Unit> = withContext(dispatchers.io) {
        resultOf {
            dao.update(document.toEntity())
            Unit
        }
    }

    override suspend fun importDocument(
        sourceUri: String,
        displayName: String,
    ): Result<Document> = withContext(dispatchers.io) {
        resultOf {
            val copied: File = fileStorage.copyToInternal(sourceUri, displayName)
            val pageCount: Int = try {
                pdfTextExtractor.extractPagesText(copied).size
            } catch (t: Throwable) {
                copied.delete()
                throw t
            }
            val entity = DocumentEntity(
                title = displayName,
                uri = copied.absolutePath,
                pageCount = pageCount,
                indexStatus = IndexStatus.NOT_INDEXED.name,
                importedAt = System.currentTimeMillis(),
            )
            val insertedId = try {
                dao.insert(entity)
            } catch (t: Throwable) {
                copied.delete()
                throw t
            }
            Timber.tag(TAG).i(
                "importDocument: id=%d title=%s pages=%d path=%s",
                insertedId,
                displayName,
                pageCount,
                copied.absolutePath,
            )
            entity.copy(id = insertedId).toDomain()
        }
    }

    override suspend fun deleteDocument(id: Long): Result<Unit> = withContext(dispatchers.io) {
        resultOf {
            val entity = dao.getById(id)
                ?: throw IllegalStateException("Document #$id not found")
            val affected = dao.deleteById(id)
            check(affected == 1) { "Document #$id delete returned $affected affected rows" }
            // 文件删除失败不视为流程失败：DB 行已删，孤儿文件可由清理 Worker 兜底
            fileStorage.delete(entity.uri)
            Unit
        }
    }
    
    override suspend fun replaceChunks(
        documentId: Long,
        chunks: List<DocumentChunk>,
    ): Result<Unit> = withContext(dispatchers.io) {
        resultOf {
            require(chunks.all { it.documentId == documentId }) {
                "All chunks must belong to document #$documentId"
            }
            chunkDao.replaceForDocument(documentId, chunks.map { it.toEntity() })
            Unit
        }
    }

    override suspend fun getChunks(documentId: Long): List<DocumentChunk> = withContext(dispatchers.io) {
        chunkDao.getChunksByDocumentId(documentId).map { it.toDomain() }
    }

    override suspend fun getChunksByPage(documentId: Long, pageIndex: Int): List<DocumentChunk> = withContext(dispatchers.io) {
        chunkDao.getChunksByDocumentIdAndPage(documentId, pageIndex).map { it.toDomain() }
    }

    private companion object {
        const val TAG = "DocumentRepositoryImpl"
    }
}
