package com.github.jayteealao.crumbs.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.jayteealao.pref.readString
import com.github.jayteealao.pref.writeString
import com.github.jayteealao.twitter.data.Prefs
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the five branches of [runXTokenMigration]:
 *  1. already migrated → no-op success
 *  2. no legacy token → marks migrated + success
 *  3. callable ok=true → clears Prefs + marks migrated + success
 *  4. callable ok=false reason=invalid → marks migrated + success (no retry)
 *  5. network exception → retry, no Prefs mutation
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XTokenMigrationWorkerTest {

    private lateinit var context: Context
    private lateinit var prefs: Prefs
    private lateinit var functions: FirebaseFunctions
    private lateinit var callable: HttpsCallableReference

    private val refreshFlow = MutableStateFlow("")

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        // CrumbApplication.onCreate enqueues a real WorkManager request; init
        // the test scheduler so the enqueue resolves to an in-memory backend.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        context.writeString(MigrationKeys.X_TOKEN_MIGRATED, "")

        prefs = mockk(relaxed = true)
        functions = mockk()
        callable = mockk()
        every { functions.getHttpsCallable("migrateXToken") } returns callable
        every { prefs.refreshCode } returns refreshFlow
        coEvery { prefs.clearAllTokens() } just Runs
    }

    private fun stubCallableResult(data: Any?) {
        val result = mockk<HttpsCallableResult>(relaxed = true)
        every { result.getData() } returns data
        every { callable.call(any()) } returns Tasks.forResult(result)
    }

    @Test
    fun alreadyMigrated_returnsSuccess_withoutCallingFirebase() = runTest {
        context.writeString(MigrationKeys.X_TOKEN_MIGRATED, "true")
        refreshFlow.value = "rt-stale"

        val result = runXTokenMigration(context, prefs, functions)

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { functions.getHttpsCallable(any()) }
    }

    @Test
    fun noLegacyToken_marksMigrated_returnsSuccess() = runTest {
        refreshFlow.value = ""

        val result = runXTokenMigration(context, prefs, functions)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("true", context.readString(MigrationKeys.X_TOKEN_MIGRATED).first())
        verify(exactly = 0) { functions.getHttpsCallable(any()) }
    }

    @Test
    fun callableOk_clearsPrefs_marksMigrated_returnsSuccess() = runTest {
        refreshFlow.value = "rt-fresh"
        stubCallableResult(mapOf("ok" to true))

        val result = runXTokenMigration(context, prefs, functions)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("true", context.readString(MigrationKeys.X_TOKEN_MIGRATED).first())
        coVerify(exactly = 1) { prefs.clearAllTokens() }
    }

    @Test
    fun callableInvalid_marksMigrated_doesNotClearPrefs_returnsSuccess() = runTest {
        refreshFlow.value = "rt-bogus"
        stubCallableResult(mapOf("ok" to false, "reason" to "invalid"))

        val result = runXTokenMigration(context, prefs, functions)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("true", context.readString(MigrationKeys.X_TOKEN_MIGRATED).first())
        coVerify(exactly = 0) { prefs.clearAllTokens() }
    }

    @Test
    fun callableThrows_returnsRetry_doesNotMarkMigrated() = runTest {
        refreshFlow.value = "rt-fresh"
        every { callable.call(any()) } returns Tasks.forException(java.io.IOException("ETIMEDOUT"))

        val result = runXTokenMigration(context, prefs, functions)

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("", context.readString(MigrationKeys.X_TOKEN_MIGRATED).first())
        coVerify(exactly = 0) { prefs.clearAllTokens() }
    }
}
