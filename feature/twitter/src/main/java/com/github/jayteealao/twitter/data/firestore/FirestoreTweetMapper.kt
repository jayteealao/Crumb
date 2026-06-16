package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.MediaKeys
import com.github.jayteealao.twitter.models.TweetEntities
import com.github.jayteealao.twitter.models.TweetReferencedTweets
import com.github.jayteealao.twitter.models.TweetReferencedTweetsFull
import com.github.jayteealao.twitter.models.tweetPublicMetrics

/**
 * Pure mapping helpers that assemble [TweetEntities] from the parallel-fetched
 * Firestore sub-collection results.  No Firestore SDK or IO imports live here —
 * only data-class transformations that can be tested without a Firestore stub.
 */

/**
 * Assembles one [TweetEntities] from the already-fetched Firestore sub-collection
 * maps.  Returns null when the required author doc is missing for [firestoreTweet].
 *
 * @param firestoreTweet  the tweet document to assemble
 * @param users           authorId → [FirestoreUser] for primary bookmark authors
 * @param metrics         tweetId → [FirestoreMetrics]
 * @param includes        tweetId → list of [FirestoreIncludes] rows
 * @param media           tweetId → list of [FirestoreMedia] docs (already re-keyed from mediaKey via includes join)
 * @param textAnnotations tweetId → list of [FirestoreTextAnnotation] docs
 * @param quotedTweets    tweetId → [FirestoreTweet] for the quoted body
 * @param quotedAuthors   authorId → [FirestoreUser] for quoted-tweet authors
 */
internal fun assembleTweetEntities(
    firestoreTweet: FirestoreTweet,
    users: Map<String, FirestoreUser>,
    metrics: Map<String, FirestoreMetrics>,
    includes: Map<String, List<FirestoreIncludes>>,
    media: Map<String, List<FirestoreMedia>>,
    textAnnotations: Map<String, List<FirestoreTextAnnotation>>,
    quotedTweets: Map<String, FirestoreTweet>,
    quotedAuthors: Map<String, FirestoreUser>,
): TweetEntities? {
    val tweetId = firestoreTweet.tweetId
    val user = users[firestoreTweet.authorId] ?: return null

    // Build the quoted-tweet reference list (FK-free junction). Each type="quoted"
    // includes row becomes a TweetReferencedTweetsFull with the resolved quoted body
    // (or null when unavailable). The DAO batch inserts the quoted TweetEntity with
    // referenced=true, keeping it out of the feed.  The dangerous mention/reply FK
    // relation is intentionally NOT re-enabled; tweetIncludesEntity stays empty.
    val referencedFull: List<TweetReferencedTweetsFull> = includes[tweetId].orEmpty()
        .filter { it.type == "quoted" && it.referencedTweetId != null }
        .distinctBy { it.referencedTweetId }
        .map { inc ->
            val refId = inc.referencedTweetId!!
            TweetReferencedTweetsFull(
                referencedTweets = TweetReferencedTweets(
                    type = "quoted",
                    id = refId,
                    tweetId = tweetId,
                ),
                tweet = quotedTweets[refId]?.toTweetEntity(referenced = true),
            )
        }

    val quotedAuthorEntities = referencedFull
        .mapNotNull { it.tweet }
        .mapNotNull { quotedAuthors[it.authorId]?.toTwitterUserEntity() }

    val authorEntities = (listOf(user.toTwitterUserEntity()) + quotedAuthorEntities)
        .distinctBy { it.id }

    return TweetEntities(
        tweetEntity = firestoreTweet.toTweetEntity(),
        twitterUserEntity = authorEntities,
        tweetPublicMetrics = metrics[tweetId]?.toTweetPublicMetrics()
            ?: tweetPublicMetrics().copy(tweetId = tweetId),
        // Thread the in-scope parent tweetId onto each media row. FirestoreMedia docs
        // carry no tweetId of their own (the server keys them by mediaKey), so without
        // this every tweetMedia row persists with tweet_id = NULL and the TweetData.media
        // @Relation matches nothing. Mirrors the MediaKeys(tweetId, …) threading below.
        tweetMediaEntity = media[tweetId]?.map { it.toTweetMediaEntity(tweetId) } ?: emptyList(),
        // Firestore includes rows carry mention/reply user ids that are not present
        // in this tweet's TwitterUserEntity batch.  Persisting them trips the
        // TweetIncludesEntity foreign keys and rolls back the whole batch insert.
        // The UI never reads TweetData.includes; this relation stays dropped.
        tweetIncludesEntity = emptyList(),
        tweetReferencedTweets = referencedFull,
        tweetContextAnnotationEntity = emptyList(),
        tweetTextEntity = textAnnotations[tweetId]?.map { it.toTweetTextEntityAnnotation() } ?: emptyList(),
        mediaKeys = media[tweetId]?.map { MediaKeys(tweetId, it.mediaKey) } ?: emptyList(),
    )
}
