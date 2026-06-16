package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetIncludesEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.Variant
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
import com.github.jayteealao.twitter.models.TwitterUserEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Firestore document model for tweets collection
 */
data class FirestoreTweet(
    @DocumentId val documentId: String = "",
    @get:PropertyName("tweetId") @set:PropertyName("tweetId")
    var tweetId: String = "",
    var text: String = "",
    @get:PropertyName("authorId") @set:PropertyName("authorId")
    var authorId: String = "",
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: String = "",
    @get:PropertyName("conversationId") @set:PropertyName("conversationId")
    var conversationId: String = "",
    @get:PropertyName("inReplyToUserId") @set:PropertyName("inReplyToUserId")
    var inReplyToUserId: String? = null,
    var lang: String? = null,
    var order: Int = 0,
    var source: String? = null,
    // True on a quoted tweet's body doc — written under tweets/ by the poll but NOT
    // a bookmark. Nullable so legacy/normal docs (no field) collapse to false. The
    // mapper passes this to toTweetEntity so a quoted body that ever syncs through the
    // standalone path lands referenced=true and stays out of the feed.
    @get:PropertyName("referenced") @set:PropertyName("referenced")
    var referenced: Boolean? = null,
    // Nullable so the deserializer accepts pre-poll-correctness docs that
    // never wrote this field. `toTweetEntity` collapses null → false.
    @get:PropertyName("pending_delete") @set:PropertyName("pending_delete")
    var pendingDelete: Boolean? = null,
    // Server-owned first-seen / poll time, written as a Firestore Timestamp by the poll
    // function only on first-seen tweets. Nullable so the deserializer accepts legacy docs
    // that predate the field. The client never originates this value.
    @get:PropertyName("retrievedAt") @set:PropertyName("retrievedAt")
    var retrievedAt: Timestamp? = null,
) {
    fun toTweetEntity(referenced: Boolean = this.referenced ?: false): TweetEntity = TweetEntity(
        id = tweetId,
        text = text,
        createdAt = createdAt,
        authorId = authorId,
        conversationId = conversationId,
        inReplyToUserId = inReplyToUserId,
        lang = lang,
        referenced = referenced,
        order = order,
        pendingDelete = pendingDelete ?: false,
        retrievedAt = retrievedAt?.toDate()?.time,
    )

    companion object {
        fun fromTweetEntity(entity: TweetEntity): FirestoreTweet = FirestoreTweet(
            tweetId = entity.id,
            text = entity.text,
            authorId = entity.authorId,
            createdAt = entity.createdAt,
            conversationId = entity.conversationId,
            inReplyToUserId = entity.inReplyToUserId,
            lang = entity.lang,
            order = entity.order,
            // Round-trip the server value so the legacy merge-upload path never nulls it.
            retrievedAt = entity.retrievedAt?.let { Timestamp(Date(it)) },
        )
    }
}

/**
 * Firestore document model for users collection
 */
data class FirestoreUser(
    @DocumentId val documentId: String = "",
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",
    var username: String = "",
    var name: String = "",
    var description: String? = null,
    @get:PropertyName("profileImageUrl") @set:PropertyName("profileImageUrl")
    var profileImageUrl: String? = null,
    var verified: Boolean = false,
    @get:PropertyName("verifiedType") @set:PropertyName("verifiedType")
    var verifiedType: String? = null,
    var location: String? = null,
    @get:PropertyName("followersCount") @set:PropertyName("followersCount")
    var followersCount: Int = 0,
    @get:PropertyName("followingCount") @set:PropertyName("followingCount")
    var followingCount: Int = 0,
    @get:PropertyName("tweetCount") @set:PropertyName("tweetCount")
    var tweetCount: Int = 0,
    @get:PropertyName("listedCount") @set:PropertyName("listedCount")
    var listedCount: Int = 0,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: String? = null,
    var url: String? = null,
    @get:PropertyName("pinnedTweetId") @set:PropertyName("pinnedTweetId")
    var pinnedTweetId: String? = null
) {
    fun toTwitterUserEntity(): TwitterUserEntity = TwitterUserEntity(
        id = userId,
        username = username,
        name = name,
        description = description,
        profileImageUrl = profileImageUrl,
        verified = verified,
        verifiedType = verifiedType,
        mentionedIn = null
    )

    companion object {
        fun fromTwitterUserEntity(entity: TwitterUserEntity): FirestoreUser = FirestoreUser(
            userId = entity.id,
            username = entity.username,
            name = entity.name,
            description = entity.description,
            profileImageUrl = entity.profileImageUrl,
            verified = entity.verified ?: false,
            verifiedType = entity.verifiedType
        )
    }
}

/**
 * Firestore document model for media collection
 */
data class FirestoreMedia(
    @DocumentId val documentId: String = "",
    @get:PropertyName("mediaKey") @set:PropertyName("mediaKey")
    var mediaKey: String = "",
    var type: String = "",
    var url: String? = null,
    @get:PropertyName("previewImageUrl") @set:PropertyName("previewImageUrl")
    var previewImageUrl: String? = null,
    var width: Int = 0,
    var height: Int = 0,
    @get:PropertyName("durationMs") @set:PropertyName("durationMs")
    var durationMs: Int? = 0,
    @get:PropertyName("altText") @set:PropertyName("altText")
    var altText: String? = null,
    @get:PropertyName("tweetId") @set:PropertyName("tweetId")
    var tweetId: String? = null,
    // HLS / DASH / progressive video stream variants. The server already writes these
    // (the poll function's `variants` mapping); before this field existed Firestore's
    // CustomClassMapper silently dropped them on read — closing that gap is the real
    // Android-side work for inline video. Read as untyped maps so BOTH the defensive
    // camelCase write (`{bitRate,contentType,url}`) and any raw-spread snake_case doc
    // (`{bit_rate,content_type,url}`) deserialize; [toVariant] reconciles the two.
    @get:PropertyName("variants") @set:PropertyName("variants")
    var variants: List<Map<String, Any?>>? = null,
) {
    // The server writes media docs keyed by mediaKey with NO tweetId field (the
    // tweet↔media link is a separate `includes` doc), so `this.tweetId` is null for
    // every synced media row. Callers that know the parent tweet (the assembly map key)
    // pass it explicitly via this parameter; the default preserves every other call site.
    fun toTweetMediaEntity(tweetId: String? = this.tweetId): TweetMediaEntity = TweetMediaEntity(
        mediaKey = mediaKey,
        type = type,
        url = url ?: previewImageUrl,
        previewImageUrl = previewImageUrl,
        width = width,
        height = height,
        durationMs = durationMs ?: 0,
        altText = altText,
        tweetId = tweetId,
        videoVariants = variants?.mapNotNull { it.toVariant() }?.takeIf { it.isNotEmpty() },
    )

    companion object {
        fun fromTweetMediaEntity(entity: TweetMediaEntity): FirestoreMedia = FirestoreMedia(
            mediaKey = entity.mediaKey,
            type = entity.type,
            url = entity.url,
            previewImageUrl = entity.previewImageUrl,
            width = entity.width,
            height = entity.height,
            durationMs = entity.durationMs,
            altText = entity.altText,
            tweetId = entity.tweetId,
            // Round-trip variants in the canonical camelCase shape so an Android-originated
            // re-upload never drops them.
            variants = entity.videoVariants?.map {
                mapOf("bitRate" to it.bitRate, "contentType" to it.contentType, "url" to it.url)
            },
        )

        /**
         * Reconcile one Firestore variant map into a [Variant], accepting either the
         * defensive camelCase keys or the raw X-API snake_case keys. Drops a map with no
         * `url` (a variant with no stream is unplayable). Firestore stores numbers as
         * [Number], so `bitRate` is coerced; a missing bit rate defaults to 0.
         */
        private fun Map<String, Any?>.toVariant(): Variant? {
            val url = this["url"] as? String ?: return null
            val contentType = (this["contentType"] ?: this["content_type"]) as? String ?: return null
            val bitRate = ((this["bitRate"] ?: this["bit_rate"]) as? Number)?.toInt() ?: 0
            return Variant(bitRate = bitRate, contentType = contentType, url = url)
        }
    }
}

/**
 * Firestore document model for metrics collection
 */
// Field names are camelCase: the Android client is the writer of record for
// metrics docs post-cutover, so its keys (likeCount, …) are the canonical
// wire format. The server poll's snake_case overlay (like_count, …) is
// best-effort additive and appears on only a subset of docs; CustomClassMapper
// logs a one-time warning per unknown key and ignores it. Count fields are
// nullable Int because some docs in the wild store these as explicit `null`
// (impressionCount in particular is null on 100% of sampled docs); a primitive
// `Int` setter would throw `IllegalArgumentException` on deserialize and
// abort the whole tweet batch.
data class FirestoreMetrics(
    @DocumentId val documentId: String = "",
    @get:PropertyName("tweetId") @set:PropertyName("tweetId")
    var tweetId: String = "",
    @get:PropertyName("likeCount") @set:PropertyName("likeCount")
    var likeCount: Int? = 0,
    @get:PropertyName("retweetCount") @set:PropertyName("retweetCount")
    var retweetCount: Int? = 0,
    @get:PropertyName("replyCount") @set:PropertyName("replyCount")
    var replyCount: Int? = 0,
    @get:PropertyName("quoteCount") @set:PropertyName("quoteCount")
    var quoteCount: Int? = 0,
    @get:PropertyName("bookmarkCount") @set:PropertyName("bookmarkCount")
    var bookmarkCount: Int? = 0,
    @get:PropertyName("impressionCount") @set:PropertyName("impressionCount")
    var impressionCount: Int? = null
) {
    fun toTweetPublicMetrics(): TweetPublicMetrics = TweetPublicMetrics(
        retweetCount = retweetCount,
        replyCount = replyCount,
        likeCount = likeCount,
        quoteCount = quoteCount,
        viewCount = impressionCount,
        tweetId = tweetId
    )

    companion object {
        fun fromTweetPublicMetrics(entity: TweetPublicMetrics): FirestoreMetrics = FirestoreMetrics(
            tweetId = entity.tweetId ?: "",
            likeCount = entity.likeCount ?: 0,
            retweetCount = entity.retweetCount ?: 0,
            replyCount = entity.replyCount ?: 0,
            quoteCount = entity.quoteCount ?: 0,
            impressionCount = entity.viewCount
        )
    }
}

/**
 * Firestore document model for includes collection
 */
data class FirestoreIncludes(
    @DocumentId val documentId: String = "",
    @get:PropertyName("tweetId") @set:PropertyName("tweetId")
    var tweetId: String = "",
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String? = null,
    @get:PropertyName("mediaKey") @set:PropertyName("mediaKey")
    var mediaKey: String? = null,
    @get:PropertyName("referencedTweetId") @set:PropertyName("referencedTweetId")
    var referencedTweetId: String? = null,
    // The X reference type ("quoted" / "replied_to" / "retweeted") and the doc kind
    // ("referenced_tweet"), written on the _ref_ doc by the poll. The repository
    // filters to type == "quoted" so only quoted references hydrate a quote; the other
    // types are ignored (the dangerous tweetIncludes FK relation stays dropped).
    var type: String? = null,
    var kind: String? = null
) {
    fun toTweetIncludesEntity(): TweetIncludesEntity = TweetIncludesEntity(
        tweetId = tweetId,
        twitterUser = userId,
        mediaKey = mediaKey,
        referencedTweetId = referencedTweetId
    )

    companion object {
        fun fromTweetIncludesEntity(entity: TweetIncludesEntity): FirestoreIncludes = FirestoreIncludes(
            tweetId = entity.tweetId,
            userId = entity.twitterUser,
            mediaKey = entity.mediaKey,
            referencedTweetId = entity.referencedTweetId
        )
    }
}

/**
 * Firestore document model for textAnnotations collection
 */
data class FirestoreTextAnnotation(
    @DocumentId val documentId: String = "",
    @get:PropertyName("tweetId") @set:PropertyName("tweetId")
    var tweetId: String = "",
    var type: String = "",
    var start: Int = 0,
    var end: Int = 0,
    var url: String? = null,
    @get:PropertyName("expandedUrl") @set:PropertyName("expandedUrl")
    var expandedUrl: String? = null,
    @get:PropertyName("displayUrl") @set:PropertyName("displayUrl")
    var displayUrl: String? = null,
    @get:PropertyName("unwoundUrl") @set:PropertyName("unwoundUrl")
    var unwoundUrl: String? = null,
    // Link-preview metadata, written server-side by the link-enrichment function
    // (best-effort OpenGraph fetch). `title`/`description` are plain keys; the
    // image needs the camelCase alias so the writer + reader agree. Before these
    // existed the mapper hardcoded null, so the card had no preview content.
    var title: String? = null,
    var description: String? = null,
    // Firestore stores the OpenGraph preview image under camelCase `imageUrl`;
    // toTweetTextEntityAnnotation carries it into the Room `image_url` column below.
    @get:PropertyName("imageUrl") @set:PropertyName("imageUrl")
    var imageUrl: String? = null,
    var username: String? = null,
    var tag: String? = null,
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String? = null
) {
    fun toTweetTextEntityAnnotation(): TweetTextEntityAnnotation = TweetTextEntityAnnotation(
        id = userId,
        start = start,
        end = end,
        product = null,
        status = null,
        tag = tag,
        // Carry the server-enriched preview metadata through to Room (was hardcoded
        // null). `image_url` is the v16 column; title/description already existed.
        title = title,
        description = description,
        imageUrl = imageUrl,
        url = url,
        expandedUrl = expandedUrl,
        displayUrl = displayUrl,
        unwoundUrl = unwoundUrl,
        mediaKey = null,
        normalizedText = null,
        tweetId = tweetId,
        type = type
    )

    companion object {
        fun fromTweetTextEntityAnnotation(entity: TweetTextEntityAnnotation): FirestoreTextAnnotation = FirestoreTextAnnotation(
            tweetId = entity.tweetId ?: "",
            type = entity.type,
            start = entity.start,
            end = entity.end,
            url = entity.url,
            expandedUrl = entity.expandedUrl,
            displayUrl = entity.displayUrl,
            unwoundUrl = entity.unwoundUrl,
            // Round-trip the preview metadata so an Android-originated re-upload
            // never drops a server-enriched title/description/image.
            title = entity.title,
            description = entity.description,
            imageUrl = entity.imageUrl,
            userId = entity.id,
            tag = entity.tag
        )
    }
}
