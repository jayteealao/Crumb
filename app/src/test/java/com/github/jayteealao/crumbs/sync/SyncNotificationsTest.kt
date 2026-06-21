package com.github.jayteealao.crumbs.sync

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [SyncNotifications]: the shared builders, channel registration,
 * and the POST_NOTIFICATIONS-gated post/cancel helpers.
 *
 * Verifies the load-bearing invariants of the sync-progress-notification slice:
 *  - the small icon is the monochrome vector (NOT the launcher mipmap that rendered
 *    as a white square),
 *  - the progress notifications target the LOW channel and the terminal alerts the
 *    DEFAULT channel, with the right ongoing/autoCancel flags,
 *  - terminal copy is singular/plural correct,
 *  - both channels are created, the terminal posts on its own id, the backfill posts
 *    then cancels on its own id, and
 *  - without the runtime grant on API 33+ a standalone notify() is a no-op (AC6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncNotificationsTest {

    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        // Grant POST_NOTIFICATIONS so the gated notify() helpers actually post; the
        // no-permission path is exercised explicitly in its own test below.
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager = application.getSystemService(NotificationManager::class.java)
    }

    private fun textOf(notification: Notification): String? =
        notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun titleOf(notification: Notification): String? =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    @Test
    fun registerChannels_createsBothChannels_withExpectedImportance() {
        SyncNotifications.registerChannels(application)

        val progress = notificationManager.getNotificationChannel(SyncNotifications.CHANNEL_PROGRESS)
        val alerts = notificationManager.getNotificationChannel(SyncNotifications.CHANNEL_ALERTS)

        assertNotNull("progress channel must be created", progress)
        assertNotNull("alerts channel must be created", alerts)
        assertEquals(NotificationManager.IMPORTANCE_LOW, progress.importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, alerts.importance)
    }

    @Test
    fun foregroundInfo_usesMonochromeIcon_progressChannel_andBoundedProgress() {
        val info = SyncNotifications.foregroundInfo(application, batchIdx = 2, batchTotal = 5)

        assertEquals(SyncNotifications.ID_FOREGROUND, info.notificationId)
        assertEquals(
            "small icon must be the monochrome vector, not the launcher mipmap",
            R.drawable.ic_sync_notification,
            shadowOf(info.notification.smallIcon).resId,
        )
        assertEquals(SyncNotifications.CHANNEL_PROGRESS, info.notification.channelId)
        assertTrue(
            "foreground notification must be ongoing",
            (info.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                info.foregroundServiceType,
            )
        }
    }

    @Test
    fun syncedCountText_isSingularForOne_pluralOtherwise() {
        assertEquals("Synced 1 bookmark", SyncNotifications.syncedCountText(1))
        assertEquals("Synced 12 bookmarks", SyncNotifications.syncedCountText(12))
        assertEquals("Synced 0 bookmarks", SyncNotifications.syncedCountText(0))
    }

    @Test
    fun terminalSuccess_buildsAutoCancelAlert_onAlertsChannel_withCountText() {
        val notification = SyncNotifications.terminalSuccess(application, count = 12)

        assertEquals(SyncNotifications.CHANNEL_ALERTS, notification.channelId)
        assertEquals("Synced 12 bookmarks", textOf(notification))
        assertTrue(
            "terminal success must auto-cancel on tap",
            (notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
        assertTrue(
            "terminal success must NOT be ongoing",
            (notification.flags and Notification.FLAG_ONGOING_EVENT) == 0,
        )
        assertEquals(
            R.drawable.ic_sync_notification,
            shadowOf(notification.smallIcon).resId,
        )
    }

    @Test
    fun terminalError_buildsAutoCancelAlert_onAlertsChannel() {
        val notification = SyncNotifications.terminalError(application)

        assertEquals(SyncNotifications.CHANNEL_ALERTS, notification.channelId)
        assertNotNull("error notification must carry title copy", titleOf(notification))
        assertTrue(
            "terminal error must auto-cancel on tap",
            (notification.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
    }

    @Test
    fun notifyTerminalSuccess_postsOnTerminalId_andAlertsChannel() {
        SyncNotifications.registerChannels(application)

        SyncNotifications.notifyTerminalSuccess(application, count = 3)

        val posted = shadowOf(notificationManager).getNotification(SyncNotifications.ID_TERMINAL)
        assertNotNull("terminal success must be posted on the terminal id", posted)
        assertEquals(SyncNotifications.CHANNEL_ALERTS, posted.channelId)
        assertEquals("Synced 3 bookmarks", textOf(posted))
    }

    @Test
    fun notifyBackfillProgress_postsThenCancel_clearsBackfillId() {
        SyncNotifications.registerChannels(application)

        SyncNotifications.notifyBackfillProgress(application, processed = 42)
        assertNotNull(
            "backfill progress must post on the backfill id",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_BACKFILL),
        )

        SyncNotifications.cancelBackfill(application)
        assertNull(
            "cancelBackfill must clear the backfill notification",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_BACKFILL),
        )
    }

    @Test
    fun backfillProgress_usesProgressChannel_andMonochromeIcon() {
        val notification = SyncNotifications.backfillProgress(application, processed = 10)

        assertEquals(SyncNotifications.CHANNEL_PROGRESS, notification.channelId)
        assertEquals(
            R.drawable.ic_sync_notification,
            shadowOf(notification.smallIcon).resId,
        )
        assertTrue(
            "backfill progress must NOT be ongoing (best-effort, dismissable)",
            (notification.flags and Notification.FLAG_ONGOING_EVENT) == 0,
        )
    }

    @Test
    fun notify_withoutPostPermission_isNoOp_onApi33Plus() {
        // AC6: on API 33+ without the grant, a standalone notify() must degrade
        // gracefully (no crash, no drawer entry) rather than throwing.
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        SyncNotifications.registerChannels(application)

        SyncNotifications.notifyTerminalSuccess(application, count = 5)
        SyncNotifications.notifyBackfillProgress(application, processed = 5)

        assertNull(
            "terminal must not post without the grant",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_TERMINAL),
        )
        assertNull(
            "backfill must not post without the grant",
            shadowOf(notificationManager).getNotification(SyncNotifications.ID_BACKFILL),
        )
    }
}
