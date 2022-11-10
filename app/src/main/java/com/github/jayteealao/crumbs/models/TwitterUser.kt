package com.github.jayteealao.crumbs.models

import com.google.gson.annotations.SerializedName

data class TwitterUser(
    @SerializedName("id") val id: String,
    val name: String,
    val username: String,
    @SerializedName("profile_image_url") val profileImageUrl: String?,
    val verified: Boolean? = false,
    val protected: Boolean? = false
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

fun TweetIncludes.extractUsersInTweet(tweet: Tweet): List<TwitterUserEntity> {
    val mentions = tweet.entities?.mentions
        ?.filter { !it.id.isNullOrBlank() }
        ?.map { it.id!! } ?: emptyList()

    return users
        .filter { it?.id == tweet.authorId || it?.id == tweet.inReplyToUserId || mentions.contains(it?.id) }
        .map { if (it!!.id == tweet.authorId) it.toTwitterUserEntity() else it.toTwitterUserEntity(tweet.id) }
}
