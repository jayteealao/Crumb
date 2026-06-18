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
import com.github.jayteealao.twitter.models.Variant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks in `TweetDao.getVideoTweetsWithoutVariants` (the widened backfill sweep) and the
 * `updateMedia` repair path. Also exercises the `MediaConverters` round-trip — inserting a
 * row with a `video_variants` list and reading it back through the type converter — since
 * this is the project's first Room `@TypeConverter`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TweetDaoVideoVariantTest {

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

    private fun media(
        mediaKey: String,
        tweetId: String,
        type: String,
        variants: List<Variant>? = null,
    ) = TweetMediaEntity(
        mediaKey = mediaKey,
        type = type,
        url = "https://v/$mediaKey.mp4",
        durationMs = 0,
        height = 0,
        width = 0,
        previewImageUrl = "https://img/$mediaKey.jpg",
        altText = null,
        tweetId = tweetId,
        videoVariants = variants,
    )

    private val sampleVariants = listOf(
        Variant(bitRate = 0, contentType = "application/x-mpegURL", url = "https://v/master.m3u8"),
        Variant(bitRate = 2_176_000, contentType = "video/mp4", url = "https://v/720.mp4"),
    )

    @Test
    fun returnsOnlyVideoAndGifRowsWithNullVariants() = runTest {
        dao.insertTweet(tweet("a-video-novariants"))
        dao.insertTweet(tweet("b-gif-novariants"))
        dao.insertTweet(tweet("c-video-withvariants"))
        dao.insertTweet(tweet("d-photo"))
        dao.insertTweet(tweet("e-referenced", referenced = true))
        dao.insertTweetMedia(media("m1", "a-video-novariants", type = "video", variants = null))
        dao.insertTweetMedia(media("m2", "b-gif-novariants", type = "animated_gif", variants = null))
        dao.insertTweetMedia(media("m3", "c-video-withvariants", type = "video", variants = sampleVariants))
        dao.insertTweetMedia(media("m4", "d-photo", type = "photo", variants = null))
        dao.insertTweetMedia(media("m5", "e-referenced", type = "video", variants = null))

        val ids = dao.getVideoTweetsWithoutVariants(afterId = "", limit = 50)

        assertEquals(listOf("a-video-novariants", "b-gif-novariants"), ids)
    }

    @Test
    fun updateMediaLandsFetchedVariants_andTypeConverterRoundTrips() = runTest {
        dao.insertTweet(tweet("v1"))
        dao.insertTweetMedia(media("mk", "v1", type = "video", variants = null))

        // Present-but-variant-less → in the sweep result set.
        assertEquals(listOf("v1"), dao.getVideoTweetsWithoutVariants(afterId = "", limit = 50))

        // The re-fetch repair updates the existing row in place with fetched variants.
        dao.updateMedia(media("mk", "v1", type = "video", variants = sampleVariants))

        // Now excluded from the sweep.
        assertTrue(dao.getVideoTweetsWithoutVariants(afterId = "", limit = 50).isEmpty())

        // And the converter round-trips the list back through the read path.
        val read = dao.getTweetById("v1")
        val storedVariants = read?.media?.first { it.mediaKey == "mk" }?.videoVariants
        assertEquals(sampleVariants, storedVariants)
    }

    @Test
    fun keysetPaginationAdvancesPastTheCursor() = runTest {
        dao.insertTweet(tweet("id-1"))
        dao.insertTweet(tweet("id-2"))
        dao.insertTweet(tweet("id-3"))
        dao.insertTweetMedia(media("k1", "id-1", type = "video", variants = null))
        dao.insertTweetMedia(media("k2", "id-2", type = "video", variants = null))
        dao.insertTweetMedia(media("k3", "id-3", type = "video", variants = null))

        val firstPage = dao.getVideoTweetsWithoutVariants(afterId = "", limit = 2)
        assertEquals(listOf("id-1", "id-2"), firstPage)

        val secondPage = dao.getVideoTweetsWithoutVariants(afterId = firstPage.last(), limit = 2)
        assertEquals(listOf("id-3"), secondPage)
    }
}
