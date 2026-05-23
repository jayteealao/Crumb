package com.github.jayteealao.crumbs.data

import com.github.jayteealao.crumbs.db.RedditFtsDao
import com.github.jayteealao.crumbs.db.TweetFtsDao
import com.github.jayteealao.crumbs.models.Bookmark
import com.github.jayteealao.reddit.screens.toBookmark as toRedditBookmark
import com.github.jayteealao.twitter.screens.toBookmark as toTwitterBookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-source full-text search. Hits flow out of two FTS shadow tables and
 * are merged into a single timeline sorted by recency. Both DAOs return
 * tombstone-aware results, so deleted bookmarks never appear in search.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val tweetFtsDao: TweetFtsDao,
    private val redditFtsDao: RedditFtsDao,
) {
    fun search(query: String): Flow<List<Bookmark>> {
        val sanitized = sanitizeFtsQuery(query)
        val tweetHits = tweetFtsDao.search(sanitized).map { rows ->
            rows.map { it.toTwitterBookmark() }
        }
        val redditHits = redditFtsDao.search(sanitized).map { rows ->
            rows.map { it.toRedditBookmark() }
        }
        return combine(tweetHits, redditHits) { t, r ->
            (t + r).sortedByDescending { it.savedAt }
        }
    }

    /**
     * SQLite FTS MATCH inherits the bare-bones FTS3/4 query grammar, which
     * treats `"`, `*`, `-`, `:`, parentheses, and prefix operators as syntax.
     * Wrapping the user input in a phrase quote and escaping inner quotes
     * makes the whole input one literal phrase — the user's keystrokes can
     * never construct an FTS operator that yields confusing results or a
     * `SQLITE_ERROR`.
     */
    private fun sanitizeFtsQuery(q: String): String {
        val escaped = q.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
