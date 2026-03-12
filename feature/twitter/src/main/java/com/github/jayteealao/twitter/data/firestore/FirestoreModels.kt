package com.github.jayteealao.twitter.data.firestore

import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetIncludesEntity
import com.github.jayteealao.twitter.models.TweetMediaEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TweetTextEntityAnnotation
import com.github.jayteealao.twitter.models.TwitterUserEntity
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

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
    var source: String? = null
) {
    fun toTweetEntity(referenced: Boolean = false): TweetEntity = TweetEntity(
        id = tweetId,
        text = text,
        createdAt = createdAt,
        authorId = authorId,
        conversationId = conversationId,
        inReplyToUserId = inReplyToUserId,
        lang = lang,
        referenced = referenced,
        order = order
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
            order = entity.order
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
    var durationMs: Int = 0,
    @get:PropertyName("altText") @set:PropertyName("altText")
    var altText: String? = null,
    @get:PropertyName("tweetId") @set:PropertyName("tweetId")
    var tweetId: String? = null
) {
    fun toTweetMediaEntity(): TweetMediaEntity = TweetMediaEntity(
        mediaKey = mediaKey,
        type = type,
        url = url ?: previewImageUrl,
        previewImageUrl = previewImageUrl,
        width = width,
        height = height,
        durationMs = durationMs,
        altText = altText,
        tweetId = tweetId
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
            tweetId = entity.tweetId
        )
    }
}

/**
 * Firestore document model for metrics collection
 */
data class FirestoreMetrics(
    @DocumentId val documentId: String = "",
    @get:PropertyName("tweetId") @set:PropertyName("tweetId")
    var tweetId: String = "",
    @get:PropertyName("likeCount") @set:PropertyName("likeCount")
    var likeCount: Int = 0,
    @get:PropertyName("retweetCount") @set:PropertyName("retweetCount")
    var retweetCount: Int = 0,
    @get:PropertyName("replyCount") @set:PropertyName("replyCount")
    var replyCount: Int = 0,
    @get:PropertyName("quoteCount") @set:PropertyName("quoteCount")
    var quoteCount: Int = 0,
    @get:PropertyName("bookmarkCount") @set:PropertyName("bookmarkCount")
    var bookmarkCount: Int = 0,
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
    var referencedTweetId: String? = null
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
        title = null,
        description = null,
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
            userId = entity.id,
            tag = entity.tag
        )
    }
}
