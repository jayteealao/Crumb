package com.github.jayteealao.crumbs.debug

import android.content.Context
import com.github.jayteealao.crumbs.db.AppDatabase
import com.github.jayteealao.pref.writeString
import com.github.jayteealao.reddit.data.RedditPrefs
import com.github.jayteealao.reddit.models.RedditPostEntity
import com.github.jayteealao.twitter.data.Prefs
import com.github.jayteealao.twitter.models.TagEntity
import com.github.jayteealao.twitter.models.TweetEntity
import com.github.jayteealao.twitter.models.TweetTagCrossRef
import com.github.jayteealao.twitter.models.TwitterUserEntity
import com.github.jayteealao.twitter.utils.ACCESS_CODE
import dagger.hilt.android.qualifiers.ApplicationContext
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
) {
    suspend fun run(wipe: Boolean) = withContext(Dispatchers.IO) {
        if (wipe) {
            db.clearAllTables()
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
}
