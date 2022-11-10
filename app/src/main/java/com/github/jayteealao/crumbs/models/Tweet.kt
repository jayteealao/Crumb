package com.github.jayteealao.crumbs.models

import com.google.gson.annotations.SerializedName

data class Tweet(
    val id: String,
    val text: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("author_id") val authorId: String,
    val attachments: TweetAttachments?,
    val includes: TweetIncludes?,
    @SerializedName("context_annotations") val contextAnnotation: List<TweetContextAnnotation>?,
    @SerializedName("conversation_id") val conversationId: String,
    val entities: TweetTextEntity?,
    @SerializedName("in_reply_to_user_id") val inReplyToUserId: String?,
    val lang: String?,
    @SerializedName("public_metrics") val publicMetrics: TweetPublicMetrics,
    @SerializedName("referenced_tweets") val referencedTweets: List<TweetReferencedTweets>?
)

