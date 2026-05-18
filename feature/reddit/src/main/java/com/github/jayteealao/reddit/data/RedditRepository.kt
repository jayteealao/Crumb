package com.github.jayteealao.reddit.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.github.jayteealao.crumbs.models.BookmarkSource
import com.github.jayteealao.crumbs.data.DeletedBookmarkRepository
import com.github.jayteealao.crumbs.data.FilterState
import com.github.jayteealao.crumbs.data.SyncErrorBus
import com.github.jayteealao.crumbs.data.SyncErrorEvent
import com.github.jayteealao.reddit.models.RedditPostData
import com.github.jayteealao.reddit.models.toEntity
import com.github.jayteealao.reddit.services.RedditApiService
import com.github.jayteealao.reddit.services.RedditAuthClient
import com.skydoves.sandwich.message
import com.skydoves.sandwich.suspendOnError
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditRepository @Inject constructor(
    private val redditDao: RedditDao,
    private val redditApiService: RedditApiService,
    private val redditAuthClient: RedditAuthClient,
    private val redditPrefs: RedditPrefs,
    private val deletedBookmarkRepository: DeletedBookmarkRepository,
    private val syncErrorBus: SyncErrorBus,
    private val scope: CoroutineScope
) {
    private var latestPostInDatabase: com.github.jayteealao.reddit.models.RedditPostEntity? = null
    private var orderOfLastPost: Int = 1000
    private val fetchMutex = Mutex()
    // Guards concurrent token refresh attempts; pairs with [refreshTokenSingleFlight]
    // so a 401 storm from parallel paginations does not spawn duplicate refresh calls.
    private val refreshMutex = Mutex()

    companion object {
        const val BUFFER = 250
        const val PAGE_SIZE = 100 // Max allowed by Reddit API
    }

    init {
        scope.launch(Dispatchers.IO) {
            latestPostInDatabase = redditDao.getLatestPost()
            Timber.d("Latest Reddit post in database: $latestPostInDatabase")
            if (latestPostInDatabase != null) {
                orderOfLastPost = latestPostInDatabase!!.order
            }
        }
    }

    /**
     * Fetch and save all saved posts from Reddit
     * Supports pagination to get all posts (up to API limits)
     */
    fun buildDatabase() {
        scope.launch(Dispatchers.IO) {
            // Single-flight: tryLock fails fast if another fetch holds the mutex.
            // Mutex + try/finally ensures the lock is released even on cancellation,
            // unlike the prior split-lock pattern that could orphan an `isFetching=true`.
            if (!fetchMutex.tryLock()) {
                Timber.d("buildDatabase: Already fetching, skipping")
                return@launch
            }

            try {
                latestPostInDatabase = redditDao.getLatestPost()
                Timber.d("Latest Reddit post in database: $latestPostInDatabase")
                if (latestPostInDatabase != null) {
                    orderOfLastPost = latestPostInDatabase!!.order
                }

                // Get access token from prefs
                val accessToken = redditPrefs.accessToken.first()
                val refreshToken = redditPrefs.refreshToken.first()

                if (accessToken.isNotBlank()) {
                    Timber.d("Building Reddit database: fetching saved posts")

                    var after: String? = null
                    var orderStart = orderOfLastPost + BUFFER
                    var fetchedCount = 0
                    val tombstones = deletedBookmarkRepository.deletedIdsSnapshot(BookmarkSource.Reddit)

                    // Fetch all pages until no more results
                    var hasMore: Boolean
                    do {
                        val response = redditApiService.getSavedPosts(
                            authorization = "Bearer $accessToken",
                            limit = PAGE_SIZE,
                            after = after
                        )

                        var entitiesToInsert: List<com.github.jayteealao.reddit.models.RedditPostEntity>? = null
                        hasMore = false

                        response.suspendOnSuccess {
                            Timber.d("Fetched ${data.data.children.size} Reddit posts")

                            // Prepare posts for database; gate on tombstone presence
                            entitiesToInsert = data.data.children
                                .filter { it.kind == "t3" }
                                .filter { it.data.id !in tombstones }
                                .map { thing ->
                                    val order = orderStart--
                                    thing.data.toEntity(order)
                                }

                            fetchedCount += entitiesToInsert?.size ?: 0

                            // Check if there are more pages
                            after = data.data.after
                            hasMore = after != null

                        }.suspendOnError {
                            Timber.e("Error fetching Reddit posts: ${message()}")

                            if (statusCode.code == 401) {
                                // Refresh-first: try silent recovery before alarming the
                                // user. Await the refresh (single-flight) so any retry
                                // sees the persisted token; only banner on hard failure.
                                val recovered = refreshToken.isNotBlank() &&
                                    refreshTokenSingleFlight(refreshToken)
                                if (!recovered) {
                                    syncErrorBus.emit(SyncErrorEvent.RedditAuth401())
                                }
                            }
                            // Any error (401 or otherwise) breaks the pagination loop
                            // — hasMore is already false here, but keeping the comment
                            // pins the contract so future edits do not reintroduce a
                            // runaway fetch on transient network errors.
                        }

                        // Insert entities outside the callback
                        entitiesToInsert?.let { entities ->
                            redditDao.insertPosts(entities)
                        }

                    } while (hasMore && fetchedCount < 800)

                    Timber.d("Finished fetching Reddit posts. Total: $fetchedCount")
                }
            } finally {
                fetchMutex.unlock()
            }
        }
    }

    /**
     * Get paged posts from database
     */
    fun getPagingPosts() = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = { redditDao.getPostsTombstoneAware() }
    ).flow

    fun pagingPostsData(filter: FilterState): Flow<PagingData<RedditPostData>> {
        val sourceFactory: () -> androidx.paging.PagingSource<Int, RedditPostData> =
            if (filter.selectedTags.isNotEmpty()) {
                { redditDao.getPostsByTagsTombstoneAware(filter.selectedTags.toList()) }
            } else {
                { redditDao.getPostsTombstoneAware() }
            }
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = sourceFactory,
        ).flow
    }

    /**
     * Delete a post from saved
     */
    suspend fun deletePost(postId: String) {
        redditDao.deletePost(postId)
        // TODO: Also unsave from Reddit API
    }

    suspend fun softDelete(id: String) {
        deletedBookmarkRepository.softDelete(id, BookmarkSource.Reddit)
    }

    suspend fun undoDelete(id: String) {
        deletedBookmarkRepository.undoDelete(id, BookmarkSource.Reddit)
    }

    /**
     * Search posts
     */
    fun searchPosts(query: String) = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = { redditDao.searchPosts(query) }
    ).flow

    /**
     * Get posts by subreddit
     */
    fun getPostsBySubreddit(subreddit: String) = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = { redditDao.getPostsBySubreddit(subreddit) }
    ).flow

    /**
     * Single-flight access-token refresh. Returns true if a fresh access token has
     * been persisted by [RedditAuthClient.refreshAccessToken] (it writes to Prefs
     * internally), false on hard failure.
     *
     * tryLock semantics: if another caller is already refreshing, skip and return
     * true — the concurrent refresh will populate Prefs and the next buildDatabase
     * invocation will pick up the new token.
     */
    private suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean {
        if (!refreshMutex.tryLock()) {
            Timber.d("refreshTokenSingleFlight: another refresh in flight, deferring")
            return true
        }
        return try {
            val newAccess = redditAuthClient.refreshAccessToken(currentRefreshToken)
            if (!newAccess.isNullOrBlank()) {
                Timber.d("refreshTokenSingleFlight: Reddit token refreshed")
                true
            } else {
                Timber.w("refreshTokenSingleFlight: Reddit refresh returned null")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "refreshTokenSingleFlight: exception during Reddit refresh")
            false
        } finally {
            refreshMutex.unlock()
        }
    }

    /**
     * Clear stored Reddit auth state. Local-only — no Reddit revoke endpoint call.
     */
    suspend fun logout() {
        redditPrefs.clearTokens()
        Timber.d("Reddit tokens cleared")
    }
}
