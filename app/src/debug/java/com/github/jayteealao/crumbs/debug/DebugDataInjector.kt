package com.github.jayteealao.crumbs.debug

import android.content.Context
import androidx.room.withTransaction
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.pref.writeString
import com.github.jayteealao.reddit.data.RedditPrefs
import com.github.jayteealao.reddit.models.RedditPostEntity
import com.github.jayteealao.twitter.data.Prefs
import com.github.jayteealao.twitter.data.SyncStatusRepository
import com.github.jayteealao.twitter.data.dto.SyncStatus
import com.github.jayteealao.crumbs.data.SyncProgress
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetPublicMetrics
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.TwitterUserEntity
import com.github.jayteealao.crumbs.migration.MigrationKeys
import com.github.jayteealao.pref.readString
import com.github.jayteealao.twitter.utils.ACCESS_CODE
import com.github.jayteealao.twitter.utils.REFRESH_CODE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug-only deterministic seed. Lives in `app/src/debug/` so AGP excludes it from release.
 * Reached from MainActivity via reflective Class.forName + DebugIntentHandler.
 */
@Singleton
class DebugDataInjector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val twitterPrefs: Prefs,
    private val redditPrefs: RedditPrefs,
    private val syncStatusRepository: SyncStatusRepository,
) {

    /**
     * Pushes a synthesized SyncStatus into SyncStatusRepository so Maestro
     * flows can deterministically drive the reconnect banner and Connect-X
     * route-guard without a live Firestore round-trip.
     */
    fun seedSyncStatus(linked: Boolean) {
        syncStatusRepository.seedForDebug(SyncStatus(linked = linked))
    }

    suspend fun run(wipe: Boolean) = withContext(Dispatchers.IO) {
        if (wipe) {
            // Tombstones survive the debug seed wipe so a developer doing
            // repeat seed+sync cycles does not see permanently-deleted
            // bookmarks reappear. The whole snapshot → clear → restore
            // round-trip runs inside one Room transaction so a torn process
            // can't leave the DB in a wiped-but-not-restored state.
            db.withTransaction {
                val preservedTombstones = db.deletedBookmarkDao().getAllDeleted()
                db.clearAllTables()
                if (preservedTombstones.isNotEmpty()) {
                    db.deletedBookmarkDao().insertAll(preservedTombstones)
                }
            }
        }
        seedTwitter()
        seedReddit()
        seedTags()
        seedAuthTokens()
    }

    private suspend fun seedAuthTokens() {
        twitterPrefs.setAccessAndRefreshToken("DEBUG_TWITTER_ACCESS", "DEBUG_TWITTER_REFRESH")
        twitterPrefs.setUserId("debug-user-twitter")
        twitterPrefs.setUserName("crumbs_test")
        redditPrefs.saveAccessToken("DEBUG_REDDIT_ACCESS")
        redditPrefs.saveRefreshToken("DEBUG_REDDIT_REFRESH")
        redditPrefs.saveUsername("crumbs_test")
    }

    /** Writes a malformed token to the Twitter ACCESS_CODE pref. Next sync emits 401. */
    suspend fun corruptTwitterToken() = withContext(Dispatchers.IO) {
        context.writeString(ACCESS_CODE, "INVALID_DEBUG_TOKEN")
    }

    /**
     * Seeds a synthetic legacy access/refresh-token pair and clears the
     * X_TOKEN_MIGRATED flag so the migration WorkManager runner fires on the
     * next cold launch. Used by maestro/upgrade_install.yaml.
     */
    suspend fun seedLegacyXTokens() = withContext(Dispatchers.IO) {
        context.writeString(ACCESS_CODE, "DEBUG_LEGACY_ACCESS")
        context.writeString(REFRESH_CODE, "DEBUG_LEGACY_REFRESH")
        context.writeString(MigrationKeys.X_TOKEN_MIGRATED, "")
    }

    /** Reads the X_TOKEN_MIGRATED flag for Maestro readback. */
    suspend fun readXTokenMigrationFlag(): String =
        context.readString(MigrationKeys.X_TOKEN_MIGRATED).first()

    /**
     * Seeds two `pendingDelete = true` tweet rows so Maestro can drive the
     * swipe-to-confirm / swipe-to-cancel flow deterministically without a
     * live daily-poll round-trip. Author row is reused from [seedTwitter].
     */
    suspend fun seedPendingDelete() = withContext(Dispatchers.IO) {
        seedAuthTokens()
        val dao = db.tweetDao()
        val user = TwitterUserEntity(
            id = "debug-user-twitter",
            name = "Crumbs Test User",
            username = "crumbs_test",
            profileImageUrl = null,
            verified = false,
            verifiedType = null,
            description = "Debug-only seeded user",
            mentionedIn = null,
        )
        dao.insertTwitterUser(user)
        dao.insertTweet(
            TweetEntity(
                id = "debug-pending-1",
                text = "Pending removal — swipe right to confirm, left to cancel.",
                createdAt = "2026-05-21T00:00:00Z",
                authorId = user.id,
                conversationId = "debug-pending-1",
                inReplyToUserId = null,
                lang = "en",
                referenced = false,
                order = 1100,
                pendingDelete = true,
            )
        )
        dao.insertTweet(
            TweetEntity(
                id = "debug-pending-2",
                text = "Another removal candidate seeded for the cancel-swipe path.",
                createdAt = "2026-05-21T00:01:00Z",
                authorId = user.id,
                conversationId = "debug-pending-2",
                inReplyToUserId = null,
                lang = "en",
                referenced = false,
                order = 1099,
                pendingDelete = true,
            )
        )
    }

    private fun seedTwitter() {
        val dao = db.tweetDao()
        val user = TwitterUserEntity(
            id = "debug-user-twitter",
            name = "Crumbs Test User",
            username = "crumbs_test",
            profileImageUrl = null,
            verified = false,
            verifiedType = null,
            description = "Debug-only seeded user",
            mentionedIn = null,
        )
        dao.insertTwitterUser(user)
        // Descending retrieved-at stamps (debug-tweet-1 most recent) so the seeded feed renders
        // in a stable recency order under the `retrieved_at DESC` sort and the labels read as
        // small relative times during manual/Maestro runs. Anchored to launch time.
        val nowMs = System.currentTimeMillis()
        listOf(
            TweetEntity(
                id = "debug-tweet-1",
                text = "Brutalist design system applied to bookmarks. Mono everything.",
                createdAt = "2026-05-18T00:00:00Z",
                authorId = user.id,
                conversationId = "debug-tweet-1",
                inReplyToUserId = null,
                lang = "en",
                referenced = false,
                order = 1000,
                retrievedAt = nowMs - 60_000L,
            ),
            TweetEntity(
                id = "debug-tweet-2",
                text = "Thread on Compose semantics + testTagsAsResourceId quirks.",
                createdAt = "2026-05-18T00:01:00Z",
                authorId = user.id,
                conversationId = "debug-tweet-2",
                inReplyToUserId = null,
                lang = "en",
                referenced = false,
                order = 999,
                retrievedAt = nowMs - 120_000L,
            ),
            TweetEntity(
                id = "debug-tweet-3",
                text = "Reading list: brutalist typography references.",
                createdAt = "2026-05-18T00:02:00Z",
                authorId = user.id,
                conversationId = "debug-tweet-3",
                inReplyToUserId = null,
                lang = "en",
                referenced = false,
                order = 998,
                retrievedAt = nowMs - 180_000L,
            ),
            TweetEntity(
                id = "debug-tweet-4",
                text = "Finance dashboard ideas. Long-form note.",
                createdAt = "2026-05-18T00:03:00Z",
                authorId = user.id,
                conversationId = "debug-tweet-4",
                inReplyToUserId = null,
                lang = "en",
                referenced = false,
                order = 997,
                retrievedAt = nowMs - 240_000L,
            ),
        ).forEach(dao::insertTweet)
    }

    private suspend fun seedReddit() {
        val dao = db.redditDao()
        listOf(
            RedditPostEntity(
                id = "debug-post-1",
                name = "t3_debug-post-1",
                title = "Show HN: Brutalist Android UI references",
                selftext = "Sharing some inspirations for mono-typography app design.",
                author = "crumbs_test",
                subreddit = "androiddev",
                subredditPrefixed = "r/androiddev",
                createdUtc = 1747526400,
                url = "https://reddit.com/r/androiddev/debug-1",
                permalink = "/r/androiddev/comments/debug-1",
                thumbnail = null,
                numComments = 12,
                score = 87,
                isSelf = true,
                isVideo = false,
                domain = "self.androiddev",
                linkFlairText = "Discussion",
                gilded = 0,
                over18 = false,
                order = 1000,
            ),
            RedditPostEntity(
                id = "debug-post-2",
                name = "t3_debug-post-2",
                title = "Compose semantics: testTagsAsResourceId quirks",
                selftext = "",
                author = "crumbs_test",
                subreddit = "Kotlin",
                subredditPrefixed = "r/Kotlin",
                createdUtc = 1747526460,
                url = "https://example.com/article",
                permalink = "/r/Kotlin/comments/debug-2",
                thumbnail = null,
                numComments = 5,
                score = 42,
                isSelf = false,
                isVideo = false,
                domain = "example.com",
                linkFlairText = "Article",
                gilded = 0,
                over18 = false,
                order = 999,
            ),
            RedditPostEntity(
                id = "debug-post-3",
                name = "t3_debug-post-3",
                title = "Brutalist web designs collection",
                selftext = "",
                author = "crumbs_test",
                subreddit = "design",
                subredditPrefixed = "r/design",
                createdUtc = 1747526520,
                url = "https://v.redd.it/debug-3",
                permalink = "/r/design/comments/debug-3",
                thumbnail = null,
                numComments = 1,
                score = 5,
                isSelf = false,
                isVideo = true,
                domain = "v.redd.it",
                linkFlairText = null,
                gilded = 0,
                over18 = false,
                order = 998,
            ),
            RedditPostEntity(
                id = "debug-post-4",
                name = "t3_debug-post-4",
                title = "Why monospace fonts work for product UI",
                selftext = "Long-form thread on legibility tradeoffs.",
                author = "crumbs_test",
                subreddit = "typography",
                subredditPrefixed = "r/typography",
                createdUtc = 1747526580,
                url = "https://reddit.com/r/typography/debug-4",
                permalink = "/r/typography/comments/debug-4",
                thumbnail = null,
                numComments = 23,
                score = 156,
                isSelf = true,
                isVideo = false,
                domain = "self.typography",
                linkFlairText = "Discussion",
                gilded = 0,
                over18 = false,
                order = 997,
            ),
        ).forEach { dao.insertPost(it) }
    }

    private suspend fun seedTags() {
        val dao = db.tweetDao()
        listOf(
            TagEntity("design"),
            TagEntity("tech"),
            TagEntity("finance"),
            TagEntity("collection-reading-list"),
            TagEntity("collection-archive"),
        ).forEach { dao.insertTag(it) }
        dao.insertTweetTag(TweetTagCrossRef("debug-tweet-1", "design"))
        dao.insertTweetTag(TweetTagCrossRef("debug-tweet-2", "tech"))
    }

    /**
     * Hydrate a synthetic local corpus of `tweetCount` tweets with monotonically
     * decreasing `createdAt` values so the `incremental_sync_visibility.yaml`
     * Maestro flow can assert that the newest cards paint quickly. The operator
     * Firestore is the source-of-truth in production verify runs; this helper
     * exists so the flow can run as a smoke check without a live Firestore.
     */
    suspend fun seedIncrementalSyncCorpus(tweetCount: Int) = withContext(Dispatchers.IO) {
        val tweetDao = db.tweetDao()
        val baseEpochMs = System.currentTimeMillis()
        val author = TwitterUserEntity(
            id = "debug-author-incremental",
            name = "Crumbs Incremental Sync Tester",
            username = "crumbs_incremental",
            profileImageUrl = "https://example.com/avatar.png",
            verified = false,
            verifiedType = null,
            description = null,
            mentionedIn = null,
        )
        tweetDao.insertTwitterUser(author)
        var order = (tweetDao.getMaxOrder() ?: 1000) + 1
        repeat(tweetCount) { idx ->
            // 60s apart so the lexicographic ISO-8601 sort is unambiguous; the
            // first tweet (idx=0) is the newest.
            val createdAt = java.time.Instant.ofEpochMilli(baseEpochMs - (idx * 60_000L)).toString()
            val tweetId = "debug-incremental-${"%04d".format(idx)}"
            val tweet = TweetEntity(
                id = tweetId,
                text = "Brutalist sync seed #$idx — synthetic for AC2 visibility flow.",
                createdAt = createdAt,
                authorId = author.id,
                conversationId = tweetId,
                inReplyToUserId = null,
                lang = "en",
                referenced = false,
                order = order++,
                pendingDelete = false,
            )
            val metrics = TweetPublicMetrics(
                retweetCount = 0,
                replyCount = 0,
                likeCount = 0,
                quoteCount = 0,
                viewCount = 0,
                tweetId = tweetId,
            )
            tweetDao.insertTweet(tweet)
            tweetDao.insertTweetPublicMetrics(metrics)
        }
    }

    /**
     * Pre-seed a `sync_progress` row at batch K so the
     * `incremental_sync_resume.yaml` Maestro flow can assert that the next
     * worker invocation reports `resumed_from_cursor batchIdx=K` (visible in
     * the lazylogcat capture) instead of starting from zero.
     */
    suspend fun seedPartialSyncProgress(batchK: Int) = withContext(Dispatchers.IO) {
        val uid = "debug-user-twitter"
        val nowMs = System.currentTimeMillis()
        val cursorCreatedAt = java.time.Instant.ofEpochMilli(nowMs - (batchK * 30L * 60_000L)).toString()
        db.syncProgressDao().upsert(
            SyncProgress(
                uid = uid,
                lastHighCursorCreatedAt = cursorCreatedAt,
                lastHighCursorTweetId = "debug-cursor-tweet",
                lastLowCursorCreatedAt = cursorCreatedAt,
                lastLowCursorTweetId = "debug-cursor-tweet",
                totalBatchesIngested = batchK,
                lastUpdatedAtMs = nowMs,
            )
        )
    }
}
