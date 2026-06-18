package com.github.jayteealao.reddit.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.reddit.models.RedditPostEntity
import com.github.jayteealao.reddit.models.RedditTagCrossRef

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
     * Get all posts excluding tombstoned ids.
     * LEFT JOIN ensures Room's InvalidationTracker watches both tables —
     * tombstone writes auto-invalidate the paging source.
     */
    @Transaction
    @Query("""
        SELECT p.* FROM reddit_posts p
        LEFT JOIN deleted_bookmarks d ON p.id = d.bookmarkId AND d.source = 'reddit'
        WHERE d.bookmarkId IS NULL
        ORDER BY p.`order` DESC
    """)
    fun getPostsTombstoneAware(): PagingSource<Int, RedditPostData>

    @Transaction
    @Query("""
        SELECT p.* FROM reddit_posts p
        LEFT JOIN deleted_bookmarks d ON p.id = d.bookmarkId AND d.source = 'reddit'
        INNER JOIN reddit_tag_crossref rtc ON rtc.postId = p.id
        WHERE d.bookmarkId IS NULL
          AND rtc.tagName IN (:tagNames)
        GROUP BY p.id
        ORDER BY p.`order` DESC
    """)
    fun getPostsByTagsTombstoneAware(tagNames: List<String>): PagingSource<Int, RedditPostData>

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

    // --- Reddit-side tagging (source-scoped; sibling of TweetDao.insertTweetTag/ etc.) ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRedditTagCrossRef(crossRef: RedditTagCrossRef)

    @Query("DELETE FROM reddit_tag_crossref WHERE postId = :postId AND tagName = :tagName")
    suspend fun deleteRedditTagCrossRef(postId: String, tagName: String)

    @Query("SELECT tagName FROM reddit_tag_crossref WHERE postId = :postId")
    suspend fun getTagsForRedditPost(postId: String): List<String>

    @Query("SELECT postId, tagName FROM reddit_tag_crossref WHERE postId IN (:postIds)")
    suspend fun getTagsForRedditPosts(postIds: List<String>): List<RedditTagCrossRef>

    // The `tags` table is shared with feature/twitter — raw SQL avoids a
    // module dependency on feature/twitter's TagEntity. INSERT OR IGNORE
    // ensures we never fail when the tag already exists.
    @Query("INSERT OR IGNORE INTO tags(name) VALUES (:name)")
    suspend fun insertSharedTag(name: String)

    @Query("SELECT name FROM tags ORDER BY name ASC")
    suspend fun getAllSharedTagNames(): List<String>
}
