package com.github.jayteealao.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


// Preference Name
const val PREFERENCE_NAME = "MyDataStore"

// Instance of DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCE_NAME)

/**
 * Add string data to DataStore (plaintext). Use [writeEncryptedString] for sensitive fields.
 */
suspend fun Context.writeString(key: String, value: String) {
    dataStore.edit { pref -> pref[stringPreferencesKey(key)] = value }
}

/**
 * Read string from DataStore preferences (plaintext). Use [readEncryptedString] for
 * sensitive fields.
 */
fun Context.readString(key: String): Flow<String> {
    return dataStore.data.map { pref ->
        pref[stringPreferencesKey(key)] ?: ""
    }
}

// ---------------------------------------------------------------------------
// Encrypted token helpers
// Keys that hold OAuth tokens are stored as AES/GCM blobs (Base64-encoded).
// All other preference keys continue to use the plaintext helpers above.
// ---------------------------------------------------------------------------

/** Encrypted variant of [writeString]. The value is AES/GCM-encrypted before storage. */
suspend fun Context.writeEncryptedString(key: String, value: String) {
    val encrypted = TokenCryptoManager.encrypt(value)
    dataStore.edit { pref -> pref[stringPreferencesKey(key)] = encrypted }
}

/**
 * Encrypted variant of [readString]. The emitted values are decrypted on the fly.
 *
 * If the stored value cannot be decrypted (e.g. an existing plaintext entry written
 * before encryption was introduced) the flow emits an empty string, and a call to
 * [migrateTokenToEncrypted] is required to re-encrypt it.
 */
fun Context.readEncryptedString(key: String): Flow<String> {
    return dataStore.data.map { pref ->
        val raw = pref[stringPreferencesKey(key)] ?: return@map ""
        val decrypted = TokenCryptoManager.decrypt(raw)
        // If decrypt returned a sentinel, the value is still plaintext — surface "" so
        // callers treat the token as absent until migration completes.
        if (decrypted.startsWith(TokenCryptoManager.PLAINTEXT_SENTINEL_PREFIX)) "" else decrypted
    }
}

/**
 * One-time migration: reads any existing plaintext value stored under [key],
 * re-writes it as an encrypted blob, and clears the plaintext copy.
 *
 * Safe to call on every app start — it is a no-op when the value is already
 * encrypted or absent.
 */
suspend fun Context.migrateTokenToEncrypted(key: String) {
    val prefKey = stringPreferencesKey(key)
    val current = dataStore.data.first()[prefKey] ?: return
    if (current.isEmpty()) return
    val decrypted = TokenCryptoManager.decrypt(current)
    if (decrypted.startsWith(TokenCryptoManager.PLAINTEXT_SENTINEL_PREFIX)) {
        // Value is still plaintext — strip the sentinel and re-encrypt.
        val plaintext = decrypted.removePrefix(TokenCryptoManager.PLAINTEXT_SENTINEL_PREFIX)
        val encrypted = TokenCryptoManager.encrypt(plaintext)
        dataStore.edit { pref -> pref[prefKey] = encrypted }
    }
    // If decrypt succeeded without a sentinel the value is already encrypted — nothing to do.
}
