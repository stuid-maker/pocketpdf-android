package com.asuka.pocketpdf.data.repository

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.resultOf
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.local.mapper.toDomain
import com.asuka.pocketpdf.data.storage.FileStorage
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [DocumentRepository] 的真实现。
 *
 * Day 1 范围：observe / get / delete 三个方法已真实现；importDocument 故意返回
 * `Result.Failure(NotImplementedError(..))`，理由：
 * 1. **接口先稳**：让 ui / ViewModel 现在就能依赖 DocumentRepository 写界面、写状态机，
 *    Day 2 替换 importDocument 实现时 ui 层零改动
 * 2. **沉降提醒**：[com.asuka.pocketpdf.domain.usecase.ImportDocumentUseCase] 的单测
 *    现在断言"必返 NotImplementedError"，Day 2 真实现后该测试自动红——逼自己写真测
 *
 * 调度策略：
 * - observeDocuments 的 Flow 不显式切 dispatcher——Room Flow DAO 内部已经在 IO 池上执行
 * - 其他 suspend 操作显式 withContext(dispatchers.io)，DAO 即便有 Internal 调度也保险一道
 */
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    private val fileStorage: FileStorage,
    private val dispatchers: DispatcherProvider,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getDocument(id: Long): Document? = withContext(dispatchers.io) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun importDocument(
        sourceUri: String,
        displayName: String,
    ): Result<Document> = Result.Failure(
        NotImplementedError(
            "DocumentRepositoryImpl.importDocument: deferred to W1 Day 2 " +
                "(PdfBox text extraction + SAF stream copy + DAO insert)",
        ),
    )

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
}
