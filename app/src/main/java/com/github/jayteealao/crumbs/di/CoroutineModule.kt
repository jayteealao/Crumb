package com.github.jayteealao.crumbs.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CoroutineModule {

    @Singleton
    @Provides
    fun providesCoroutineScope(): CoroutineScope {
        // SupervisorJob: one child's failure does not cancel sibling syncs.
        // CoroutineExceptionHandler: surface uncaught exceptions to Timber so
        // a transient network error in Repository.init does not silently kill
        // background sync for the process lifetime.
        val handler = CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Uncaught exception in application coroutine scope")
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }
}
