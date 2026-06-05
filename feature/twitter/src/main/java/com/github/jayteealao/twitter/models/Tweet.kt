package com.github.jayteealao.twitter.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
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
    @SerializedName("referenced_tweets") val referencedTweets: List<TweetReferencedTweets>?,
    @SerializedName("edit_history_tweet_ids") val editHistoryTweetIds: List<String>? = null,
    @SerializedName("edit_controls") val editControls: TweetEditControls? = null,
    @SerializedName("note_tweet") val noteTweet: NoteTweet? = null,
    @SerializedName("reply_settings") val replySettings: String? = null,
    @SerializedName("possibly_sensitive") val possiblySensitive: Boolean? = null
)

/*
* This entity represents a bookmarked tweet, data present in Tweet data class but not present here
* are represented in other tables via foreign keys
* */
@Entity(
    tableName = "tweetEntity",
    // `order` indexed because the legacy feed query sorted by `order DESC` (retained for
    // the sync worker's max-order assignment). The composite (retrieved_at, created_at)
    // index backs the recency feed sort (`ORDER BY retrieved_at DESC, created_at DESC`),
    // letting it run as a reverse B-tree scan instead of a full-table sort.
    indices = [
        Index("author_id"),
        Index("order"),
        Index(value = ["retrieved_at", "created_at"]),
        // Backs the THREAD type-filter's correlated "sibling shares conversation"
        // subquery (`EXISTS (... WHERE s.conversation_id = t.conversation_id ...)`)
        // so it runs index-backed instead of O(n²). Added in migration v14.
        Index("conversation_id"),
    ]
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
    val order: Int = 0,
    @ColumnInfo(name = "pending_delete") val pendingDelete: Boolean = false,
    // Server-stamped first-seen / poll time (epoch millis); null for legacy rows and any
    // tweet synced before the server began writing it. Drives the feed recency sort.
    @ColumnInfo(name = "retrieved_at") val retrievedAt: Long? = null,
)

fun Tweet.toTweetEntity(referenced: Boolean = false) = TweetEntity(
    id,
    text,
    createdAt,
    authorId,
    conversationId,
    inReplyToUserId,
    lang,
    referenced,
    retrievedAt = System.currentTimeMillis(),
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
    val tweetTextAnnotation: List<TweetTextEntityAnnotation>,

    // Raw referenced-tweet rows for this tweet (quoted / replied_to / retweeted).
    // Knowing a quote was referenced even when its body is absent is what drives the
    // "unavailable" placeholder; the mapper filters to type == "quoted". FK-free
    // junction (no @ForeignKey anywhere on this path), so a row can outlive a missing
    // body. Defaults empty so non-relation query paths and test fixtures still build.
    @Relation(parentColumn = "id", entityColumn = "tweet_id")
    val referencedTweets: List<TweetReferencedTweets> = emptyList(),

    // The resolved quoted-tweet bodies (+ their authors), joined through the FK-free
    // tweetReferencedTweets junction. Empty when the quote is unavailable (a reference
    // row exists in [referencedTweets] but no matching TweetEntity was stored). The
    // quoted TweetEntity is stored referenced=true, so it never surfaces in the feed.
    @Relation(
        entity = TweetEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TweetReferencedTweets::class,
            parentColumn = "tweet_id",
            entityColumn = "id",
        ),
    )
    val quotedTweets: List<QuotedTweetData> = emptyList(),

    // Display-only SQLite rowid, surfaced by the feed queries via
    // `SELECT t.rowid AS db_rowid`. Scalar (not part of the @Embedded entity)
    // so it never participates in writes. Defaults to 0 for any query path that
    // does not project the alias (e.g. relation-only reads), keeping Room from
    // erroring on a missing column. The card renders it as the per-row number.
    @ColumnInfo(name = "db_rowid") val dbRowId: Long = 0L,
)

/**
 * A resolved quoted tweet: its [TweetEntity] body plus the quoting author. [author]
 * is **nullable** so a quoted tweet whose [TwitterUserEntity] was never persisted
 * still hydrates (the card falls back to a generic permalink / handle-less label).
 */
data class QuotedTweetData(
    @Embedded val tweet: TweetEntity,
    @Relation(parentColumn = "author_id", entityColumn = "id")
    val author: TwitterUserEntity?,
)

fun tweetResponseToTweetMapper(tweetData: List<Tweet>, includes: TweetIncludes): List<Tweet> {
    return tweetData.map { tweet ->
        val user = includes.users.first { it?.id == tweet.authorId }
        val media = emptyList<TweetMedia>().toMutableList()
        if (!tweet.attachments?.mediaKeys.isNullOrEmpty()) {
            tweet.attachments?.mediaKeys?.forEach { key ->
                media += includes.media?.filter { it.mediaKey == key } ?: emptyList()
            }
        }
        tweet.copy(
            includes = TweetIncludes(
                users = listOf(user),
                media = media,
                tweets = null
            )
        )
    }
}

/**
 * Edit controls data for tweets that can be edited
 */
data class TweetEditControls(
    @SerializedName("edits_remaining") val editsRemaining: Int?,
    @SerializedName("is_edit_eligible") val isEditEligible: Boolean?,
    @SerializedName("editable_until") val editableUntil: String?
)

/**
 * Note tweet data for long-form posts (posts exceeding 280 characters)
 */
data class NoteTweet(
    val text: String?,
    val entities: TweetTextEntity?
)
