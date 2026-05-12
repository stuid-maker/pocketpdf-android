package com.asuka.pocketpdf.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `/v1/models` 顶层响应包装：`{ "object": "list", "data": [...] }`。
 */
@JsonClass(generateAdapter = true)
data class ModelsResponseDto(
    @Json(name = "data") val data: List<ModelDto>,
)
