package com.github.jayteealao.crumbs.sync

import com.github.jayteealao.twitter.data.TwitterSyncEnqueuer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindTwitterSyncEnqueuer(impl: TwitterSyncEnqueuerImpl): TwitterSyncEnqueuer
}
