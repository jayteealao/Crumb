package com.github.jayteealao.twitter.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

data class TweetContextAnnotation(
    val domain: ContextAnnotationDomain,
    val entity: ContextAnnotationEntity

)

data class ContextAnnotationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val desc: String
)

data class ContextAnnotationDomain(
    val id: String,
    val name: String,
    val desc: String
)

@Entity(
    tableName = "tweetContextAnnotation",
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
data class TweetContextAnnotationEntity(

    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "domain_id") val domainId: String,
    @ColumnInfo(name = "tweet_id") val tweetId: String,
    @ColumnInfo(name = "domain_name") val domainName: String,
    @ColumnInfo(name = "entity_name") val entityName: String,
    @ColumnInfo(name = "domain_desc") val domainDesc: String?,
    @ColumnInfo(name = "entity_desc") val entityDesc: String?

)

fun TweetContextAnnotation.toTweetContextAnnotationEntity(tweetId: String) =
    TweetContextAnnotationEntity(
        entityId = entity.id,
        domainId = domain.id,
        tweetId = tweetId,
        domainName = domain.name,
        entityName = entity.name,
        domainDesc = domain.name,
        entityDesc = entity.desc
    )
