package com.github.jayteealao.crumbs.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-uid checkpoint for the streaming Twitter sync. Two cursors are tracked:
 *
 * - **High watermark** (`lastHighCursor*`): newest [createdAt] seen across all
 *   syncs. Advanced on every fetched batch; used to ask Firestore "anything
 *   newer than this?" on the next run.
 * - **Low watermark** (`lastLowCursor*`): oldest [createdAt] successfully
 *   written to Room. Advanced on every transaction commit; used to resume
 *   a backfill that was killed mid-stream.
 *
 * The cursor pair is `(createdAt, tweetId)`. The secondary `tweetId` key
 * disambiguates docs that share the same server-stamped createdAt (common
 * inside a single 30-doc Firestore poll batch).
 *
 * Nullable cursor fields model "fresh install — no sync has run yet."
 * One row per uid; the upsert overwrites by definition.
 */
@Entity(tableName = "sync_progress")
data class SyncProgress(
    @PrimaryKey val uid: String,
    @ColumnInfo(name = "last_high_cursor_created_at") val lastHighCursorCreatedAt: String?,
    @ColumnInfo(name = "last_high_cursor_tweet_id") val lastHighCursorTweetId: String?,
    @ColumnInfo(name = "last_low_cursor_created_at") val lastLowCursorCreatedAt: String?,
    @ColumnInfo(name = "last_low_cursor_tweet_id") val lastLowCursorTweetId: String?,
    @ColumnInfo(name = "total_batches_ingested") val totalBatchesIngested: Int,
    @ColumnInfo(name = "last_updated_at_ms") val lastUpdatedAtMs: Long,
)
