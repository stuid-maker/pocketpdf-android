package com.asuka.pocketpdf.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyCipher: ApiKeyCipher,
) {
    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val modelName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MODEL_NAME] ?: DEFAULT_MODEL_NAME
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        val stored = prefs[KEY_API_KEY] ?: return@map null
        if (apiKeyCipher.isEncrypted(stored)) {
            runCatching { apiKeyCipher.decrypt(stored) }
                .getOrElse {
                    context.dataStore.edit { values -> values.remove(KEY_API_KEY) }
                    null
                }
        } else {
            context.dataStore.edit { it[KEY_API_KEY] = apiKeyCipher.encrypt(stored) }
            stored
        }
    }

    val systemPrompt: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_PROMPT] ?: ""
    }

    val chunkingStrategy: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHUNKING_STRATEGY] ?: STRATEGY_SLIDING_WINDOW
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url }
    }

    suspend fun setModelName(name: String) {
        context.dataStore.edit { it[KEY_MODEL_NAME] = name }
    }

    suspend fun setApiKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key != null) {
                prefs[KEY_API_KEY] = apiKeyCipher.encrypt(key)
            } else {
                prefs.remove(KEY_API_KEY)
            }
        }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setChunkingStrategy(strategy: String) {
        context.dataStore.edit { it[KEY_CHUNKING_STRATEGY] = strategy }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun resetDefaults() {
        // Keep onboarding flag — user shouldn't re-see onboarding after settings reset
        val wasOnboarded = onboardingCompleted.first()
        context.dataStore.edit { it.clear() }
        if (wasOnboarded) {
            context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
        }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MODEL_NAME = stringPreferencesKey("model_name")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_CHUNKING_STRATEGY = stringPreferencesKey("chunking_strategy")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        const val STRATEGY_SLIDING_WINDOW = "sliding_window"
        const val STRATEGY_PARAGRAPH = "paragraph"
        const val DEFAULT_BASE_URL = "http://localhost:1234/v1"
        const val DEFAULT_MODEL_NAME = "gemma-3-4b"
    }
}
