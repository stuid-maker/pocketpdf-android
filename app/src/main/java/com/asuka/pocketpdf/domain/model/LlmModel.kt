package com.asuka.pocketpdf.domain.model

/**
 * 表示 LLM 服务上可用的一个模型条目。
 *
 * 字段挑选原则：只保留 UI / UseCase 真正会用到的字段；DTO 里的 `object` 字段不在 domain 暴露。
 * 这样无论后端是 LM Studio / vLLM / DeepSeek / OpenAI，UI 层都不用感知差异。
 */
data class LlmModel(
    val id: String,
    val ownedBy: String?,
)
