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
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
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
 * Pins the TYPE-filter predicate semantics independent of the live sync data gap:
 * IMAGE = has a `photo` media row, VIDEO = `video`/`animated_gif`, ARTICLE = an external
 * (non twitter/x.com) `urls` annotation, THREAD = a reply or a conversation with a sibling,
 * TEXT = neither media nor an external link. THREAD and TEXT legitimately overlap for a
 * media-less, link-less reply — that overlap is asserted explicitly, not hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TweetDaoTypeFilterTest {

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

    private fun tweet(id: String, conversationId: String = id) = TweetEntity(
        id = id,
        text = "text-$id",
        createdAt = "2026-01-01T00:00:00.000Z",
        authorId = "u1",
        conversationId = conversationId,
        inReplyToUserId = null,
        lang = "en",
        retrievedAt = 1_000L,
    )

    private fun media(key: String, type: String, tweetId: String) = TweetMediaEntity(
        mediaKey = key,
        type = type,
        url = "https://example.com/$key",
        durationMs = 0,
        height = 1,
        width = 1,
        previewImageUrl = null,
        altText = null,
        tweetId = tweetId,
    )

    private fun urlAnnotation(tweetId: String, expandedUrl: String) = TweetTextEntityAnnotation(
        id = null,
        start = 0,
        end = 0,
        product = null,
        status = null,
        tag = null,
        title = null,
        description = null,
        url = null,
        expandedUrl = expandedUrl,
        displayUrl = null,
        unwoundUrl = null,
        mediaKey = null,
        normalizedText = null,
        tweetId = tweetId,
        type = "urls",
    )

    @Test
    fun typeFilters_returnExpectedSubsets() = runTest {
        // One fixture per positive type marker, plus a media-less/link-less reply and a plain tweet.
        dao.insertTweet(tweet("photo"))
        dao.insertTweetMedia(media("m-photo", "photo", "photo"))

        dao.insertTweet(tweet("video"))
        dao.insertTweetMedia(media("m-video", "video", "video"))

        dao.insertTweet(tweet("article"))
        dao.insertTweetTextEntityAnnotation(urlAnnotation("article", "https://example.com/post"))

        dao.insertTweet(tweet("reply", conversationId = "conv-root")) // conversation_id <> id → THREAD
        dao.insertTweet(tweet("text"))

        assertEquals(setOf("photo", "video", "article", "reply", "text"), idsFor("ALL"))
        assertEquals(setOf("photo"), idsFor("IMAGE"))
        assertEquals(setOf("video"), idsFor("VIDEO"))
        assertEquals(setOf("article"), idsFor("ARTICLE"))
        assertEquals(setOf("reply"), idsFor("THREAD"))
        // TEXT = no media AND no external link → the plain tweet and the (media-less) reply.
        assertEquals(setOf("text", "reply"), idsFor("TEXT"))
    }

    @Test
    fun articleIgnoresInternalTwitterLinks() = runTest {
        // A urls annotation that points back at twitter.com must NOT count as an ARTICLE.
        dao.insertTweet(tweet("internal-link"))
        dao.insertTweetTextEntityAnnotation(
            urlAnnotation("internal-link", "https://twitter.com/i/web/status/123"),
        )

        assertEquals(emptySet<String>(), idsFor("ARTICLE"))
        // With no external link and no media it falls through to TEXT.
        assertEquals(setOf("internal-link"), idsFor("TEXT"))
    }

    private suspend fun idsFor(type: String): Set<String> {
        val page = dao.getTweetsTombstoneAware(type).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        return page.data.map { it.tweet.id }.toSet()
    }
}
