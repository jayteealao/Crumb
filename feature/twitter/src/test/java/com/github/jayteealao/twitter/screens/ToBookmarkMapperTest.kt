package com.github.jayteealao.twitter.screens

import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.crumbs.models.toRelativeTime
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TwitterUserEntity
import com.github.jayteealao.twitter.util.parseTweetTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the [toBookmark] timestamp-selection contract
 * (server `retrievedAt` → tweet `createdAt` → [Bookmark.UNKNOWN_TIME] sentinel)
 * and the multi-photo media mapping (all photo URLs → `imageUrls`, first → `imageUrl`).
 */
class ToBookmarkMapperTest {

    private fun photo(key: String, url: String?) = TweetMediaEntity(
        mediaKey = key,
        type = "photo",
        url = url,
        durationMs = 0,
        height = 0,
        width = 0,
        previewImageUrl = null,
        altText = null,
        tweetId = "t1",
    )

    private fun tweetData(
        createdAt: String = "2026-05-13T12:22:37.000Z",
        retrievedAt: Long? = null,
        media: List<TweetMediaEntity> = emptyList(),
    ): TweetData = TweetData(
        tweet = TweetEntity(
            id = "t1",
            text = "hello world",
            createdAt = createdAt,
            authorId = "u1",
            conversationId = "t1",
            inReplyToUserId = null,
            lang = "en",
            retrievedAt = retrievedAt,
        ),
        user = TwitterUserEntity(
            id = "u1",
            name = "Tester",
            username = "tester",
            profileImageUrl = null,
            verified = false,
            verifiedType = null,
            description = null,
            mentionedIn = null,
        ),
        publicMetrics = null,
        media = media,
        includes = emptyList(),
        tweetTextAnnotation = emptyList(),
    )

    @Test
    fun usesServerRetrievedAtWhenPresent() {
        val saved = tweetData(
            createdAt = "2020-09-16T17:51:39.000Z",
            retrievedAt = 1_700_000_000_000L,
        ).toBookmark().savedAt
        assertEquals(1_700_000_000_000L, saved)
    }

    @Test
    fun fallsBackToCreatedAtWhenRetrievedAtNull() {
        val expected = parseTweetTimestamp("2020-09-16T17:51:39.000Z")
        val saved = tweetData(
            createdAt = "2020-09-16T17:51:39.000Z",
            retrievedAt = null,
        ).toBookmark().savedAt
        assertEquals(expected, saved)
    }

    @Test
    fun parsesLegacyV1_1CreatedAtFallback() {
        val expected = parseTweetTimestamp("Wed Sep 16 17:51:39 +0000 2020")
        val saved = tweetData(
            createdAt = "Wed Sep 16 17:51:39 +0000 2020",
            retrievedAt = null,
        ).toBookmark().savedAt
        assertEquals(expected, saved)
    }

    @Test
    fun unparseableCreatedAtAndNoRetrievedAt_yieldsUnknownTimeSentinelAndUnderscoreLabel() {
        val bookmark = tweetData(createdAt = "not a date", retrievedAt = null).toBookmark()
        assertEquals(Bookmark.UNKNOWN_TIME, bookmark.savedAt)
        assertEquals("_", bookmark.savedAt.toRelativeTime())
    }

    @Test
    fun allPhotoUrlsMapToImageUrlsInOrder_andImageUrlIsTheFirst() {
        val bookmark = tweetData(
            media = listOf(
                photo("k1", "https://img/1.jpg"),
                photo("k2", "https://img/2.jpg"),
                photo("k3", "https://img/3.jpg"),
            ),
        ).toBookmark()
        assertEquals(
            listOf("https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg"),
            bookmark.imageUrls,
        )
        assertEquals("https://img/1.jpg", bookmark.imageUrl)
        assertEquals(ContentType.Image, bookmark.contentType)
    }

    @Test
    fun photosWithNullUrlAreSkippedFromImageUrls() {
        val bookmark = tweetData(
            media = listOf(
                photo("k1", "https://img/1.jpg"),
                photo("k2", null),
                photo("k3", "https://img/3.jpg"),
            ),
        ).toBookmark()
        assertEquals(listOf("https://img/1.jpg", "https://img/3.jpg"), bookmark.imageUrls)
        assertEquals("https://img/1.jpg", bookmark.imageUrl)
    }

    @Test
    fun noPhotosYieldsEmptyImageUrlsAndNullImageUrl() {
        val bookmark = tweetData(media = emptyList()).toBookmark()
        assertTrue(bookmark.imageUrls.isEmpty())
        assertNull(bookmark.imageUrl)
    }
}
