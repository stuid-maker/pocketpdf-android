package com.asuka.pocketpdf.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * OpenAI 兼容 `/v1/chat/completions` 请求体。
 *
 * 只声明我们用到的字段，LM Studio / DeepSeek / 通义等兼容后端均接受此格式。
 */
@JsonClass(generateAdapter = true)
data class ChatCompletionRequestDto(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<MessageDto>,
    @Json(name = "stream") val stream: Boolean = true,
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "max_tokens") val maxTokens: Int? = null,
)

/**
 * 消息：role + content。
 * role 取值 "user" / "assistant" / "system"。
 */
@JsonClass(generateAdapter = true)
data class MessageDto(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
)

/**
 * 流式响应的 SSE chunk，对应 OpenAI `data: {json}\n\n` 中的 JSON 部分。
 */
@JsonClass(generateAdapter = true)
data class ChatCompletionChunkDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "object") val `object`: String? = null,
    @Json(name = "created") val created: Long? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "choices") val choices: List<ChoiceDto>? = null,
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "delta") val delta: DeltaDto? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class DeltaDto(
    @Json(name = "role") val role: String? = null,
    @Json(name = "content") val content: String? = null,
)
