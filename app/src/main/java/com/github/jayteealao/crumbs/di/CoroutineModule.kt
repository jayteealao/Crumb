package com.github.jayteealao.crumbs.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
class CoroutineModule {
    @Provides
    fun providesCoroutineScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.IO)
    }
}
