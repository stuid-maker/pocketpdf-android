package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.LlmModel
import com.asuka.pocketpdf.domain.repository.LlmRepository
import javax.inject.Inject

/**
 * 获取后端可用模型列表的用例。Week 0 仅用于 Ping 自检。
 *
 * Week 3 起会被复用：聊天页"模型选择"下拉框直接调这个 UseCase。
 *
 * 设计上保持单一职责：不缓存、不过滤、不排序——把策略留给上层。
 */
class GetAvailableModelsUseCase @Inject constructor(
    private val repository: LlmRepository,
) {
    suspend operator fun invoke(): Result<List<LlmModel>> = repository.listModels()
}
