package com.github.jayteealao.crumbs.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MediaBackfillWorker] focusing on the per-UID run-once
 * idempotency flag stored in SharedPreferences.
 *
 * [MediaBackfillWorker.doWork] calls [EntryPointAccessors.fromApplication] which
 * requires a live Hilt component and cannot be invoked in a unit test without a
 * full Hilt test harness. These tests therefore exercise the idempotency contract
 * directly via the private companion helpers (`isBackfillDone`, `markBackfillDone`,
 * `doneKey`) using reflection — the same pattern the [TwitterSyncWorkerTest] uses
 * for its `buildRequest` helper. The goal is to verify the load-bearing invariant:
 * per-UID key isolation, flag set ↔ done, flag absent ↔ not done.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaBackfillWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Wipe the SharedPreferences between tests so flag state is isolated.
        context.getSharedPreferences("media_backfill_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // Reflective accessors for the private companion helpers.

    private fun isBackfillDone(uid: String): Boolean {
        val method = MediaBackfillWorker.Companion::class.java
            .getDeclaredMethod("isBackfillDone", Context::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(MediaBackfillWorker.Companion, context, uid) as Boolean
    }

    private fun markBackfillDone(uid: String) {
        val method = MediaBackfillWorker.Companion::class.java
            .getDeclaredMethod("markBackfillDone", Context::class.java, String::class.java)
        method.isAccessible = true
        method.invoke(MediaBackfillWorker.Companion, context, uid)
    }

    private fun doneKey(uid: String): String {
        val method = MediaBackfillWorker.Companion::class.java
            .getDeclaredMethod("doneKey", String::class.java)
        method.isAccessible = true
        return method.invoke(MediaBackfillWorker.Companion, uid) as String
    }

    // -------------------------------------------------------------------------
    // 1. Flag absent → isBackfillDone returns false
    // -------------------------------------------------------------------------

    @Test
    fun isBackfillDone_whenFlagAbsent_returnsFalse() {
        assertFalse(
            "Flag must be absent by default so the worker runs on first launch",
            isBackfillDone("uid-a"),
        )
    }

    // -------------------------------------------------------------------------
    // 2. markBackfillDone → isBackfillDone returns true for the same UID
    // -------------------------------------------------------------------------

    @Test
    fun markBackfillDone_thenIsBackfillDone_returnsTrue() {
        assertFalse(isBackfillDone("uid-b"))
        markBackfillDone("uid-b")
        assertTrue(
            "After markBackfillDone the flag must be set for the same UID",
            isBackfillDone("uid-b"),
        )
    }

    // -------------------------------------------------------------------------
    // 3. Per-UID isolation: marking UID-A done does not mark UID-B done
    //    This is the load-bearing contract: a second account on the same device
    //    must still run its own backfill sweep.
    // -------------------------------------------------------------------------

    @Test
    fun markBackfillDone_forOneUid_doesNotMarkOtherUidDone() {
        markBackfillDone("uid-alpha")
        assertTrue("uid-alpha should be marked done", isBackfillDone("uid-alpha"))
        assertFalse(
            "uid-beta must remain unset when only uid-alpha was marked",
            isBackfillDone("uid-beta"),
        )
    }

    // -------------------------------------------------------------------------
    // 4. doneKey includes the UID so collisions cannot occur
    // -------------------------------------------------------------------------

    @Test
    fun doneKey_includesUidInKey() {
        val key = doneKey("uid-xyz")
        assertTrue(
            "The key must embed the UID to prevent cross-account collisions",
            key.contains("uid-xyz"),
        )
    }

    @Test
    fun doneKey_distinctUids_produceDistinctKeys() {
        val keyA = doneKey("uid-1")
        val keyB = doneKey("uid-2")
        assertTrue(
            "Different UIDs must produce different SharedPreferences keys",
            keyA != keyB,
        )
    }

    // -------------------------------------------------------------------------
    // 5. Second markBackfillDone call for same UID is idempotent
    // -------------------------------------------------------------------------

    @Test
    fun markBackfillDone_calledTwice_remainsTrue() {
        markBackfillDone("uid-c")
        markBackfillDone("uid-c")
        assertTrue(
            "Calling markBackfillDone twice for the same UID must leave the flag set",
            isBackfillDone("uid-c"),
        )
    }

    // -------------------------------------------------------------------------
    // 6. Generation bump: a device that completed the original (pre-generation)
    //    backfill carries only the legacy boolean flag. The v18→v19 media wipe needs
    //    it to re-pull ONCE, so it must read as NOT done until it completes the
    //    current generation. This is the post-wipe re-pull nudge.
    // -------------------------------------------------------------------------

    @Test
    fun legacyBooleanFlag_isTreatedAsGeneration1_andRerunsForCurrentGeneration() {
        // Simulate a legacy install: only the old boolean key is set, no generation int.
        context.getSharedPreferences("media_backfill_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(doneKey("uid-legacy"), true)
            .commit()

        assertFalse(
            "a legacy gen-1 install must re-run after the generation bump (post-wipe re-pull)",
            isBackfillDone("uid-legacy"),
        )

        // Completing the sweep stamps the current generation; it is then done.
        markBackfillDone("uid-legacy")
        assertTrue(
            "after completing the current generation the worker must short-circuit again",
            isBackfillDone("uid-legacy"),
        )
    }
}
