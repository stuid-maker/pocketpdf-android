package com.asuka.pocketpdf.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val modelName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MODEL_NAME] ?: DEFAULT_MODEL_NAME
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_KEY]
    }

    val systemPrompt: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_PROMPT] ?: ""
    }

    val chunkingStrategy: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHUNKING_STRATEGY] ?: STRATEGY_SLIDING_WINDOW
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url }
    }

    suspend fun setModelName(name: String) {
        context.dataStore.edit { it[KEY_MODEL_NAME] = name }
    }

    suspend fun setApiKey(key: String?) {
        context.dataStore.edit { it[KEY_API_KEY] = key ?: "" }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setChunkingStrategy(strategy: String) {
        context.dataStore.edit { it[KEY_CHUNKING_STRATEGY] = strategy }
    }

    suspend fun resetDefaults() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_CHUNKING_STRATEGY = stringPreferencesKey("chunking_strategy")
        const val STRATEGY_SLIDING_WINDOW = "sliding_window"
        const val STRATEGY_PARAGRAPH = "paragraph"
        const val DEFAULT_BASE_URL = "http://localhost:1234/v1"
        const val DEFAULT_MODEL_NAME = "gemma-3-4b"
    }
}
