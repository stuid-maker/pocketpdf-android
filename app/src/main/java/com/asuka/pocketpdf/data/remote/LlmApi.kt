package com.asuka.pocketpdf.data.remote

import com.asuka.pocketpdf.data.remote.dto.ModelsResponseDto
import retrofit2.http.GET

/**
 * OpenAI 兼容 LLM 服务的 Retrofit 接口。
 *
 * Week 0 只需要 `/v1/models` 用于 Ping；后续 Week 3 起会在这里追加
 * `/v1/chat/completions`（带 SSE 流式）和 `/v1/embeddings`（如果选择走 HTTP embed）。
 *
 * BaseUrl 在 [com.asuka.pocketpdf.di.NetworkModule] 配置，默认 `http://localhost:1234/`
 * （adb reverse 后即本机 LM Studio）。
 */
interface LlmApi {

    /** 列出后端当前已加载的模型。LM Studio 上等同于 Server 端 Loaded Models。 */
    @GET("v1/models")
    suspend fun listModels(): ModelsResponseDto
}
