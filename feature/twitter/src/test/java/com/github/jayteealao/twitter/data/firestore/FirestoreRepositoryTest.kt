package com.github.jayteealao.twitter.data.firestore

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.AggregateQuery
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct (non-facade) coverage for [FirestoreRepository.getServerBookmarkCount] — the
 * `net = total - referenced - deleted` aggregate-count netting introduced alongside the
 * reconciliation feature (the `deleted` subtraction is the DI-10 fix). The three
 * `CollectionReference/Query.count()` aggregate RPCs are mocked individually so the
 * subtraction itself — not just the mocked facade in [SyncReconcilerTest] — is exercised.
 *
 * Robolectric is required only because the Firestore SDK touches Android internals during
 * class loading (same rationale as [SyncStatusRepositoryTest]); no Activity or UI involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreRepositoryTest {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var tweetsCollection: CollectionReference
    private lateinit var repository: FirestoreRepository

    @Before
    fun setUp() {
        db = mockk()
        auth = mockk()
        val user = mockk<FirebaseUser>()
        every { user.uid } returns "uid1"
        every { auth.currentUser } returns user

        val usersCollection = mockk<CollectionReference>()
        val userDoc = mockk<DocumentReference>()
        tweetsCollection = mockk()
        every { db.collection("users") } returns usersCollection
        every { usersCollection.document("uid1") } returns userDoc
        every { userDoc.collection("tweets") } returns tweetsCollection

        repository = FirestoreRepository(db, auth)
    }

    /** Stubs `query.count().get(AggregateSource.SERVER).await().count == value`. */
    private fun stubCount(query: Query, value: Long) {
        val aggQuery = mockk<AggregateQuery>()
        val snapshot = mockk<AggregateQuerySnapshot>()
        every { query.count() } returns aggQuery
        every { aggQuery.get(AggregateSource.SERVER) } returns Tasks.forResult(snapshot)
        every { snapshot.count } returns value
    }

    @Test
    fun getServerBookmarkCount_netsTotalMinusReferencedMinusDeleted() = runTest {
        val referencedQuery = mockk<Query>()
        val deletedQuery = mockk<Query>()
        every { tweetsCollection.whereEqualTo("referenced", true) } returns referencedQuery
        every { tweetsCollection.whereEqualTo("deleted", true) } returns deletedQuery

        stubCount(tweetsCollection, 1000L)
        stubCount(referencedQuery, 120L)
        stubCount(deletedQuery, 30L)

        val result = repository.getServerBookmarkCount()

        assertTrue(result.isSuccess)
        // 1000 - 120 - 30 = 850 — proves both the referenced AND the deleted subtraction
        // (the latter is the DI-10 fix; a regression back to `total - referenced` would
        // report 880 here instead).
        assertEquals(850L, result.getOrNull())
    }

    @Test
    fun getServerBookmarkCount_zeroReferencedAndDeleted_netsToTotal() = runTest {
        val referencedQuery = mockk<Query>()
        val deletedQuery = mockk<Query>()
        every { tweetsCollection.whereEqualTo("referenced", true) } returns referencedQuery
        every { tweetsCollection.whereEqualTo("deleted", true) } returns deletedQuery

        stubCount(tweetsCollection, 500L)
        stubCount(referencedQuery, 0L)
        stubCount(deletedQuery, 0L)

        val result = repository.getServerBookmarkCount()

        assertEquals(500L, result.getOrNull())
    }

    @Test
    fun getServerBookmarkCount_aggregateFailure_returnsFailureResult() = runTest {
        every { tweetsCollection.count() } throws RuntimeException("boom")

        val result = repository.getServerBookmarkCount()

        assertTrue("a failed aggregate query must surface as Result.failure so the caller can reset the reconcile gate", result.isFailure)
    }
}
