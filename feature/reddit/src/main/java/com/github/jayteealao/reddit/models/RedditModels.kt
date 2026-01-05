package com.github.jayteealao.reddit.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.google.gson.annotations.SerializedName

/**
 * Reddit API Response Models
 */

data class RedditListingResponse(
    val kind: String,
    val data: RedditListingData
)

data class RedditListingData(
    val after: String?,
    val before: String?,
    val children: List<RedditThing>,
    val dist: Int?
)

data class RedditThing(
    val kind: String, // "t1" = comment, "t3" = link/post
    val data: RedditPost
)

data class RedditPost(
    val id: String,
    val name: String, // Full name (e.g., "t3_abc123")
    val title: String,
    val selftext: String, // Post body text
    val author: String,
    val subreddit: String,
    @SerializedName("subreddit_name_prefixed") val subredditPrefixed: String, // e.g., "r/programming"
    @SerializedName("created_utc") val createdUtc: Long, // Unix timestamp
    val url: String,
    val permalink: String,
    val thumbnail: String?,
    @SerializedName("num_comments") val numComments: Int,
    val score: Int,
    val ups: Int,
    val downs: Int,
    @SerializedName("is_self") val isSelf: Boolean, // True if self/text post
    @SerializedName("is_video") val isVideo: Boolean,
    val domain: String,
    @SerializedName("link_flair_text") val linkFlairText: String?,
    @SerializedName("author_flair_text") val authorFlairText: String?,
    val gilded: Int,
    val stickied: Boolean,
    val locked: Boolean,
    val over_18: Boolean
)

/**
 * Room Database Entity for saved Reddit posts
 */
@Entity(
    tableName = "reddit_posts",
    indices = [Index("author"), Index("subreddit")]
)
data class RedditPostEntity(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    val selftext: String,
    val author: String,
    val subreddit: String,
    @ColumnInfo(name = "subreddit_prefixed") val subredditPrefixed: String,
    @ColumnInfo(name = "created_utc") val createdUtc: Long,
    val url: String,
    val permalink: String,
    val thumbnail: String?,
    @ColumnInfo(name = "num_comments") val numComments: Int,
    val score: Int,
    @ColumnInfo(name = "is_self") val isSelf: Boolean,
    @ColumnInfo(name = "is_video") val isVideo: Boolean,
    val domain: String,
    @ColumnInfo(name = "link_flair_text") val linkFlairText: String?,
    val gilded: Int,
    @ColumnInfo(name = "over_18") val over18: Boolean,
    val order: Int = 0 // For sorting saved posts
)

/**
 * User profile response
 */
data class RedditUserResponse(
    val name: String,
    val id: String,
    @SerializedName("icon_img") val iconImg: String?,
    @SerializedName("created_utc") val createdUtc: Long,
    @SerializedName("link_karma") val linkKarma: Int,
    @SerializedName("comment_karma") val commentKarma: Int
)

/**
 * OAuth token response
 */
data class RedditTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("refresh_token") val refreshToken: String?,
    val scope: String
)

/**
 * Full post data with all relations (for Room queries)
 */
data class RedditPostData(
    @Embedded val post: RedditPostEntity
)

/**
 * Conversion functions
 */
fun RedditPost.toEntity(order: Int = 0) = RedditPostEntity(
    id = id,
    name = name,
    title = title,
    selftext = selftext,
    author = author,
    subreddit = subreddit,
    subredditPrefixed = subredditPrefixed,
    createdUtc = createdUtc,
    url = url,
    permalink = permalink,
    thumbnail = thumbnail,
    numComments = numComments,
    score = score,
    isSelf = isSelf,
    isVideo = isVideo,
    domain = domain,
    linkFlairText = linkFlairText,
    gilded = gilded,
    over18 = over_18,
    order = order
)
