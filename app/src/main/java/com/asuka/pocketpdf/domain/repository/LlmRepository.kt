package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.LlmModel
import kotlinx.coroutines.flow.Flow

/**
 * 与 LLM 服务交互的领域接口。
 *
 * 接口放在 domain，实现放在 data —— 强制 ui / usecase 只能依赖抽象，
 * 后续把 LM Studio 切到 DeepSeek 云端只需替换 data 层 Impl，零业务改动。
 */
interface LlmRepository {

    /** 拉取后端当前可用的模型列表，用于环境就绪自检。 */
    suspend fun listModels(): Result<List<LlmModel>>

    /**
     * 流式聊天补全，返回 token 级别的 [Flow]。
     *
     * 流本身通过 emit exception 传递网络 / 解析错误，因此不包 [Result]。
     *
     * @param model 模型 ID（如 "gemma-3-4b"）
     * @param messages 对话消息列表
     * @param temperature 采样温度，null 则用后端默认值
     */
    fun chatCompletionStream(
        model: String,
        messages: List<ChatMessage>,
        temperature: Float? = null,
        maxTokens: Int? = null,
    ): Flow<String>

    /**
     * 测试到指定 baseUrl 的连接，调用 `/v1/models`。
     */
    suspend fun testConnection(baseUrl: String): Result<List<LlmModel>>
}
