package com.github.jayteealao.crumbs.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Query
import androidx.room.Transaction
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.reddit.models.RedditPostEntity
import kotlinx.coroutines.flow.Flow

/**
 * Full-text search shadow over [RedditPostEntity.title] + `selftext`. Same
 * external-content pattern as [TweetFts] — Room maintains the index via
 * trigger-driven sync against `reddit_posts`.
 */
@Entity(tableName = "reddit_fts")
@Fts4(
    contentEntity = RedditPostEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
data class RedditFts(
    val title: String,
    val selftext: String,
)

@Dao
interface RedditFtsDao {
    /**
     * Search returns [RedditPostData] (the @Embedded RedditPostEntity wrapper)
     * so the call site can pivot to a `Bookmark` via the existing
     * `RedditPostData.toBookmark()` helper without an extra lookup. Tombstoned
     * rows are excluded via the LEFT JOIN — same shape as the feed paging
     * queries.
     */
    @Transaction
    @Query(
        """
        SELECT r.* FROM reddit_posts r
        JOIN reddit_fts f ON r.rowid = f.rowid
        LEFT JOIN deleted_bookmarks d ON r.id = d.bookmarkId AND d.source = 'reddit'
        WHERE reddit_fts MATCH :q
          AND d.bookmarkId IS NULL
        ORDER BY r.`order` DESC
        LIMIT 50
        """
    )
    fun search(q: String): Flow<List<RedditPostData>>
}
