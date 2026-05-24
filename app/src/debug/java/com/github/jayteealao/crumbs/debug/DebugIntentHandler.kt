package com.github.jayteealao.crumbs.debug

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Plain object dispatched reflectively from MainActivity in debug builds only.
 * Reads `intent.extras` populated by Maestro's `launchApp.arguments:` directive
 * and routes to DebugDataInjector.
 *
 * MainActivity invokes via Java reflection — keep this signature stable:
 *   handleIntent(activity: ComponentActivity, intent: Intent?)
 */
object DebugIntentHandler {

    private const val ACTION_SEED = "seed"
    private const val ACTION_WIPE = "wipe"
    private const val ACTION_CORRUPT_TOKEN = "corrupt_token"
    private const val ACTION_SEED_SYNC_STATUS = "seed_sync_status"
    private const val ACTION_SEED_PENDING_DELETE = "seed_pending_delete"
    private const val ACTION_SEED_LEGACY_X_TOKENS = "seed_legacy_x_tokens"
    private const val ACTION_SEED_INCREMENTAL_SYNC_CORPUS = "seed_incremental_sync_corpus"
    private const val ACTION_SEED_PARTIAL_SYNC_PROGRESS = "seed_partial_sync_progress"

    @JvmStatic
    fun handleIntent(activity: ComponentActivity, intent: Intent?) {
        val action = intent?.getStringExtra("debug_action") ?: return
        val injector = EntryPointAccessors
            .fromApplication(activity.application, DebugInjectorEntryPoint::class.java)
            .debugDataInjector()
        when (action) {
            ACTION_SEED -> {
                val wipe = intent.getBooleanExtra("wipe", false)
                activity.lifecycleScope.launch {
                    runCatching { injector.run(wipe = wipe) }
                        .onFailure { Timber.e(it, "DebugDataInjector.run failed") }
                }
            }
            ACTION_WIPE -> {
                activity.lifecycleScope.launch {
                    runCatching { injector.run(wipe = true) }
                        .onFailure { Timber.e(it, "DebugDataInjector.run(wipe=true) failed") }
                }
            }
            ACTION_CORRUPT_TOKEN -> {
                activity.lifecycleScope.launch {
                    runCatching { injector.corruptTwitterToken() }
                        .onFailure { Timber.e(it, "DebugDataInjector.corruptTwitterToken failed") }
                }
            }
            ACTION_SEED_SYNC_STATUS -> {
                val linked = intent.getStringExtra("linked")?.equals("true", ignoreCase = true) ?: false
                runCatching { injector.seedSyncStatus(linked = linked) }
                    .onFailure { Timber.e(it, "DebugDataInjector.seedSyncStatus failed") }
            }
            ACTION_SEED_PENDING_DELETE -> {
                activity.lifecycleScope.launch {
                    runCatching { injector.seedPendingDelete() }
                        .onFailure { Timber.e(it, "DebugDataInjector.seedPendingDelete failed") }
                }
            }
            ACTION_SEED_LEGACY_X_TOKENS -> {
                activity.lifecycleScope.launch {
                    runCatching { injector.seedLegacyXTokens() }
                        .onFailure { Timber.e(it, "DebugDataInjector.seedLegacyXTokens failed") }
                }
            }
            ACTION_SEED_INCREMENTAL_SYNC_CORPUS -> {
                val tweetCount = intent.getStringExtra("tweet_count")?.toIntOrNull() ?: 75
                activity.lifecycleScope.launch {
                    runCatching { injector.seedIncrementalSyncCorpus(tweetCount) }
                        .onFailure { Timber.e(it, "DebugDataInjector.seedIncrementalSyncCorpus failed") }
                }
            }
            ACTION_SEED_PARTIAL_SYNC_PROGRESS -> {
                val batchK = intent.getStringExtra("batch_k")?.toIntOrNull() ?: 3
                activity.lifecycleScope.launch {
                    runCatching { injector.seedPartialSyncProgress(batchK) }
                        .onFailure { Timber.e(it, "DebugDataInjector.seedPartialSyncProgress failed") }
                }
            }
            else -> Timber.w("DebugIntentHandler: unknown debug_action=%s", action)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugInjectorEntryPoint {
    fun debugDataInjector(): DebugDataInjector
}
