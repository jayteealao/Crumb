package com.github.jayteealao.crumbs.data

interface TagRepository {
    suspend fun getTagsForTweet(id: String): List<String>
    suspend fun getAllTags(): List<String>
    suspend fun saveTags(id: String, tags: List<String>)
    suspend fun addTagToTweet(id: String, tagName: String)
    suspend fun removeTagFromTweet(id: String, tagName: String)
}
