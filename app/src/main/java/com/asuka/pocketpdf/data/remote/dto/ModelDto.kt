package com.asuka.pocketpdf.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 对应 OpenAI 兼容协议 `/v1/models` 响应中 `data[i]` 的单条目。
 *
 * 只声明我们用得到的字段，多余字段（如 `created`、各家实现自定义扩展）由 Moshi 忽略。
 * 转换到 domain 见 [com.asuka.pocketpdf.data.remote.repository.LlmRepositoryImpl]。
 */
@JsonClass(generateAdapter = true)
data class ModelDto(
    @Json(name = "id") val id: String,
    @Json(name = "owned_by") val ownedBy: String? = null,
)
