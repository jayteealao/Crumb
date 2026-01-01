package com.github.jayteealao.twitter.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

data class TwitterUser(
    @SerializedName("id") val id: String,
    val name: String,
    val username: String,
    @SerializedName("profile_image_url") val profileImageUrl: String?,
    val verified: Boolean? = false,
    val protected: Boolean? = false,
    @SerializedName("verified_type") val verifiedType: String? = null,
    val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val location: String? = null
)

data class TwitterUserResponse(
    @SerializedName("data") val data: TwitterUser
)

@Entity(
    tableName = "twitterUser",
    //    a foreign key linking the author_id to a user stored in the twitterUser table
    indices = [
        Index("id")
    ]
)
data class TwitterUserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val username: String,
    @ColumnInfo(name = "profile_image_url") val profileImageUrl: String?,
    val verified: Boolean?,
    @ColumnInfo(name = "verified_type") val verifiedType: String?,
    val description: String?,
//    TODO: use this to reference users mentioned in a tweet
    val mentionedIn: String?
)

fun TwitterUser.toTwitterUserEntity(mentionedIn: String? = null) = TwitterUserEntity(
    id,
    name,
    username,
    profileImageUrl,
    verified,
    verifiedType,
    description,
    mentionedIn
)

fun TwitterUserEntity.toTwitterUser() = TwitterUser(
    id,
    name,
    username,
    profileImageUrl,
    verified,
    protected = false,
    verifiedType = verifiedType,
    description = description
)

fun TweetIncludes.extractUsersInTweet(tweet: Tweet): List<TwitterUserEntity> {
    val mentions = tweet.entities?.mentions
        ?.filter { !it.id.isNullOrBlank() }
        ?.map { it.id!! } ?: emptyList()

    return users
        .filter { it?.id == tweet.authorId || it?.id == tweet.inReplyToUserId || mentions.contains(it?.id) }
        .map { if (it!!.id == tweet.authorId) it.toTwitterUserEntity() else it.toTwitterUserEntity(tweet.id) }
}
