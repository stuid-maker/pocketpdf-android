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

    override fun get(documentId: Long, scope: SummaryScope): Flow<String?> =
        context.cacheStore.data.map { prefs ->
            val key = stringPreferencesKey(cacheKey(documentId, scope))
            prefs[key]
        }

    override suspend fun set(documentId: Long, scope: SummaryScope, text: String) {
        context.cacheStore.edit { prefs ->
            val key = stringPreferencesKey(cacheKey(documentId, scope))
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

    private fun cacheKey(documentId: Long, scope: SummaryScope): String =
        "$KEY_PREFIX:$documentId:${scope.toCacheKey()}"

    private fun SummaryScope.toCacheKey(): String = when (this) {
        is SummaryScope.Full -> "full"
        is SummaryScope.Page -> "page_${pageIndex}"
    }
}
