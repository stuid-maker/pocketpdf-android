package com.asuka.pocketpdf.data.embedding

import android.content.Context
import com.asuka.pocketpdf.domain.embedding.EmbeddingEngine
import com.asuka.pocketpdf.domain.embedding.EmbeddingModelMissingException
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

    @Volatile
    private var textEmbedder: TextEmbedder? = null

    /**
     * 初始化 MediaPipe TextEmbedder。
     * 默认使用 Universal Sentence Encoder（需要 .tflite 格式）。
     * 模型文件需放入 assets/models/ 目录，可通过 Gradle 任务自动下载或手动放置。
     *
     * 初始化失败时抛 [EmbeddingModelMissingException]，
     * 由上层（IndexWorker → Document FAILED → Library 页）向用户展示可读原因。
     */
    private fun ensureInitialized() {
        if (textEmbedder == null) {
            synchronized(this) {
                if (textEmbedder == null) {
                    try {
                        val baseOptions = BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET_PATH)
                            .build()

                        val options = TextEmbedderOptions.builder()
                            .setBaseOptions(baseOptions)
                            .build()

                        textEmbedder = TextEmbedder.createFromOptions(context, options)
                        Timber.d("MediaPipe TextEmbedder initialized successfully")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to initialize MediaPipe TextEmbedder")
                        throw EmbeddingModelMissingException(
                            modelPath = MODEL_ASSET_PATH,
                            originalError = e.message ?: e.javaClass.simpleName,
                        )
                    }
                }
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

    companion object {
        const val MODEL_ASSET_PATH = "models/universal_sentence_encoder.tflite"
    }
}
