package com.asuka.pocketpdf.data.embedding

import android.content.Context
import com.asuka.pocketpdf.domain.embedding.EmbeddingEngine
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipeEmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddingEngine {

    private var textEmbedder: TextEmbedder? = null

    /**
     * 初始化 MediaPipe TextEmbedder。
     * 默认使用 Universal Sentence Encoder 或类似模型（需要 .tflite 格式）。
     * 模型文件需放入 assets/models/ 目录。
     */
    private fun ensureInitialized() {
        if (textEmbedder == null) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("models/universal_sentence_encoder.tflite")
                    .build()
                
                val options = TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .build()

                textEmbedder = TextEmbedder.createFromOptions(context, options)
                Timber.d("MediaPipe TextEmbedder initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize MediaPipe TextEmbedder")
                // 注意：由于是 Singleton 且在后台运行，这里不抛出异常，
                // 但后续 encode 会失败。
            }
        }
    }

    override suspend fun getEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        ensureInitialized()
        val embedder = textEmbedder ?: throw IllegalStateException("TextEmbedder not initialized")
        val result = embedder.embed(text)
        // MediaPipe 返回的是 List<Embedding>，通常取第一个
        result.embeddingResult().embeddings().first().floatEmbedding()
    }

    override suspend fun getEmbeddings(texts: List<String>): List<FloatArray> = withContext(Dispatchers.Default) {
        ensureInitialized()
        val embedder = textEmbedder ?: throw IllegalStateException("TextEmbedder not initialized")
        texts.map { text ->
            embedder.embed(text).embeddingResult().embeddings().first().floatEmbedding()
        }
    }
}
