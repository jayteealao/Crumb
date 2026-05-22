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
            else -> Timber.w("DebugIntentHandler: unknown debug_action=%s", action)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugInjectorEntryPoint {
    fun debugDataInjector(): DebugDataInjector
}
