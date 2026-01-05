package com.github.jayteealao.reddit.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.github.jayteealao.reddit.models.RedditListingResponse
import com.github.jayteealao.reddit.models.toEntity
import com.github.jayteealao.reddit.services.RedditApiService
import com.github.jayteealao.reddit.services.RedditAuthClient
import com.skydoves.sandwich.message
import com.skydoves.sandwich.onError
import com.skydoves.sandwich.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Enable when RedditDao is implemented
// @Singleton
class RedditRepository /* @Inject */ constructor(
    private val redditDao: RedditDao,
    private val redditApiService: RedditApiService,
    private val redditAuthClient: RedditAuthClient,
    private val redditPrefs: RedditPrefs,
    private val scope: CoroutineScope
) {
    private var latestPostInDatabase: com.github.jayteealao.reddit.models.RedditPostEntity? = null
    private var orderOfLastPost: Int = 1000
    private val fetchMutex = Mutex()
    private var isFetching = false

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
            // Prevent concurrent fetches
            fetchMutex.withLock {
                if (isFetching) {
                    Timber.d("buildDatabase: Already fetching, skipping")
                    return@launch
                }
                isFetching = true
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

                        response.onSuccess {
                            Timber.d("Fetched ${data.data.children.size} Reddit posts")

                            // Prepare posts for database
                            entitiesToInsert = data.data.children
                                .filter { it.kind == "t3" } // Only save link posts, not comments
                                .map { thing ->
                                    val order = orderStart--
                                    thing.data.toEntity(order)
                                }

                            fetchedCount += entitiesToInsert?.size ?: 0

                            // Check if there are more pages
                            after = data.data.after
                            hasMore = after != null

                        }.onError {
                            Timber.e("Error fetching Reddit posts: ${message()}")

                            // Try to refresh token if unauthorized
                            if (statusCode.code == 401 && refreshToken.isNotBlank()) {
                                scope.launch {
                                    redditAuthClient.refreshAccessToken(refreshToken)
                                }
                            }
                        }

                        // Insert entities outside the callback
                        entitiesToInsert?.let { entities ->
                            redditDao.insertPosts(entities)
                        }

                    } while (hasMore && fetchedCount < 800) // Limit to 800 posts max

                    Timber.d("Finished fetching Reddit posts. Total: $fetchedCount")
                }
            } finally {
                fetchMutex.withLock {
                    isFetching = false
                }
            }
        }
    }

    /**
     * Get paged posts from database
     */
    fun getPagingPosts() = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = { redditDao.getPosts() }
    ).flow

    /**
     * Delete a post from saved
     */
    suspend fun deletePost(postId: String) {
        redditDao.deletePost(postId)
        // TODO: Also unsave from Reddit API
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
}
