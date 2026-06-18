package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.TweetEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test closing the media-assembly blind spot behind the "bookmark cards render
 * text-only" defect. Every other media test factory hard-codes a non-null `tweetId`, so the
 * real Firestore→Room assembly path — which receives media docs with `tweetId = null` (the
 * server keys them by mediaKey; the tweet↔media link is a separate includes doc) — was never
 * exercised. [assembleTweetEntities] must inject the map-key tweetId onto each
 * [com.github.jayteealao.twitter.models.TweetMediaEntity]; without it Room's `tweet_id`-keyed
 * @Relation matches nothing and no image/video ever renders.
 *
 * Pure JVM test — [assembleTweetEntities] is a data-class transform with no Firestore/Android
 * dependency, so no Robolectric runner is needed.
 */
class FirestoreTweetMapperAssemblyTest {

    private val parentTweetId = "t1"

    private fun tweet() = FirestoreTweet(
        tweetId = parentTweetId,
        text = "hello",
        authorId = "u1",
        createdAt = "2026-01-01T00:00:00.000Z",
        conversationId = parentTweetId,
    )

    private fun author() = FirestoreUser(
        userId = "u1",
        username = "tester",
        name = "Tester",
    )

    // Media docs exactly as the server writes them: NO tweetId field on the doc itself.
    private fun photoDoc() = FirestoreMedia(
        mediaKey = "mk-photo",
        type = "photo",
        url = "https://img/mk-photo.jpg",
        tweetId = null,
    )

    private fun videoDoc() = FirestoreMedia(
        mediaKey = "mk-video",
        type = "video",
        previewImageUrl = "https://img/poster.jpg",
        tweetId = null,
        variants = listOf(
            mapOf<String, Any?>(
                "bit_rate" to 0,
                "content_type" to "application/x-mpegURL",
                "url" to "https://v/master.m3u8",
            ),
        ),
    )

    // The map is keyed by the real tweetId (re-keyed from mediaKey via the includes join
    // upstream); the FirestoreMedia objects inside it still carry tweetId = null.
    private fun assemble(media: List<FirestoreMedia>): TweetEntities? =
        assembleTweetEntities(
            firestoreTweet = tweet(),
            users = mapOf("u1" to author()),
            metrics = emptyMap(),
            includes = emptyMap(),
            media = mapOf(parentTweetId to media),
            textAnnotations = emptyMap(),
            quotedTweets = emptyMap(),
            quotedAuthors = emptyMap(),
        )

    @Test
    fun photoMediaDocWithNullTweetId_getsParentTweetIdOnTheEntity() {
        val entities = assemble(listOf(photoDoc()))

        assertNotNull("assembly should succeed when the author doc is present", entities)
        val media = entities!!.tweetMediaEntity
        assertEquals("exactly one media entity assembled", 1, media.size)
        assertEquals(
            "the parent tweetId (map key) must be threaded onto the media entity, " +
                "not the doc's own null tweetId",
            parentTweetId,
            media.single().tweetId,
        )
    }

    @Test
    fun videoMediaDocWithNullTweetId_getsParentTweetIdOnTheEntity() {
        val entities = assemble(listOf(videoDoc()))

        val media = entities!!.tweetMediaEntity.single()
        assertEquals(parentTweetId, media.tweetId)
        assertTrue(
            "video variants should survive assembly",
            !media.videoVariants.isNullOrEmpty(),
        )
    }

    @Test
    fun mediaEntityTweetId_matchesTheMediaKeysJunctionRow() {
        // The junction line (always correct) and the media-entity line (the broken one) must
        // agree on the parent id — that agreement is exactly what the @Relation join needs.
        val entities = assemble(listOf(photoDoc()))!!

        val mediaTweetId = entities.tweetMediaEntity.single().tweetId
        val junction = entities.mediaKeys.single()
        assertEquals(parentTweetId, junction.tweetId)
        assertEquals(
            "media entity and mediaKeys junction must reference the same tweet",
            junction.tweetId,
            mediaTweetId,
        )
        assertEquals("mk-photo", junction.mediaKey)
    }

    @Test
    fun sharedMediaKey_isAttributedToEachOwnerTweet_notCollapsed() {
        // The wrong-media-attached regression: two tweets on one fetch page reference the
        // SAME media_key (a quote/co-page asset). assembleTweetEntities runs once per tweet
        // with that tweet as the map key, so each must stamp its OWN id onto the media row
        // (and the mediaKeys junction). The composite (tweet_id, media_key) PK then stores
        // both pairings instead of collapsing the asset onto one arbitrary tweet.
        val sharedKey = "mk-shared"
        fun tweetWithId(id: String) = FirestoreTweet(
            tweetId = id,
            text = "t-$id",
            authorId = "u1",
            createdAt = "2026-01-01T00:00:00.000Z",
            conversationId = id,
        )
        fun assembleFor(id: String): TweetEntities = assembleTweetEntities(
            firestoreTweet = tweetWithId(id),
            users = mapOf("u1" to author()),
            metrics = emptyMap(),
            includes = emptyMap(),
            media = mapOf(
                id to listOf(FirestoreMedia(mediaKey = sharedKey, type = "photo", url = "https://img/s.jpg", tweetId = null)),
            ),
            textAnnotations = emptyMap(),
            quotedTweets = emptyMap(),
            quotedAuthors = emptyMap(),
        )!!

        val a = assembleFor("A")
        val b = assembleFor("B")

        // Each tweet owns the shared asset under its OWN id — no collapse.
        assertEquals("A", a.tweetMediaEntity.single().tweetId)
        assertEquals("B", b.tweetMediaEntity.single().tweetId)
        assertEquals(sharedKey, a.tweetMediaEntity.single().mediaKey)
        assertEquals(sharedKey, b.tweetMediaEntity.single().mediaKey)
        // The junction rows agree per tweet (the @Relation join key).
        assertEquals("A", a.mediaKeys.single().tweetId)
        assertEquals("B", b.mediaKeys.single().tweetId)
    }
}
