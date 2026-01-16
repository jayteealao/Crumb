package com.github.jayteealao.reddit.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.reddit.models.RedditPostEntity

/**
 * DAO for Reddit saved posts
 */
@Dao
interface RedditDao {

    /**
     * Insert posts into database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPosts(posts: List<RedditPostEntity>)

    /**
     * Insert single post
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPost(post: RedditPostEntity)

    /**
     * Get all posts ordered by order (newest first)
     */
    @Transaction
    @Query("SELECT * FROM reddit_posts ORDER BY `order` DESC")
    fun getPosts(): PagingSource<Int, RedditPostData>

    /**
     * Get latest post (for pagination tracking)
     */
    @Query("SELECT * FROM reddit_posts ORDER BY `order` DESC LIMIT 1")
    suspend fun getLatestPost(): RedditPostEntity?

    /**
     * Delete post by ID
     */
    @Query("DELETE FROM reddit_posts WHERE id = :postId")
    suspend fun deletePost(postId: String)

    /**
     * Search posts by title or selftext
     */
    @Transaction
    @Query("""
        SELECT * FROM reddit_posts
        WHERE title LIKE '%' || :query || '%'
        OR selftext LIKE '%' || :query || '%'
        OR subreddit LIKE '%' || :query || '%'
        ORDER BY `order` DESC
    """)
    fun searchPosts(query: String): PagingSource<Int, RedditPostData>

    /**
     * Get posts from specific subreddit
     */
    @Transaction
    @Query("SELECT * FROM reddit_posts WHERE subreddit = :subreddit ORDER BY `order` DESC")
    fun getPostsBySubreddit(subreddit: String): PagingSource<Int, RedditPostData>

    /**
     * Get all subreddits (for filters)
     */
    @Query("SELECT DISTINCT subreddit FROM reddit_posts ORDER BY subreddit ASC")
    suspend fun getAllSubreddits(): List<String>

    /**
     * Get post count
     */
    @Query("SELECT COUNT(*) FROM reddit_posts")
    suspend fun getPostCount(): Int

    /**
     * Clear all posts
     */
    @Query("DELETE FROM reddit_posts")
    suspend fun clearAll()
}
