package com.github.jayteealao.crumbs.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-uid checkpoint for the streaming Twitter sync. Three cursors are tracked:
 *
 * - **Incremental head watermark** (`lastIncrementalRetrievedAtMs`): the highest
 *   server-stamped `retrievedAt` (epoch-millis) such that every bookmark newer than
 *   it has been synced. Drives the **incremental head** phase — `orderBy(retrievedAt
 *   DESC)` from the top stops once it reaches this value. It is the recency-aligned
 *   resume point: every newly-seen item (including a newly-bookmarked *old* tweet,
 *   which has a fresh `retrievedAt` but an old `createdAt`) sits above it, so the head
 *   catches it where a `createdAt`-only cursor would silently skip it.
 * - **Low watermark** (`lastLowCursor*`): oldest [createdAt] enumerated by the
 *   **backfill tail** phase. Advanced as the historical `orderBy(createdAt DESC)`
 *   walk pages downward; used to resume a backfill that was killed mid-stream so the
 *   next run continues from the last committed position instead of re-scanning.
 * - **High watermark** (`lastHighCursor*`): newest [createdAt] seen. Retained for
 *   logging/back-compat continuity only — **superseded** by the retrievedAt watermark
 *   above as the incremental resume key (a `createdAt` high cursor cannot order legacy
 *   NULL-`retrievedAt` docs and misses newly-bookmarked old tweets). Harmless to keep.
 *
 * The createdAt cursor pair is `(createdAt, tweetId)`. The secondary `tweetId` key
 * disambiguates docs that share the same server-stamped createdAt (common inside a
 * single Firestore page), matching the tail's `(createdAt DESC, __name__ ASC)` order.
 *
 * Nullable cursor fields model "fresh install — no sync has run yet." A null
 * `lastIncrementalRetrievedAtMs` means "seed the watermark from the corpus head on the
 * next sync." One row per uid; the upsert overwrites by definition.
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
    // The incremental-head resume watermark (server `retrievedAt` epoch-millis).
    // Nullable: null = "no incremental watermark established yet, seed on next sync."
    // Added in DB v20 (MIGRATION_19_20) — defaults keep older call sites compiling.
    @ColumnInfo(name = "last_incremental_retrieved_at_ms") val lastIncrementalRetrievedAtMs: Long? = null,
)
