package com.github.jayteealao.crumbs.sync

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.github.jayteealao.crumbs.MainActivity
import com.github.jayteealao.crumbs.R

/**
 * Single source of truth for every bookmark-sync notification: the two channels,
 * the small icon, the notification ids, the tap-to-open [PendingIntent], and the
 * builders for the ongoing foreground progress, the non-foreground backfill
 * progress, and the terminal success/error alerts.
 *
 * Centralizing this here means [TwitterSyncWorker], [MediaBackfillWorker], and
 * [com.github.jayteealao.crumbs.CrumbApplication] all share one icon/channel/id
 * definition instead of redeclaring it — the previous inline duplication is what
 * let the small icon drift to a full-color launcher mipmap (rendered as a white
 * square; see [R.drawable.ic_sync_notification]).
 *
 * Standalone `notify()` calls (backfill + terminal) require POST_NOTIFICATIONS on
 * API 33+; the ongoing *foreground-service* notification is exempt. The post
 * helpers below gate on [canPostNotifications] so a missing grant degrades
 * gracefully (the sync still runs, just without a drawer entry) rather than
 * crashing or silently throwing.
 */
object SyncNotifications {
    /** Low-importance channel for the ongoing/determinate progress notifications (no sound). */
    const val CHANNEL_PROGRESS = "twitter_sync_progress"

    /** Default-importance channel for the one-shot terminal success/error alerts. */
    const val CHANNEL_ALERTS = "twitter_sync_alerts"

    /** Ongoing foreground-service progress notification (cold-start drain). Reused id. */
    const val ID_FOREGROUND = 4242

    /** Non-foreground legacy-backfill progress notification. */
    const val ID_BACKFILL = 4243

    /** One-shot terminal success/error alert. */
    const val ID_TERMINAL = 4244

    // User-facing copy. Centralized here (matching the existing inline-string
    // convention) so every consumer shares one wording.
    private const val TITLE_SYNCING = "Syncing your bookmarks"
    private const val TITLE_BACKFILL = "Restoring older bookmarks"
    private const val TITLE_COMPLETE = "Bookmarks updated"
    private const val TITLE_FAILED = "Bookmark sync paused"
    private const val TEXT_LOADING = "Loading…"
    private const val TEXT_BACKFILL = "Catching up on media and links…"
    private const val TEXT_ERROR = "Couldn't finish syncing. We'll try again automatically."

    /**
     * Register both notification channels. Idempotent — safe to call on every
     * cold start. Sourced from one place so the channel ids never drift from the
     * ids the builders below target.
     */
    fun registerChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val progress =
            NotificationChannelCompat
                .Builder(
                    CHANNEL_PROGRESS,
                    NotificationManagerCompat.IMPORTANCE_LOW,
                ).setName("Bookmark sync")
                .setDescription("Progress while syncing your X bookmarks")
                .build()
        val alerts =
            NotificationChannelCompat
                .Builder(
                    CHANNEL_ALERTS,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT,
                ).setName("Bookmark sync alerts")
                .setDescription("Tells you when a bookmark sync finishes or fails")
                .build()
        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(alerts)
    }

    /**
     * Tap target for every sync notification: launches [MainActivity], which routes
     * to the bookmark feed. `FLAG_IMMUTABLE` is mandatory on API 31+ (supported
     * from API 23, so safe at minSdk 24); `FLAG_UPDATE_CURRENT` keeps a single
     * cached intent fresh.
     */
    fun contentPendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Ongoing progress notification used when the main sync is promoted to a
     * foreground service. `batchTotal == 0` (or `batchIdx == 0`) renders an
     * indeterminate bar; otherwise it's bounded ("Batch N of M").
     */
    fun foregroundInfo(
        context: Context,
        batchIdx: Int,
        batchTotal: Int,
    ): ForegroundInfo {
        val contentText =
            when {
                batchTotal > 0 -> "Batch $batchIdx of $batchTotal"
                batchIdx > 0 -> "Batch $batchIdx"
                else -> TEXT_LOADING
            }
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_PROGRESS)
                .setContentTitle(TITLE_SYNCING)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_sync_notification)
                .setContentIntent(contentPendingIntent(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply {
                    if (batchTotal > 0) setProgress(batchTotal, batchIdx, false) else setProgress(0, 0, true)
                }.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(ID_FOREGROUND, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_FOREGROUND, notification)
        }
    }

    /**
     * Non-foreground progress notification for the legacy media/link/quote backfill
     * sweep. The total tweet count is unknown until each sweep drains, so this shows
     * an indeterminate bar with a running processed count. Not ongoing — the user can
     * dismiss it, and best-effort backfill loss on process death is acceptable.
     */
    fun backfillProgress(
        context: Context,
        processed: Int,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_PROGRESS)
            .setContentTitle(TITLE_BACKFILL)
            .setContentText(TEXT_BACKFILL)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentIntent(contentPendingIntent(context))
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, processed, true)
            .build()

    /** Singular/plural copy for the terminal success notification. */
    fun syncedCountText(count: Int): String = if (count == 1) "Synced 1 bookmark" else "Synced $count bookmarks"

    /** Terminal "sync complete" alert. Auto-cancels on tap; not ongoing. */
    fun terminalSuccess(
        context: Context,
        count: Int,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_ALERTS)
            .setContentTitle(TITLE_COMPLETE)
            .setContentText(syncedCountText(count))
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentIntent(contentPendingIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    /** Terminal "sync failed" alert. Auto-cancels on tap; not ongoing. */
    fun terminalError(context: Context): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_ALERTS)
            .setContentTitle(TITLE_FAILED)
            .setContentText(TEXT_ERROR)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentIntent(contentPendingIntent(context))
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    // ---- gated post/cancel helpers (standalone notify needs POST_NOTIFICATIONS) ----
    //
    // The POST_NOTIFICATIONS check is inlined at each notify() site (rather than
    // delegated to a shared helper) so it both degrades gracefully on a missing
    // grant AND is recognized by lint's `NotificationPermission` check, which only
    // honors a check that lexically guards the call. Below API 33 the grant is
    // implicit, so the short-circuit lets the notification through.

    fun notifyBackfillProgress(
        context: Context,
        processed: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(ID_BACKFILL, backfillProgress(context, processed))
    }

    fun cancelBackfill(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_BACKFILL)
    }

    fun notifyTerminalSuccess(
        context: Context,
        count: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(ID_TERMINAL, terminalSuccess(context, count))
    }

    fun notifyTerminalError(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(ID_TERMINAL, terminalError(context))
    }
}
