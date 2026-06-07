package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 删除一篇文档：DB 行 + 对应内部存储 PDF 文件。
 *
 * 任意一环失败（DB 或文件）由 Repository 包成 `Result.Failure`，
 * 调用方据此决定是否提示用户重试 / 进行孤儿清理。
 */
class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val summaryCacheRepository: SummaryCacheRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> {
        val result = repository.deleteDocument(id)
        if (result is Result.Success) {
            try {
                summaryCacheRepository.invalidate(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The document is already deleted; orphan cache cleanup is best-effort.
            }
        }
        return result
    }
}
