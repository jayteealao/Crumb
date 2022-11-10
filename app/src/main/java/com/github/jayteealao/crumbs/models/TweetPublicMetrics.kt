package com.github.jayteealao.crumbs.models

import com.google.gson.annotations.SerializedName

data class TweetPublicMetrics(
    @SerializedName("retweet_count")
    val retweetCount: Int?,
    @SerializedName("reply_count")
    val replyCount: Int?,
    @SerializedName("like_count")
    val likeCount: Int?,
    @SerializedName("quote_count")
    val quoteCount: Int?,
    @SerializedName("view_count")
    val viewCount: Int?,
)

fun tweetPublicMetrics() = TweetPublicMetrics(0, 0, 0, 0, 0)
