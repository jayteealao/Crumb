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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks the media @Relation contract that the "cards render text-only" defect violated.
 * `TweetData.media` is `@Relation(parentColumn = "id", entityColumn = "tweet_id")`, so a
 * media row's `tweet_id` MUST equal its parent tweet id for the relation to resolve. A row
 * written with `tweet_id = NULL` (the pre-fix assembly bug) resolves to an EMPTY relation —
 * SQL `IN (…)` never matches NULL — which is the exact mechanism behind the missing images
 * and video. Both directions are asserted so the fix is guarded and the failure mode is
 * documented in code.
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

    private fun photo(mediaKey: String, tweetId: String?) = TweetMediaEntity(
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
    fun mediaRowWithNullTweetId_leavesTheRelationEmpty() = runTest {
        // The pre-fix failure mode: a NULL tweet_id is never matched by the @Relation's
        // `WHERE tweet_id IN (…)`, so the card sees no media and falls through to text.
        dao.insertTweet(tweet("t2"))
        dao.insertTweetMedia(photo("mk2", tweetId = null))

        val data = dao.getTweetById("t2")

        assertNotNull(data)
        assertTrue(
            "a NULL-tweet_id media row must NOT resolve into any tweet's relation",
            data!!.media.isEmpty(),
        )
    }
}
