package com.github.jayteealao.crumbs

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CrumbApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
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
    }
}
