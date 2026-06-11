package com.asuka.pocketpdf.data.remote

import com.asuka.pocketpdf.data.remote.dto.ChatCompletionChunkDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okio.BufferedSource
import timber.log.Timber

/**
 * SSE (Server-Sent Events) / NDJSON 流式响应解析器。
 *
 * 将 LM Studio / OpenAI 兼容后端返回的流式 chunk 逐行解析为 token 流。
 *
 * 协议格式：`data: {json}\n\n`
 * - 每个 chunk 以 `data: ` 开头，后跟 JSON
 * - 空行分隔事件
 * - `: ...` 开头的行是 SSE 注释，跳过
 * - `data: [DONE]` 表示流结束
 *
 * 生命周期：[source] 的关闭由调用方（如 [LlmRepositoryImpl]）负责。
 * 本解析器只负责读取和 emit，不持有资源。
 *
 * @param moshi 用于反序列化 chunk JSON
 */
class SseStreamParser(
    private val moshi: Moshi,
) {

    private val chunkAdapter = moshi.adapter(ChatCompletionChunkDto::class.java)

    /**
     * 逐行读取 [source] 并 emit 每个非空 content token。
     *
     * 使用 [channelFlow] 而非 [flow]：读取操作可能发生在调用方的调度器上，
     * channelFlow 允许在不同 context 中 send。
     */
    fun parse(source: BufferedSource): Flow<String> = channelFlow {
        withContext(Dispatchers.IO) {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue

                // 跳过 SSE 注释行（以 `:` 开头）
                if (line.startsWith(":")) continue

                // 跳过空行
                if (line.isBlank()) continue

                // 检测流结束标记
                if (line.startsWith("data: [DONE]") || line.startsWith("data:[DONE]")) {
                    Timber.tag(TAG).d("Stream finished (DONE)")
                    break
                }

                // 提取 `data: ` 后的 JSON
                if (!line.startsWith("data: ")) continue

                val json = line.removePrefix("data: ")
                if (json.isBlank()) continue

                try {
                    val chunk = chunkAdapter.fromJson(json) ?: continue
                    val content = chunk.choices
                        ?.firstOrNull()
                        ?.delta
                        ?.content

                    if (!content.isNullOrEmpty()) {
                        send(content)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to parse SSE chunk (skipping): %s", json)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SseStreamParser"
    }
}
