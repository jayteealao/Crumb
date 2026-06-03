package com.github.jayteealao.twitter.screens

import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.crumbs.models.ContentType
import com.github.jayteealao.crumbs.models.toRelativeTime
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
import com.github.jayteealao.twitter.models.TwitterUserEntity
import com.github.jayteealao.twitter.models.Variant
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

    private fun video(
        key: String,
        type: String = "video",
        url: String? = null,
        previewImageUrl: String? = "https://img/poster.jpg",
        variants: List<Variant>? = null,
    ) = TweetMediaEntity(
        mediaKey = key,
        type = type,
        url = url,
        durationMs = 30000,
        height = 720,
        width = 1280,
        previewImageUrl = previewImageUrl,
        altText = null,
        tweetId = "t1",
        videoVariants = variants,
    )

    private fun urls(
        expandedUrl: String?,
        displayUrl: String? = null,
        title: String? = null,
        description: String? = null,
        imageUrl: String? = null,
    ) = TweetTextEntityAnnotation(
        id = null,
        start = 0,
        end = 0,
        product = null,
        status = null,
        tag = null,
        title = title,
        description = description,
        imageUrl = imageUrl,
        url = "https://t.co/x",
        expandedUrl = expandedUrl,
        displayUrl = displayUrl,
        unwoundUrl = null,
        mediaKey = null,
        normalizedText = null,
        tweetId = "t1",
        type = "urls",
    )

    private fun tweetData(
        text: String = "hello world",
        createdAt: String = "2026-05-13T12:22:37.000Z",
        retrievedAt: Long? = null,
        media: List<TweetMediaEntity> = emptyList(),
        textAnnotations: List<TweetTextEntityAnnotation> = emptyList(),
    ): TweetData = TweetData(
        tweet = TweetEntity(
            id = "t1",
            text = text,
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
        tweetTextAnnotation = textAnnotations,
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

    @Test
    fun videoMediaMapsToVideoContentTypeWithVariantsThumbnailAndHlsBestUrl() {
        val bookmark = tweetData(
            media = listOf(
                video(
                    "vk1",
                    variants = listOf(
                        Variant(bitRate = 0, contentType = "application/x-mpegURL", url = "https://v/master.m3u8"),
                        Variant(bitRate = 832000, contentType = "video/mp4", url = "https://v/480.mp4"),
                        Variant(bitRate = 2176000, contentType = "video/mp4", url = "https://v/720.mp4"),
                    ),
                ),
            ),
        ).toBookmark()
        assertEquals(ContentType.Video, bookmark.contentType)
        assertEquals("https://img/poster.jpg", bookmark.videoThumbnailUrl)
        assertEquals(3, bookmark.videoVariants.size)
        // HLS is preferred for the back-compat single videoUrl.
        assertEquals("https://v/master.m3u8", bookmark.videoUrl)
    }

    @Test
    fun animatedGifMapsToVideoContentType() {
        val bookmark = tweetData(
            media = listOf(
                video(
                    "gk1",
                    type = "animated_gif",
                    variants = listOf(Variant(bitRate = 0, contentType = "video/mp4", url = "https://v/gif.mp4")),
                ),
            ),
        ).toBookmark()
        assertEquals(ContentType.Video, bookmark.contentType)
        assertEquals("https://v/gif.mp4", bookmark.videoUrl)
    }

    @Test
    fun legacyVideoWithNoVariantsFallsBackToFlatUrlAndEmptyVariants() {
        val bookmark = tweetData(
            media = listOf(video("vk2", url = "https://v/legacy.mp4", variants = null)),
        ).toBookmark()
        assertEquals(ContentType.Video, bookmark.contentType)
        assertTrue(bookmark.videoVariants.isEmpty())
        assertEquals("https://v/legacy.mp4", bookmark.videoUrl)
        assertEquals("https://img/poster.jpg", bookmark.videoThumbnailUrl)
    }

    @Test
    fun noVideoYieldsEmptyVideoVariantsAndNullVideoFields() {
        val bookmark = tweetData(media = emptyList()).toBookmark()
        assertTrue(bookmark.videoVariants.isEmpty())
        assertNull(bookmark.videoUrl)
        assertNull(bookmark.videoThumbnailUrl)
    }

    @Test
    fun externalUrlEntityMapsToLinkContentTypeWithPreviewFields() {
        val bookmark = tweetData(
            text = "great read https://t.co/x",
            textAnnotations = listOf(
                urls(
                    expandedUrl = "https://example.com/article",
                    displayUrl = "example.com/article",
                    title = "Brutalist Web Design",
                    description = "A guide.",
                    imageUrl = "https://cdn.example.com/og.jpg",
                ),
            ),
        ).toBookmark()
        assertEquals(ContentType.Link, bookmark.contentType)
        assertEquals("https://example.com/article", bookmark.linkUrl)
        assertEquals("example.com/article", bookmark.linkDisplayUrl)
        assertEquals("Brutalist Web Design", bookmark.linkTitle)
        assertEquals("A guide.", bookmark.linkDescription)
        assertEquals("https://cdn.example.com/og.jpg", bookmark.linkImageUrl)
    }

    @Test
    fun internalXComUrlIsNotTreatedAsAnExternalLink() {
        val bookmark = tweetData(
            text = "quote tweet https://t.co/x",
            textAnnotations = listOf(urls(expandedUrl = "https://x.com/user/status/123")),
        ).toBookmark()
        assertEquals(ContentType.Text, bookmark.contentType)
        assertNull(bookmark.linkUrl)
    }

    @Test
    fun displayUrlFallsBackToExpandedHostWhenAbsent() {
        val bookmark = tweetData(
            textAnnotations = listOf(urls(expandedUrl = "https://www.example.com/path/to/article")),
        ).toBookmark()
        assertEquals(ContentType.Link, bookmark.contentType)
        // www. is stripped; host only.
        assertEquals("example.com", bookmark.linkDisplayUrl)
    }

    @Test
    fun mediaPrecedenceWinsOverLink_photoPlusLinkIsImage() {
        val bookmark = tweetData(
            text = "photo + link https://t.co/x",
            media = listOf(photo("k1", "https://img/1.jpg")),
            textAnnotations = listOf(urls(expandedUrl = "https://example.com/article")),
        ).toBookmark()
        assertEquals(ContentType.Image, bookmark.contentType)
        // Link fields are still populated, but the card renders the image band (precedence).
        assertEquals("https://example.com/article", bookmark.linkUrl)
    }

    @Test
    fun noUrlAnnotationYieldsNullLinkFieldsAndTextType() {
        val bookmark = tweetData(text = "plain text, no link").toBookmark()
        assertEquals(ContentType.Text, bookmark.contentType)
        assertNull(bookmark.linkUrl)
        assertNull(bookmark.linkDisplayUrl)
        assertNull(bookmark.linkTitle)
        assertNull(bookmark.linkImageUrl)
    }
}
