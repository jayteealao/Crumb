package com.github.jayteealao.crumbs.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Query
import androidx.room.Transaction
import com.github.jayteealao.twitter.models.TweetData
import com.github.jayteealao.twitter.models.TweetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Full-text search shadow over [TweetEntity.text]. External-content FTS — Room
 * keeps the index in sync via triggers tied to the parent `tweetEntity` table.
 * The `rowid` linkage is SQLite's implicit row identifier on `tweetEntity`
 * (the String `id` PK is not used for the FTS join).
 */
@Entity(tableName = "tweet_fts")
@Fts4(
    contentEntity = TweetEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
data class TweetFts(
    val text: String,
)

@Dao
interface TweetFtsDao {
    /**
     * Search returns the full [TweetData] aggregate (TweetEntity + relations)
     * so callers can map directly to the existing `Bookmark` UI model via
     * `TweetData.toBookmark()` — no extra round-trip to enrich author /
     * media / public-metrics after the FTS hit. The tombstone LEFT JOIN
     * mirrors the feed paging queries so soft-deleted bookmarks never leak
     * into search results.
     */
    @Transaction
    @Query(
        """
        SELECT t.* FROM tweetEntity t
        JOIN tweet_fts f ON t.rowid = f.rowid
        LEFT JOIN deleted_bookmarks d ON t.id = d.bookmarkId AND d.source = 'twitter'
        WHERE tweet_fts MATCH :q
          AND t.referenced = 0
          AND d.bookmarkId IS NULL
        ORDER BY t.`order` DESC
        LIMIT 50
        """
    )
    fun search(q: String): Flow<List<TweetData>>
}
