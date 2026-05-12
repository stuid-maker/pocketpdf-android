package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.LlmModel

/**
 * 与 LLM 服务交互的领域接口。
 *
 * 接口放在 domain，实现放在 data —— 强制 ui / usecase 只能依赖抽象，
 * 后续把 LM Studio 切到 DeepSeek 云端只需替换 data 层 Impl，零业务改动。
 */
interface LlmRepository {

    /** 拉取后端当前可用的模型列表，用于环境就绪自检。 */
    suspend fun listModels(): Result<List<LlmModel>>
}
