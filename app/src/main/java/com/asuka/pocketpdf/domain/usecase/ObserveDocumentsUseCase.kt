package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 订阅文档库列表。
 *
 * 返回冷流，UI 层用 `repeatOnLifecycle(STARTED)` 收集；任何 import / delete
 * 都会经由 Room Flow DAO 自动推到 View，不需要 ViewModel 主动刷新。
 *
 * 保持单一职责：不过滤、不排序、不分页——把策略留给 ViewModel / Repository。
 */
class ObserveDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    operator fun invoke(): Flow<List<Document>> = repository.observeDocuments()
}
