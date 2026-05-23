package com.asuka.pocketpdf.data.remote.repository

import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.resultOf
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.data.remote.LlmApi
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * [LlmRepository] 的 Retrofit + OkHttp 实现。
 *
 * 职责：
 * 1. 通过 [LlmApi] 发起非流式 HTTP 调用（listModels）
 * 2. 通过原生 [OkHttpClient] 发起流式 chat completions
 * 3. 把 DTO 映射为 domain model
 * 4. 把异常包成 [Result.Failure] 或通过 Flow emit exception
 *
 * 流式调用绕过 Retrofit 的原因：Retrofit 的 suspend 函数会把整个 response body
 * 读进内存再返回，对大流式响应不适用。原生 OkHttp 直接拿到 [ResponseBody.source]。
 */
class LlmRepositoryImpl @Inject constructor(
    private val api: LlmApi,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
) : LlmRepository {

    private val sseParser = SseStreamParser(moshi)
    private val requestAdapter = moshi.adapter(ChatCompletionRequestDto::class.java)

    override suspend fun listModels(): Result<List<LlmModel>> =
        withContext(dispatchers.io) {
            resultOf { api.listModels().data.map(ModelDto::toDomain) }
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
    ): Flow<String> = callbackFlow {
        val requestDto = ChatCompletionRequestDto(
            model = model,
            messages = messages.map { MessageDto(role = it.role, content = it.content) },
            stream = true,
            temperature = temperature,
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

        var response: okhttp3.Response? = null
        try {
            response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                response.close()
                throw IOException("Chat completion failed: $errorBody")
            }

            val body = response.body
            if (body == null) {
                response.close()
                throw IOException("Chat completion returned empty body")
            }

            val source = body.source()
            sseParser.parse(source).collect { token ->
                val result = trySend(token)
                if (result.isFailure) {
                    Timber.tag(TAG).w("channel closed, dropping SSE token")
                }
            }
            response.close()
            channel.close()
        } catch (e: CancellationException) {
            response?.close()
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "chatCompletionStream failed")
            response?.close()
            throw e
        }

        awaitClose {
            Timber.tag(TAG).d("chatCompletionStream Flow cancelled")
        }
    }

    override suspend fun testConnection(baseUrl: String): Result<List<LlmModel>> =
        withContext(dispatchers.io) {
            resultOf {
                val apiKey = settingsDataStore.apiKey.first()
                val request = Request.Builder()
                    .url("$baseUrl/models")
                    .header("Content-Type", "application/json")
                    .apply {
                        if (!apiKey.isNullOrBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    val body = resp.body?.string() ?: throw IOException("Empty response body")
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP ${resp.code}: $body")
                    }
                    val modelsResponse = moshi.adapter(ModelsResponseDto::class.java).fromJson(body)
                        ?: throw IOException("Failed to parse models response")
                    modelsResponse.data.map(ModelDto::toDomain)
                }
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
