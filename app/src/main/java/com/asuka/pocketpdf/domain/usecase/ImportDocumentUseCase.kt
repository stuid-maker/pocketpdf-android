package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * 导入一篇 PDF：把 SAF 选中的文件复制到内部存储 + PdfBox 解析 + 落库。
 *
 * Day 1 范围：本 UseCase 的契约 + Repository 接口落地，Impl 暂回
 * `Result.Failure(NotImplementedError(..))`，Day 2 接入 PdfBox 后真实现。
 *
 * 参数用 String 装 URI 是刻意选择：让 domain 层不引入 `android.net.Uri`，
 * 维持"domain 可在 JVM 单测无 Robolectric 直接跑"的边界（详见 PLAN.md §4.1）。
 *
 * @param sourceUri SAF 返回 URI 的字符串形式（`content://...`）
 * @param displayName SAF DISPLAY_NAME，作为初始标题
 */
class ImportDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        displayName: String,
    ): Result<Document> = repository.importDocument(sourceUri, displayName)
}
