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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks the media @Relation contract behind both media-attribution defects.
 * `TweetData.media` is `@Relation(parentColumn = "id", entityColumn = "tweet_id")`, so a
 * media row's `tweet_id` MUST equal its parent tweet id for the relation to resolve.
 *
 * 1. A correct `tweet_id` resolves the relation (the rendering fix).
 * 2. A media_key SHARED across two tweets attaches to BOTH owners under the composite
 *    `(tweet_id, media_key)` PK — the wrong-media-attached fix. Under the old sole-`media_key`
 *    PK the second insert collided and the asset collapsed onto one arbitrary tweet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Bare Application (not CrumbApplication) so this pure Room test does not pull in
// Hilt / Firebase init — it only needs a Context to build an in-memory database.
@Config(sdk = [34], application = Application::class)
class TweetDaoMediaRelationTest {

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

    private fun tweet(id: String) = TweetEntity(
        id = id,
        text = "text-$id",
        createdAt = "2026-01-01T00:00:00.000Z",
        authorId = "u1",
        conversationId = id,
        inReplyToUserId = null,
        lang = "en",
        referenced = false,
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
    fun mediaRowWithCorrectTweetId_resolvesTheRelation() = runTest {
        dao.insertTweet(tweet("t1"))
        dao.insertTweetMedia(photo("mk1", tweetId = "t1"))

        val data = dao.getTweetById("t1")

        assertNotNull("tweet should be fetchable", data)
        assertEquals("media relation should contain the inserted row", 1, data!!.media.size)
        assertEquals("mk1", data.media.single().mediaKey)
    }

    @Test
    fun sharedMediaKey_resolvesIntoBothOwnerTweets() = runTest {
        // The wrong-media-attached regression: one media_key belongs to two tweets (a
        // quote/co-page asset). Under the composite (tweet_id, media_key) PK both rows
        // coexist, so the @Relation resolves the asset onto EACH owner — not collapsed
        // onto one. The old sole-media_key PK rejected the second insert.
        dao.insertTweet(tweet("t1"))
        dao.insertTweet(tweet("t2"))
        dao.insertTweetMedia(photo("mk-shared", tweetId = "t1"))
        dao.insertTweetMedia(photo("mk-shared", tweetId = "t2"))

        val d1 = dao.getTweetById("t1")
        val d2 = dao.getTweetById("t2")

        assertNotNull(d1)
        assertNotNull(d2)
        assertEquals("t1 must own the shared asset", 1, d1!!.media.size)
        assertEquals("mk-shared", d1.media.single().mediaKey)
        assertEquals("t2 must independently own the same asset", 1, d2!!.media.size)
        assertEquals("mk-shared", d2.media.single().mediaKey)
    }
}
