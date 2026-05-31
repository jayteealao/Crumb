package com.github.jayteealao.crumbs.data

import android.app.Application
import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TwitterUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks in the feed recency sort: `ORDER BY retrieved_at DESC, created_at DESC` with NULL
 * `retrieved_at` sorted LAST (SQLite's default-DESC behaviour, not the version-gated NULLS LAST
 * keyword), and ties on `retrieved_at` broken by `created_at DESC`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Use a bare Application (not the production CrumbApplication) so this pure Room test does not
// pull in Hilt / Firebase init — it only needs a Context to build an in-memory database.
@Config(sdk = [34], application = Application::class)
class TweetDaoSortTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TweetDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tweetDao()
        dao.insertTwitterUser(
            TwitterUserEntity(
                id = "u1",
                name = "Tester",
                username = "tester",
                profileImageUrl = null,
                verified = false,
                verifiedType = null,
                description = null,
                mentionedIn = null,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tweet(id: String, retrievedAt: Long?, createdAt: String) = TweetEntity(
        id = id,
        text = "text-$id",
        createdAt = createdAt,
        authorId = "u1",
        conversationId = id,
        inReplyToUserId = null,
        lang = "en",
        retrievedAt = retrievedAt,
    )

    @Test
    fun feedSortsByRetrievedAtDescThenCreatedAtDesc_nullRetrievedAtLast() = runTest {
        // Insert deliberately out of final order.
        dao.insertTweet(tweet("null-older", retrievedAt = null, createdAt = "2026-01-01T00:00:00.000Z"))
        dao.insertTweet(tweet("null-newer", retrievedAt = null, createdAt = "2026-02-01T00:00:00.000Z"))
        dao.insertTweet(tweet("ret-low", retrievedAt = 1_000L, createdAt = "2020-01-01T00:00:00.000Z"))
        dao.insertTweet(tweet("ret-high", retrievedAt = 2_000L, createdAt = "2020-01-01T00:00:00.000Z"))
        dao.insertTweet(tweet("ret-tieEarlier", retrievedAt = 1_500L, createdAt = "2021-06-01T00:00:00.000Z"))
        dao.insertTweet(tweet("ret-tieLater", retrievedAt = 1_500L, createdAt = "2021-07-01T00:00:00.000Z"))

        val ids = loadAllIds(dao.getTweets())

        assertEquals(
            listOf(
                "ret-high",        // retrieved_at 2000
                "ret-tieLater",    // retrieved_at 1500, created_at 2021-07 (newer tiebreak first)
                "ret-tieEarlier",  // retrieved_at 1500, created_at 2021-06
                "ret-low",         // retrieved_at 1000
                "null-newer",      // null retrieved_at sorts last; created_at 2026-02 before 2026-01
                "null-older",
            ),
            ids,
        )
    }

    private suspend fun loadAllIds(source: PagingSource<Int, TweetData>): List<String> {
        val page = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        return page.data.map { it.tweet.id }
    }
}
