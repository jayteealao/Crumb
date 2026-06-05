package com.github.jayteealao.crumbs.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.twitter.data.TweetDao
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetReferencedTweets
import com.github.jayteealao.twitter.models.TwitterUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round-trips the FK-free quoted `@Relation` junction through a real (in-memory) v17
 * Room database — the read half of the quoted-tweets slice AC1 ("the referenced
 * tweet's author + text are persisted to Room"). Complements [ToBookmarkMapperTest],
 * which exercises the `toBookmark()` mapper over hand-built `TweetData` fixtures: this
 * proves Room itself actually hydrates `TweetData.quotedTweets` / `referencedTweets`
 * from the `tweetReferencedTweets` junction (no FK on the path), including the
 * "unavailable" orphan-reference case, the nullable quoted-author fallback, and the
 * `referenced = true` feed-exclusion guard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Bare Application (not CrumbApplication) so this pure Room test does not pull in
// Hilt / Firebase init — it only needs a Context to build an in-memory database.
@Config(sdk = [34], application = Application::class)
class TweetDaoQuotedTweetTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TweetDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.tweetDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun user(id: String, name: String, username: String) = TwitterUserEntity(
        id = id,
        name = name,
        username = username,
        profileImageUrl = null,
        verified = false,
        verifiedType = null,
        description = null,
        mentionedIn = null,
    )

    private fun tweet(id: String, authorId: String, referenced: Boolean = false) = TweetEntity(
        id = id,
        text = "text-$id",
        createdAt = "2026-01-01T00:00:00.000Z",
        authorId = authorId,
        conversationId = id,
        inReplyToUserId = null,
        lang = "en",
        referenced = referenced,
    )

    private fun quotedRef(parentId: String, quotedId: String, type: String = "quoted") =
        TweetReferencedTweets(type = type, id = quotedId, tweetId = parentId)

    @Test
    fun availableQuote_hydratesQuotedBodyAndAuthorThroughJunction() = runTest {
        dao.insertTwitterUser(user("u1", "Parent Author", "parent"))
        dao.insertTwitterUser(user("qu1", "Quoted Author", "quoted_author"))
        // Parent bookmarked tweet (referenced = false → surfaces in the feed).
        dao.insertTweet(tweet("t1", authorId = "u1"))
        // Quoted body, stored referenced = true so it never leaks into the feed.
        dao.insertTweet(tweet("q1", authorId = "qu1", referenced = true))
        dao.insertTweetReferencedTweets(quotedRef("t1", "q1"))

        val data = dao.getTweetById("t1")!!

        // Raw reference row hydrated for the parent.
        assertEquals(1, data.referencedTweets.size)
        assertEquals("q1", data.referencedTweets.first().id)
        assertEquals("quoted", data.referencedTweets.first().type)
        assertEquals("t1", data.referencedTweets.first().tweetId)

        // Quoted body + author resolved through the FK-free junction.
        assertEquals(1, data.quotedTweets.size)
        val quoted = data.quotedTweets.first()
        assertEquals("q1", quoted.tweet.id)
        assertEquals("text-q1", quoted.tweet.text)
        assertTrue(quoted.tweet.referenced)
        assertEquals("qu1", quoted.author?.id)
        assertEquals("quoted_author", quoted.author?.username)
    }

    @Test
    fun unavailableQuote_referenceRowWithoutBody_yieldsEmptyQuotedTweetsButKeepsReference() = runTest {
        dao.insertTwitterUser(user("u1", "Parent Author", "parent"))
        dao.insertTweet(tweet("t2", authorId = "u1"))
        // A quoted reference whose body was never stored (deleted/protected quote).
        dao.insertTweetReferencedTweets(quotedRef("t2", "q2"))

        val data = dao.getTweetById("t2")!!

        // The reference row survives even with no resolvable body — this is the
        // "unavailable" signal the card reads (quotedTweetId set, quotedText null).
        assertEquals(1, data.referencedTweets.size)
        assertEquals("q2", data.referencedTweets.first().id)
        assertTrue(data.quotedTweets.isEmpty())
    }

    @Test
    fun availableQuote_withMissingAuthor_hydratesBodyWithNullAuthor() = runTest {
        dao.insertTwitterUser(user("u1", "Parent Author", "parent"))
        dao.insertTweet(tweet("t3", authorId = "u1"))
        // Quoted body present, but its author row was never persisted.
        dao.insertTweet(tweet("q3", authorId = "missing-author", referenced = true))
        dao.insertTweetReferencedTweets(quotedRef("t3", "q3"))

        val data = dao.getTweetById("t3")!!

        assertEquals(1, data.quotedTweets.size)
        val quoted = data.quotedTweets.first()
        assertEquals("q3", quoted.tweet.id)
        // Nullable author is the load-bearing design point: the body still hydrates.
        assertNull(quoted.author)
    }

    @Test
    fun quotedBodyStoredReferenced_isExcludedFromTheFeedQuery() = runTest {
        dao.insertTwitterUser(user("qu1", "Quoted Author", "quoted_author"))
        dao.insertTweet(tweet("q1", authorId = "qu1", referenced = true))

        // getTweetById filters `referenced = 0`, so a quoted body never surfaces as a
        // standalone bookmark — the feed-exclusion guard the plan flagged as a risk.
        assertNull(dao.getTweetById("q1"))
    }
}
