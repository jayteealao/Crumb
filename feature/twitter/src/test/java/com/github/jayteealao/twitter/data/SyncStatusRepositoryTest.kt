package com.github.jayteealao.twitter.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncStatusRepositoryTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var docRef: DocumentReference
    private lateinit var statusDoc: DocumentReference
    private lateinit var snapshot: DocumentSnapshot

    @Before
    fun setUp() {
        firestore = mockk()
        auth = mockk()
        docRef = mockk()
        statusDoc = mockk()
        snapshot = mockk()

        val user = mockk<FirebaseUser>()
        every { user.uid } returns "uid1"
        every { auth.currentUser } returns user

        val usersCollection = mockk<CollectionReference>()
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document("uid1") } returns docRef
        val syncStatusCollection = mockk<CollectionReference>()
        every { docRef.collection("sync_status") } returns syncStatusCollection
        every { syncStatusCollection.document("state") } returns statusDoc
        every { statusDoc.get(Source.SERVER) } returns Tasks.forResult(snapshot) as Task<DocumentSnapshot>
    }

    @Test
    fun refresh_returnsParsedSyncStatus() = runTest {
        every { snapshot.data } returns mapOf(
            "linked" to true,
            "lastError" to null,
            "itemsAdded" to 5L,
            "xUserId" to "xuid-123",
            "latest_tweet_id" to "9999",
        )

        val repo = SyncStatusRepository(firestore, auth)
        val result = repo.refresh()

        assertTrue(result?.linked == true)
        assertEquals("xuid-123", result?.xUserId)
        assertEquals(5, result?.itemsAdded)
        assertEquals("9999", result?.latestTweetId)
        // flow caches the parsed value
        assertEquals(result, repo.flow.value)
    }

    @Test
    fun refresh_throttlesWithinFiveSeconds() = runTest {
        every { snapshot.data } returns mapOf("linked" to true)

        val repo = SyncStatusRepository(firestore, auth)
        repo.refresh()
        repo.refresh() // second call within throttle window should be a no-op
        repo.refresh()

        // Exactly one server hit across three back-to-back calls.
        verify(exactly = 1) { statusDoc.get(Source.SERVER) }
    }

    @Test
    fun refresh_forceBypassesThrottle() = runTest {
        every { snapshot.data } returns mapOf("linked" to true)

        val repo = SyncStatusRepository(firestore, auth)
        repo.refresh()
        repo.refresh(force = true)

        verify(exactly = 2) { statusDoc.get(Source.SERVER) }
    }

    @Test
    fun refresh_returnsNullWhenUnauthenticated() = runTest {
        every { auth.currentUser } returns null
        val repo = SyncStatusRepository(firestore, auth)
        assertNull(repo.refresh())
        assertNull(repo.flow.value)
    }

    @Test
    fun parse_missingLinkedDefaultsFalse() = runTest {
        every { snapshot.data } returns mapOf("xUserId" to "x")
        val repo = SyncStatusRepository(firestore, auth)
        val result = repo.refresh()
        assertFalse(result?.linked ?: true)
    }
}
