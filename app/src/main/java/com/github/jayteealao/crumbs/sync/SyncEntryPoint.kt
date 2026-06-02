package com.github.jayteealao.crumbs.sync

import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.SyncProgressDao
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.twitter.data.Repository
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.data.firestore.FirestoreRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point read by [TwitterSyncWorker] via `EntryPointAccessors`.
 * Mirrors the precedent set by `MigrationEntryPoint` — avoids pulling in
 * `androidx.hilt:hilt-work` + a custom `WorkerFactory` for the second worker.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun authGateway(): AuthGateway
    fun appDatabase(): AppDatabase
    fun tweetDao(): TweetDao
    fun syncProgressDao(): SyncProgressDao
    fun firestoreRepository(): FirestoreRepository
    fun deletedBookmarkRepository(): DeletedBookmarkRepository
    // Read by MediaBackfillWorker — reuses Repository.refetchTweetMedia for the
    // idempotent fetch + IGNORE insert of each legacy tweet's missing media.
    fun repository(): Repository
}
