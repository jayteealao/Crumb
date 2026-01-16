package com.github.jayteealao.twitter.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tag entity for categorizing bookmarks
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    val name: String // Tag name is the primary key (unique tags)
)

/**
 * Cross-reference table linking tweets to tags
 * Many-to-many relationship
 */
@Entity(
    tableName = "tweet_tags",
    primaryKeys = ["tweetId", "tagName"],
    foreignKeys = [
        ForeignKey(
            entity = TweetEntity::class,
            parentColumns = ["id"],
            childColumns = ["tweetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tagName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tweetId"), Index("tagName")]
)
data class TweetTagCrossRef(
    val tweetId: String,
    val tagName: String
)
