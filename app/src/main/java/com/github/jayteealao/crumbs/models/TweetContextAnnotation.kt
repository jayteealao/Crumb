package com.github.jayteealao.crumbs.models

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
