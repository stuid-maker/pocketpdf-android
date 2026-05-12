package com.asuka.pocketpdf.data.remote.repository

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.resultOf
import com.asuka.pocketpdf.data.remote.LlmApi
import com.asuka.pocketpdf.data.remote.dto.ModelDto
import com.asuka.pocketpdf.domain.model.LlmModel
import com.asuka.pocketpdf.domain.repository.LlmRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [LlmRepository] 的 Retrofit 实现。
 *
 * 职责：
 * 1. 通过 [LlmApi] 发起 HTTP 调用
 * 2. 把 DTO 映射为 domain model
 * 3. 把异常包成 [Result.Failure]，向 UseCase 屏蔽底层细节
 *
 * 调度：Retrofit suspend 自身已在 OkHttp 线程池跑，这里仍显式 [withContext]
 * 为后续可能的 IO 工作（DTO → domain 大映射、磁盘缓存）预留正确线程。
 */
class LlmRepositoryImpl @Inject constructor(
    private val api: LlmApi,
    private val dispatchers: DispatcherProvider,
) : LlmRepository {

    override suspend fun listModels(): Result<List<LlmModel>> =
        withContext(dispatchers.io) {
            resultOf { api.listModels().data.map(ModelDto::toDomain) }
        }
}

private fun ModelDto.toDomain(): LlmModel = LlmModel(
    id = id,
    ownedBy = ownedBy,
)
