package com.github.jayteealao.crumbs.migration

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.jayteealao.pref.readString
import com.github.jayteealao.pref.writeString
import com.github.jayteealao.twitter.data.Prefs
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

// One-shot runner that uploads the on-device X refresh token to the
// server-side `migrateXToken` callable, then clears the local Prefs so the
// device never again holds an X credential. KEEP-policy enqueue + the
// X_TOKEN_MIGRATED flag make this idempotent across cold restarts and
// re-installs.
//
// Result semantics:
//   - success → migration is terminal (either landed server-side, was a no-op,
//     or the token was rejected as invalid). The reconnect banner from the
//     android-reader linked=false gate handles the invalid-token UX.
//   - retry → transient (no network, no sign-in, callable timeout).
class XTokenMigrationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val entry = EntryPointAccessors.fromApplication(ctx, MigrationEntryPoint::class.java)
        return runXTokenMigration(ctx, entry.prefs(), entry.firebaseFunctions())
    }
}

// Extracted decision logic — exposed as a top-level suspend so Robolectric
// tests can exercise every branch without needing a Hilt-initialized
// application context.
internal suspend fun runXTokenMigration(
    ctx: Context,
    prefs: Prefs,
    functions: FirebaseFunctions,
): androidx.work.ListenableWorker.Result {
    val alreadyMigrated = ctx.readString(MigrationKeys.X_TOKEN_MIGRATED).first()
    if (alreadyMigrated == "true") {
        Timber.d("XTokenMigrationWorker: already migrated, skipping")
        return androidx.work.ListenableWorker.Result.success()
    }

    val refreshToken = prefs.refreshCode.first()
    if (refreshToken.isBlank()) {
        Timber.d("XTokenMigrationWorker: no legacy refresh token, marking migrated")
        ctx.writeString(MigrationKeys.X_TOKEN_MIGRATED, "true")
        return androidx.work.ListenableWorker.Result.success()
    }

    return try {
        val result = functions
            .getHttpsCallable("migrateXToken")
            .call(mapOf("refreshToken" to refreshToken))
            .await()
        val payload = result.data as? Map<*, *>
        val ok = payload?.get("ok") as? Boolean ?: false
        when {
            ok -> {
                prefs.clearAllTokens()
                ctx.writeString(MigrationKeys.X_TOKEN_MIGRATED, "true")
                Timber.d("XTokenMigrationWorker: migrated successfully")
                androidx.work.ListenableWorker.Result.success()
            }
            payload?.get("reason") == "invalid" -> {
                // Token is dead server-side; no retry value. The reconnect
                // banner is the user-visible UX path.
                ctx.writeString(MigrationKeys.X_TOKEN_MIGRATED, "true")
                Timber.d("XTokenMigrationWorker: invalid token, marking migrated")
                androidx.work.ListenableWorker.Result.success()
            }
            else -> {
                Timber.w("XTokenMigrationWorker: unexpected payload $payload, retrying")
                androidx.work.ListenableWorker.Result.retry()
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "XTokenMigrationWorker: transient failure, retrying")
        androidx.work.ListenableWorker.Result.retry()
    }
}
