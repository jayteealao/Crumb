package com.github.jayteealao.crumbs

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.github.jayteealao.crumbs.migration.XTokenMigrationWorker
import com.github.jayteealao.crumbs.sync.SyncEntryPoint
import com.github.jayteealao.crumbs.sync.TwitterSyncWorker
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CrumbApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        registerSyncNotificationChannel()

        // One-shot upload-and-clear of the legacy X refresh token. KEEP policy
        // + the worker's internal idempotency flag ensure this is a true
        // singleton across the install lifetime.
        // try/catch so Robolectric (where WorkManager isn't auto-initialized
        // until the test harness sets it up) doesn't blow up Application
        // construction; production cold-starts always have the
        // androidx.startup-registered WorkManagerInitializer available.
        try {
            val migrationRequest = OneTimeWorkRequestBuilder<XTokenMigrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(this)
                .enqueueUniqueWork(MIGRATION_WORK_NAME, ExistingWorkPolicy.KEEP, migrationRequest)
        } catch (e: IllegalStateException) {
            Timber.w(e, "WorkManager not initialized; skipping migration enqueue")
        }

        // Cold-start Twitter bookmark sync. Gated on the user already being
        // signed in (a fresh sign-in fires its own enqueue via the auth
        // gateway). KEEP policy coalesces against any worker still alive from
        // a prior process; the worker promotes itself to a foreground service
        // so a large backlog drain survives backgrounding.
        try {
            val sync = EntryPointAccessors.fromApplication(this, SyncEntryPoint::class.java)
            sync.authGateway().currentUser.value?.uid?.let { uid ->
                WorkManager.getInstance(this).enqueueUniqueWork(
                    TwitterSyncWorker.uniqueName(uid),
                    ExistingWorkPolicy.KEEP,
                    TwitterSyncWorker.buildRequest(uid, runAsForegroundService = true),
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "TwitterSyncWorker initial enqueue failed (likely Robolectric or pre-auth)")
        }
    }

    private fun registerSyncNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            TwitterSyncWorker.NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName("Bookmark sync")
            .setDescription("Progress while syncing your X bookmarks")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    // Project-wide Coil singleton. Crossfade smooths the placeholder→image
    // swap (PERF-07), and explicit memory/disk caches bound RAM/storage so
    // the brutalist feed does not balloon when scrolled aggressively.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        // The duration-overload already enables crossfade — the boolean
        // overload above was redundant.
        .crossfade(CROSSFADE_DURATION_MS)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(MEMORY_CACHE_FRACTION)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizePercent(DISK_CACHE_FRACTION)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        // Brutalist UI swaps backgrounds and accents per theme — honoring
        // upstream cache-control headers would force redownloads on every
        // theme flip. We trust our cache keys instead.
        .respectCacheHeaders(false)
        .build()

    private companion object {
        const val CROSSFADE_DURATION_MS = 180
        const val MEMORY_CACHE_FRACTION = 0.20
        const val DISK_CACHE_FRACTION = 0.02
        const val MIGRATION_WORK_NAME = "x-token-migration"
    }
}
