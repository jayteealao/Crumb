package com.github.jayteealao.pref

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Recent-search history for the SearchScreen. Stored as a JSON-encoded
 * `List<String>` under a single Preferences key so the order ("most recent
 * first") survives — `stringSetPreferencesKey` is set-typed and would
 * shuffle on every write. Capped at [MAX_RECENT_SEARCHES] entries; duplicates
 * are coalesced so re-running a search promotes it to the head of the list
 * rather than adding a second row.
 *
 * Reuses the existing [Context.dataStore] singleton from [AuthPref]; do NOT
 * instantiate a second `preferencesDataStore(name = ...)` against the same
 * Context — Android only allows one DataStore instance per file per process,
 * and a duplicate registration raises an [IllegalStateException] at first
 * read.
 */
private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
private val recentSearchesSerializer = ListSerializer(String.serializer())
private const val MAX_RECENT_SEARCHES = 10

fun Context.recentSearches(): Flow<List<String>> =
    dataStore.data.map { prefs ->
        prefs[RECENT_SEARCHES_KEY]?.decodeRecentSearches() ?: emptyList()
    }

suspend fun Context.addRecentSearch(query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    dataStore.edit { prefs ->
        val current = prefs[RECENT_SEARCHES_KEY]?.decodeRecentSearches() ?: emptyList()
        val updated = (listOf(trimmed) + current.filter { it != trimmed }).take(MAX_RECENT_SEARCHES)
        prefs[RECENT_SEARCHES_KEY] = Json.encodeToString(recentSearchesSerializer, updated)
    }
}

suspend fun Context.clearRecentSearches() {
    dataStore.edit { prefs -> prefs.remove(RECENT_SEARCHES_KEY) }
}

private fun String.decodeRecentSearches(): List<String> = try {
    Json.decodeFromString(recentSearchesSerializer, this)
} catch (_: Exception) {
    // Corrupt payload (older format, manual tampering) — silently reset on
    // next write rather than crash the search screen.
    emptyList()
}
