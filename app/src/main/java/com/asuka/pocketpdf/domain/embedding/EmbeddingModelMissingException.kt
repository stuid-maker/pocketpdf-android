package com.asuka.pocketpdf.domain.embedding

/**
 * 嵌入模型缺失或在 APK assets 中找不到，导致 TextEmbedder 初始化失败。
 *
 * 此异常应被上层捕获并转化为对用户可读的错误提示，
 * 例如在 Library 卡片上将 IndexStatus.FAILED 标记为「模型缺失」并提供说明文案。
 *
 * @param modelPath assets 下的相对路径，如 "models/universal_sentence_encoder.tflite"
 * @param originalError MediaPipe 原始初始化错误的 message（用于日志）
 */
class EmbeddingModelMissingException(
    val modelPath: String,
    val originalError: String,
) : IllegalStateException(
    "嵌入模型缺失或加载失败。请确保将 $modelPath 放入 assets 目录。" +
        "原始错误: $originalError",
)
