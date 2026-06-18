package com.github.jayteealao.crumbs.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
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
 * Locks in `TweetDao.getTweetsWithoutMedia` — the query the one-time
 * `MediaBackfillWorker` sweeps: it returns only non-referenced, non-tombstoned
 * tweets that have no `tweetMedia` rows, keyset-paginated by id so a media-less
 * tweet is visited exactly once per sweep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Bare Application (not CrumbApplication) so this pure Room test does not pull in
// Hilt / Firebase init — it only needs a Context to build an in-memory database.
@Config(sdk = [34], application = Application::class)
class TweetDaoMediaTest {

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

    private fun tweet(id: String, referenced: Boolean = false) = TweetEntity(
        id = id,
        text = "text-$id",
        createdAt = "2026-01-01T00:00:00.000Z",
        authorId = "u1",
        conversationId = id,
        inReplyToUserId = null,
        lang = "en",
        referenced = referenced,
    )

    private fun photo(mediaKey: String, tweetId: String) = TweetMediaEntity(
        mediaKey = mediaKey,
        type = "photo",
        url = "https://img/$mediaKey.jpg",
        durationMs = 0,
        height = 0,
        width = 0,
        previewImageUrl = null,
        altText = null,
        tweetId = tweetId,
    )

    @Test
    fun returnsOnlyMediaLessNonReferencedTweets() = runTest {
        dao.insertTweet(tweet("a-withmedia"))
        dao.insertTweet(tweet("b-nomedia"))
        dao.insertTweet(tweet("c-nomedia"))
        dao.insertTweet(tweet("d-referenced", referenced = true))
        dao.insertTweetMedia(photo("m1", "a-withmedia"))

        val ids = dao.getTweetsWithoutMedia(afterId = "", limit = 50)

        assertEquals(listOf("b-nomedia", "c-nomedia"), ids)
    }

    @Test
    fun keysetPaginationAdvancesPastTheCursor() = runTest {
        dao.insertTweet(tweet("id-1"))
        dao.insertTweet(tweet("id-2"))
        dao.insertTweet(tweet("id-3"))

        val firstPage = dao.getTweetsWithoutMedia(afterId = "", limit = 2)
        assertEquals(listOf("id-1", "id-2"), firstPage)

        val secondPage = dao.getTweetsWithoutMedia(afterId = firstPage.last(), limit = 2)
        assertEquals(listOf("id-3"), secondPage)
    }
}
