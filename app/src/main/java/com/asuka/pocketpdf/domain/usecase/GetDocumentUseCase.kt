package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * 按主键拉单条文档；不存在返回 `null`。
 *
 * 用 nullable 而非 `Result<Document>` 的原因：
 * "ID 不存在"在业务上属正常分支（用户从列表点已删除项的可能性等），不是错误路径。
 * 真正的 I/O 失败由 Repository 内部抛运行时异常，由调用方上层包成 Result。
 */
class GetDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(id: Long): Document? = repository.getDocument(id)
}
