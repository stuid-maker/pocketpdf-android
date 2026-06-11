package com.asuka.pocketpdf.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asuka.pocketpdf.domain.model.SummaryScope
import com.asuka.pocketpdf.domain.repository.SummaryCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cacheStore by preferencesDataStore(name = "summary_cache")

@Singleton
class SummaryCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SummaryCacheRepository {
    private val KEY_PREFIX = "summary"

    override fun get(
        documentId: Long,
        scope: SummaryScope,
        algorithmVersion: Int,
        model: String,
        systemPrompt: String,
    ): Flow<String?> =
        context.cacheStore.data.map { prefs ->
            val key = stringPreferencesKey(
                cacheKey(documentId, scope, algorithmVersion, model, systemPrompt)
            )
            prefs[key]
        }

    override suspend fun set(
        documentId: Long,
        scope: SummaryScope,
        algorithmVersion: Int,
        model: String,
        systemPrompt: String,
        text: String,
    ) {
        context.cacheStore.edit { prefs ->
            val key = stringPreferencesKey(
                cacheKey(documentId, scope, algorithmVersion, model, systemPrompt)
            )
            prefs[key] = text
        }
    }

    override suspend fun invalidate(documentId: Long) {
        context.cacheStore.edit { prefs ->
            val prefix = "$KEY_PREFIX:$documentId:"
            prefs.asMap().keys
                .filterIsInstance<Preferences.Key<String>>()
                .filter { it.name.startsWith(prefix) }
                .forEach { prefs.remove(it) }
        }
    }

    override suspend fun invalidateAll() {
        context.cacheStore.edit { prefs ->
            prefs.asMap().keys
                .filterIsInstance<Preferences.Key<String>>()
                .filter { it.name.startsWith("$KEY_PREFIX:") }
                .forEach { prefs.remove(it) }
        }
    }

    /**
     * 生成带版本的缓存键。
     *
     * 格式: summary:{docId}:{scope}:v{algoVer}:{model}:{sysPromptHash}
     *
     * 取 systemPrompt 前 8 位稳定散列作为摘要，避免 Prompt 中的特殊字符破坏 key 格式。
     */
    private fun cacheKey(
        documentId: Long,
        scope: SummaryScope,
        algorithmVersion: Int,
        model: String,
        systemPrompt: String,
    ): String {
        val scopeKey = scope.toCacheKey()
        val modelSafe = model.replace(":", "_")
        val sysHash = if (systemPrompt.isBlank()) "default"
        else systemPromptHash(systemPrompt)
        return "$KEY_PREFIX:$documentId:$scopeKey:v$algorithmVersion:$modelSafe:$sysHash"
    }

    private fun SummaryScope.toCacheKey(): String = when (this) {
        is SummaryScope.Full -> "full"
        is SummaryScope.Page -> "page_${pageIndex}"
    }

    /**
     * 计算 system prompt 的 SHA-256 哈希。
     *
     * 使用 MessageDigest 替代自制 31 进制散列，消除碰撞风险。
     * 不同 system prompt 命中同一条缓存会导致错误摘要。
     * 截取前 16 位十六进制已足够区分所有实际使用的 prompt。
     */
    private fun systemPromptHash(prompt: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(prompt.toByteArray(Charsets.UTF_8))
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }
}
