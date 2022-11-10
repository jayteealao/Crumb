package com.github.jayteealao.crumbs.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

data class TweetTextEntity(
    val annotation: List<TweetTextEntityAnnotation>?,
    val cashtags: List<TweetTextEntityAnnotation>?,
    val hashtags: List<TweetTextEntityAnnotation>?,
    val mentions: List<TweetTextEntityAnnotation>?,
    val urls: List<TweetTextEntityAnnotation>?
)

@Entity(
    tableName = "tweetTextEntityAnnotation",
    foreignKeys = [
        ForeignKey(
            entity = TweetEntity::class,
            parentColumns = ["id"],
            childColumns = ["tweet_id"]
        )
    ],
    indices = [
        Index(value = ["tweet_id"])
    ]
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
    @ColumnInfo(name = "expanded_url")
    @SerializedName("expanded_url")
    val expandedUrl: String?,
    @ColumnInfo(name = "display_url")
    @SerializedName("display_url")
    val displayUrl: String?,
    @ColumnInfo(name = "unwound_url")
    @SerializedName("unwound_url")
    val unwoundUrl: String?,
    @ColumnInfo(name = "media_key")
    @SerializedName("media_key")
    val mediaKey: String?,
    @ColumnInfo(name = "normalized_text")
    @SerializedName("normalized_text")
    val normalizedText: String?,
    //    database only
    @ColumnInfo(name = "entity_id")
    @PrimaryKey(autoGenerate = true)
    val entityId: Int = 0,
    @ColumnInfo(name = "tweet_id") val tweetId: String? = null,
    val type: String
)

fun TweetTextEntity.toTweetTextEntityAnnotation(tweetId: String): List<TweetTextEntityAnnotation> =
    emptySequence<TweetTextEntityAnnotation>()
        .plus(annotation?.map { it.copy(tweetId = tweetId, type = "annotation") } ?: emptyList())
        .plus(cashtags?.map { it.copy(tweetId = tweetId, type = "cashtags") } ?: emptyList())
        .plus(hashtags?.map { it.copy(tweetId = tweetId, type = "hashtags") } ?: emptyList())
        .plus(mentions?.map { it.copy(tweetId = tweetId, type = "mentions") } ?: emptyList())
        .plus(urls?.map { it.copy(tweetId = tweetId, type = "urls") } ?: emptyList())
        .toList()
