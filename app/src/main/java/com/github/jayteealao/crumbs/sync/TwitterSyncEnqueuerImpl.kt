package com.github.jayteealao.crumbs.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.twitter.data.TwitterSyncEnqueuer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-side implementation of the `feature/twitter` enqueuer seam. Encapsulates
 * the `WorkManager.enqueueUniqueWork(uniqueName, KEEP, request)` call so the
 * feature module never imports `androidx.work` directly.
 *
 * No-ops if the user is not signed in — the eventual sign-in completion path
 * (see `FirebaseAuthGateway`) fires its own cold-start enqueue.
 */
@Singleton
class TwitterSyncEnqueuerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authGateway: AuthGateway,
) : TwitterSyncEnqueuer {

    override fun enqueueColdStart() {
        enqueue(runAsForegroundService = true, tag = "cold_start")
    }

    override fun enqueueRefresh() {
        enqueue(runAsForegroundService = false, tag = "refresh")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeIsRunning(): Flow<Boolean> {
        return authGateway.currentUser.flatMapLatest { user ->
            val uid = user?.uid
            if (uid.isNullOrEmpty()) {
                flowOf(false)
            } else {
                runCatching {
                    val workManager = WorkManager.getInstance(context)
                    val flowable = workManager.getWorkInfosForUniqueWorkFlow(
                        TwitterSyncWorker.uniqueName(uid),
                    )
                    flowable.map { infos ->
                        infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                    }
                }.getOrElse {
                    Timber.tag("IncrementalSync").w(it, "observeIsRunning failed (test env?)")
                    flowOf(false)
                }
            }
        }
    }

    private fun enqueue(runAsForegroundService: Boolean, tag: String) {
        val uid = authGateway.currentUser.value?.uid
        if (uid.isNullOrEmpty()) {
            Timber.tag("IncrementalSync").d("enqueue_skipped tag=$tag reason=no_uid")
            return
        }
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                TwitterSyncWorker.uniqueName(uid),
                ExistingWorkPolicy.KEEP,
                TwitterSyncWorker.buildRequest(uid, runAsForegroundService),
            )
            Timber.tag("IncrementalSync").d("enqueue_ok tag=$tag uid=$uid fg=$runAsForegroundService")
        } catch (e: Exception) {
            Timber.tag("IncrementalSync").w(e, "enqueue_failed tag=$tag uid=$uid (likely test env)")
        }
    }
}
