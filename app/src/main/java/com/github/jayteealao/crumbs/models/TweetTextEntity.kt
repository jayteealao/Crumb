package com.github.jayteealao.crumbs.models

import com.google.gson.annotations.SerializedName

data class TweetTextEntity(
    val annotation: List<TweetTextEntityAnnotation>?,
    val cashtags: List<TweetTextEntityAnnotation>?,
    val hashtags: List<TweetTextEntityAnnotation>?,
    val mentions: List<TweetTextEntityAnnotation>?,
    val urls: List<TweetTextEntityAnnotation>?
)

data class TweetTextEntityAnnotation(
//    this value only exists for mentions
    val id: String?,
    val start: Int,
    val end: Int,
    val product: String?,
    val status: String?,
    val tag: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    @SerializedName("expanded_url")
    val expandedUrl: String?,
    @SerializedName("display_url")
    val displayUrl: String?,
    @SerializedName("unwound_url")
    val unwoundUrl: String?,
    @SerializedName("media_key")
    val mediaKey: String?,
    @SerializedName("normalized_text")
    val normalizedText: String?,
)
