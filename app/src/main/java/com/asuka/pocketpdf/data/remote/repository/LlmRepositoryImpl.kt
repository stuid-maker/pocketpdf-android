package com.asuka.pocketpdf.data.remote.repository

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.resultOf
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.data.remote.SseStreamParser
import com.asuka.pocketpdf.data.remote.dto.ChatCompletionRequestDto
import com.asuka.pocketpdf.data.remote.dto.MessageDto
import com.asuka.pocketpdf.data.remote.dto.ModelDto
import com.asuka.pocketpdf.data.remote.dto.ModelsResponseDto
import com.asuka.pocketpdf.domain.model.ChatMessage
import com.asuka.pocketpdf.domain.model.LlmModel
import com.asuka.pocketpdf.domain.repository.LlmRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * [LlmRepository] 的 OkHttp 实现（无 Retrofit）。
 *
 * 全部 HTTP 调用统一走原生 OkHttp + 动态读取 [SettingsDataStore] 的 baseUrl，
 * 确保设置页、诊断页、聊天流式调用访问的是同一地址，不存在硬编码分歧。
 */
class LlmRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
) : LlmRepository {

    private val sseParser = SseStreamParser(moshi)
    private val requestAdapter = moshi.adapter(ChatCompletionRequestDto::class.java)

    override suspend fun listModels(): Result<List<LlmModel>> =
        withContext(dispatchers.io) {
            resultOf {
                val baseUrl = settingsDataStore.baseUrl.first()
                fetchModels(baseUrl)
            }
        }

    /**
     * 流式 Chat Completion (SSE)。
     *
     * **注意：这是一个 cold Flow**，每次 [collect] 都会发起新的 HTTP 请求。
     * 调用方（ViewModel/UseCase）必须确保只 collect 一次，避免重复调用 LLM 产生额外费用。
     */
    override fun chatCompletionStream(
        model: String,
        messages: List<ChatMessage>,
        temperature: Float?,
        maxTokens: Int?,
    ): Flow<String> = callbackFlow {
        val requestDto = ChatCompletionRequestDto(
            model = model,
            messages = messages.map { MessageDto(role = it.role, content = it.content) },
            stream = true,
            temperature = temperature,
            maxTokens = maxTokens,
        )

        val requestBody = requestAdapter.toJson(requestDto)
            .toRequestBody("application/json".toMediaType())

        val (baseUrl, apiKey) = withContext(Dispatchers.IO) {
            settingsDataStore.baseUrl.first() to settingsDataStore.apiKey.first()
        }
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .apply {
                if (!apiKey.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val callRef = AtomicReference<okhttp3.Call?>()
        val responseRef = AtomicReference<okhttp3.Response?>()
        val worker = launch(dispatchers.io) {
            try {
                val call = okHttpClient.newCall(request)
                callRef.set(call)
                coroutineContext.ensureActive()
                val response = call.execute()
                responseRef.set(response)

                response.use { openResponse ->
                    if (!openResponse.isSuccessful) {
                        val errorBody = openResponse.body?.string() ?: "HTTP ${openResponse.code}"
                        throw IOException("Chat completion failed: $errorBody")
                    }

                    val body = openResponse.body
                        ?: throw IOException("Chat completion returned empty body")
                    sseParser.parse(body.source()).collect { token ->
                        val result = trySend(token)
                        if (result.isFailure) {
                            Timber.tag(TAG).w("channel closed, dropping SSE token")
                        }
                    }
                }
                channel.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (callRef.get()?.isCanceled() != true) {
                    Timber.tag(TAG).e(e, "chatCompletionStream failed")
                    channel.close(e)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "chatCompletionStream failed")
                channel.close(e)
            } finally {
                callRef.set(null)
                responseRef.getAndSet(null)?.close()
            }
        }

        awaitClose {
            Timber.tag(TAG).d("chatCompletionStream Flow cancelled, cancelling HTTP call")
            callRef.get()?.cancel()
            responseRef.get()?.close()
            worker.cancel()
        }
    }

    override suspend fun testConnection(baseUrl: String): Result<List<LlmModel>> =
        withContext(dispatchers.io) {
            resultOf { fetchModels(baseUrl) }
        }

    /** 向指定 baseUrl 的 /models 端点发送 GET 请求并反序列化模型列表 */
    private suspend fun fetchModels(baseUrl: String): List<LlmModel> {
        val apiKey = settingsDataStore.apiKey.first()
        val request = Request.Builder()
            .url("$baseUrl/models")
            .get()
            .header("Content-Type", "application/json")
            .apply {
                if (!apiKey.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .build()
        val response = okHttpClient.newCall(request).execute()
        return response.use { resp ->
            val body = resp.body?.string() ?: throw IOException("Empty response body")
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: $body")
            }
            val modelsResponse = moshi.adapter(ModelsResponseDto::class.java).fromJson(body)
                ?: throw IOException("Failed to parse models response")
            modelsResponse.data.map(ModelDto::toDomain)
        }
    }

    companion object {
        private const val TAG = "LlmRepositoryImpl"
    }
}

private fun ModelDto.toDomain(): LlmModel = LlmModel(
    id = id,
    ownedBy = ownedBy,
)
