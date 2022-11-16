package com.github.jayteealao.crumbs.models.twitter

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
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

/*
* This entity represents a bookmarked tweet, data present in Tweet data class but not present here
* are represented in other tables via foreign keys
* */
@Entity(
    tableName = "tweetEntity",
    indices = [Index("author_id")]
)
data class TweetEntity(
    @PrimaryKey val id: String,
    val text: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "author_id") val authorId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "in_reply_to_user_id") val inReplyToUserId: String?,
    val lang: String?,
    val referenced: Boolean = false,
    val order: Int = 0
)

fun Tweet.toTweetEntity(referenced: Boolean = false) = TweetEntity(
    id,
    text,
    createdAt,
    authorId,
    conversationId,
    inReplyToUserId,
    lang,
    referenced
)

data class TweetData(
    @Embedded val tweet: TweetEntity,
    @Relation(parentColumn = "author_id", entityColumn = "id")
    val user: TwitterUserEntity,
    @Relation(parentColumn = "id", entityColumn = "tweet_id")
    val publicMetrics: TweetPublicMetrics?,
    @Relation(parentColumn = "id", entityColumn = "tweet_id")
    val media: List<TweetMediaEntity>,
    @Relation(parentColumn = "id", entityColumn = "tweet_id")
    val includes: List<TweetIncludesEntity>,
    @Relation(parentColumn = "id", entityColumn = "tweet_id")
    val tweetTextAnnotation: List<TweetTextEntityAnnotation>

)
