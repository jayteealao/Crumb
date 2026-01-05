# Crumbs v2.0 - Phase 2 Roadmap
**Date:** 2026-01-02
**Status:** In Progress (2/7 tasks complete)

---

## ✅ Completed Tasks

### Phase 2.1: Map Twitter Data to Bookmark Model (COMPLETE)
**File:** `app/src/main/java/com/github/jayteealao/crumbs/mappers/BookmarkMapper.kt`

**Changes Made:**
- ✅ Created `toBookmarkData()` extension function for TweetData
- ✅ Maps Twitter API data to unified Bookmark model
- ✅ Handles thread detection (replies and referenced tweets)
- ✅ Determines content type (Text, Image, Link, Thread)
- ✅ Formats ISO 8601 timestamps to relative time ("2h ago")
- ✅ Extracts preview text (200 chars max)
- ✅ Gets first image from media attachments

**Result:** Twitter data successfully mapped to BookmarkCard format

---

### Phase 2.2: Replace Mock Data with Real Twitter Bookmarks (COMPLETE)
**File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt`

**Changes Made:**
- ✅ Integrated BookmarksViewModel with Paging3
- ✅ Replaced mock data with real Twitter bookmarks from database
- ✅ Added login state detection
- ✅ Added OAuth callback handling
- ✅ Tab-based filtering (Twitter/All tabs show real data)
- ✅ Login prompts for unauthenticated users

**Result:** App now displays real Twitter bookmarks with parallax background and new UI

---

## 🚧 In Progress

### Phase 2.3: Implement Reddit API with OAuth (IN PROGRESS)
**Target Directory:** `feature/reddit/`

**Requirements:**
- Implement Reddit OAuth2 PKCE flow (similar to Twitter)
- Create RedditApiService interface
- Create RedditRepository with Paging support
- Create RedditAuthClient for token management
- Add Reddit data models (Post, Comment, Subreddit)
- Store Reddit bookmarks in Room database
- Create RedditViewModel

**Implementation Plan:**

#### Step 1: Reddit OAuth Setup
```kotlin
// feature/reddit/src/main/java/com/github/jayteealao/reddit/services/RedditAuthClient.kt
interface RedditAuthClient {
    fun getAuthIntent(): Intent
    suspend fun getAccessToken(code: String): String?
    suspend fun refreshAccessToken(refreshToken: String): String?
}
```

**OAuth Flow:**
1. Authorization URL: `https://www.reddit.com/api/v1/authorize`
2. Token URL: `https://www.reddit.com/api/v1/access_token`
3. Scopes needed: `read`, `save`, `history`
4. Use PKCE for security

#### Step 2: Reddit API Service
```kotlin
// feature/reddit/src/main/java/com/github/jayteealao/reddit/services/RedditApiService.kt
interface RedditApiService {
    @GET("/api/v1/me")
    suspend fun getUser(
        @Header("Authorization") authorization: String
    ): ApiResponse<RedditUserResponse>

    @GET("/user/{username}/saved")
    suspend fun getSavedPosts(
        @Header("Authorization") authorization: String,
        @Path("username") username: String,
        @Query("limit") limit: Int = 100,
        @Query("after") after: String? = null
    ): ApiResponse<RedditListingResponse>
}
```

#### Step 3: Data Models
```kotlin
data class RedditPost(
    val id: String,
    val title: String,
    val selftext: String,  // Post body
    val author: String,
    val subreddit: String,
    val created_utc: Long,
    val url: String,
    val thumbnail: String?,
    val num_comments: Int,
    val score: Int,
    val permalink: String
)
```

#### Step 4: Room Database Integration
```kotlin
@Entity(tableName = "reddit_posts")
data class RedditPostEntity(
    @PrimaryKey val id: String,
    val title: String,
    val selftext: String,
    val author: String,
    val subreddit: String,
    val created_utc: Long,
    val url: String,
    val thumbnail: String?,
    val order: Int = 0
)
```

#### Step 5: Mapper
```kotlin
// app/src/main/java/com/github/jayteealao/crumbs/mappers/BookmarkMapper.kt
fun RedditPost.toBookmarkData(): BookmarkData {
    return BookmarkData(
        source = "Reddit",
        author = "u/$author",
        title = title,
        previewText = selftext.take(200),
        imageUrl = if (thumbnail?.startsWith("http") == true) thumbnail else null,
        contentType = determineContentType(),
        timestamp = formatRelativeTime(created_utc * 1000),
        tags = listOf(subreddit),
        isThread = num_comments > 0,
        threadCount = num_comments,
        isDeleted = false
    )
}
```

#### Step 6: HomeScreen Integration
```kotlin
// In HomeScreen.kt
val redditBookmarks = redditViewModel.pagingFlowData().collectAsLazyPagingItems()

when (selectedTab) {
    BottomNavTab.Reddit -> {
        // Display Reddit bookmarks
    }
    BottomNavTab.All -> {
        // Merge Twitter and Reddit bookmarks
        // Sort by savedAt timestamp
    }
}
```

**Estimated Time:** 12-16 hours

---

### Phase 2.4: Add Room FTS for Full-Text Search (NOT STARTED)
**Target Files:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditDao.kt`

**Requirements:**
- Add FTS4/FTS5 virtual tables for tweets and reddit posts
- Create search queries with ranking
- Support multi-column search (title, text, author)
- Highlight matching text in results

**Implementation:**
```kotlin
@Entity(tableName = "tweet_fts")
@Fts4(contentEntity = TweetEntity::class)
data class TweetFts(
    val text: String,
    @ColumnInfo(name = "rowid") val rowid: Int
)

@Dao
interface TweetDao {
    @Query("""
        SELECT tweetEntity.* FROM tweetEntity
        JOIN tweet_fts ON tweetEntity.rowid = tweet_fts.rowid
        WHERE tweet_fts MATCH :query
        ORDER BY rank
    """)
    fun searchTweets(query: String): PagingSource<Int, TweetEntity>
}
```

**Estimated Time:** 6-8 hours

---

### Phase 2.5: Create Search UI with Filters (NOT STARTED)
**Target File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/SearchScreen.kt`

**Requirements:**
- SearchBar at top with real-time search
- Multi-select filter chips (Source, Content Type, Tags)
- Search history
- Clear all filters button
- Results with highlighted matching text

**UI Components:**
```kotlin
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSources by remember { mutableStateOf(setOf<BookmarkSource>()) }
    var selectedTypes by remember { mutableStateOf(setOf<ContentType>()) }

    Column {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onBack = onNavigateBack
        )

        FilterChipRow(
            sources = selectedSources,
            contentTypes = selectedTypes,
            onSourcesChange = { selectedSources = it },
            onTypesChange = { selectedTypes = it }
        )

        SearchResults(
            query = searchQuery,
            filters = SearchFilters(selectedSources, selectedTypes)
        )
    }
}
```

**Estimated Time:** 8-10 hours

---

### Phase 2.6: Implement Quick Actions Menu (NOT STARTED)
**Target File:** `app/src/main/java/com/github/jayteealao/crumbs/components/QuickActionsBottomSheet.kt`

**Requirements:**
- Bottom sheet with 6 actions:
  1. Open in browser
  2. Share
  3. Delete
  4. Edit tags
  5. Add to collection (Phase 3)
  6. Mark as read/unread

**Implementation:**
```kotlin
@Composable
fun QuickActionsBottomSheet(
    bookmark: BookmarkData,
    onDismiss: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    onToggleRead: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            QuickActionItem(
                icon = Icons.Default.OpenInBrowser,
                label = "Open in browser",
                onClick = onOpenInBrowser
            )
            QuickActionItem(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = onShare
            )
            // ... more actions
        }
    }
}
```

**HomeScreen Integration:**
```kotlin
var selectedBookmark by remember { mutableStateOf<BookmarkData?>(null) }

BookmarkCard(
    bookmark = bookmark,
    onLongPress = { selectedBookmark = bookmark }
)

selectedBookmark?.let { bookmark ->
    QuickActionsBottomSheet(
        bookmark = bookmark,
        onDismiss = { selectedBookmark = null },
        onOpenInBrowser = {
            // Open bookmark.sourceUrl in Chrome Custom Tabs
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(context, Uri.parse(bookmark.sourceUrl))
        },
        onShare = {
            // Share using Android share sheet
            val shareIntent = Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, bookmark.sourceUrl)
                type = "text/plain"
            }, null)
            context.startActivity(shareIntent)
        }
    )
}
```

**Estimated Time:** 6-8 hours

---

### Phase 2.7: Test Phase 2 Integration (NOT STARTED)
**Testing Checklist:**

**Functionality:**
- [ ] Twitter bookmarks display correctly with real data
- [ ] Reddit bookmarks display correctly (after 2.3)
- [ ] Tab switching works (Twitter/Reddit/All/Map)
- [ ] Login flow works for both platforms
- [ ] Parallax background scrolls smoothly
- [ ] Search returns accurate results
- [ ] Filters apply correctly
- [ ] Quick actions menu opens and works
- [ ] Share/Delete/Edit actions work
- [ ] App handles offline state gracefully

**Performance:**
- [ ] Scroll performance is smooth (60fps)
- [ ] Image loading doesn't block UI
- [ ] Search is responsive (<200ms)
- [ ] Paging loads smoothly without jank
- [ ] Memory usage is reasonable (<100MB)

**Edge Cases:**
- [ ] Empty states (no bookmarks)
- [ ] Error states (network failure)
- [ ] Deleted content handling
- [ ] Very long text handling
- [ ] Thread with 50+ tweets
- [ ] Logout and re-login flow

**Estimated Time:** 4-6 hours

---

## 📊 Phase 2 Summary

**Total Tasks:** 7
**Completed:** 2 (29%)
**In Progress:** 1 (Reddit API)
**Remaining:** 4

**Estimated Time Remaining:** ~42-48 hours

**Key Achievements So Far:**
- ✅ Real Twitter data integration working
- ✅ Clean mapper architecture for multi-platform support
- ✅ Paging3 integration complete
- ✅ Login state management working

**Next Immediate Steps:**
1. Complete Reddit OAuth implementation
2. Create Reddit data models and repository
3. Integrate Reddit bookmarks into HomeScreen
4. Add FTS search to database
5. Build search UI with filters
6. Implement quick actions menu

---

## 📦 Dependencies Needed for Phase 2.3

Add to `feature/reddit/build.gradle`:
```gradle
dependencies {
    // Already have Retrofit from Twitter module
    implementation(libs.bundles.retrofit)

    // Already have Room and Paging
    implementation(libs.bundles.room)
    implementation(libs.paging.compose)

    // Chrome Custom Tabs for opening links
    implementation "androidx.browser:browser:1.7.0"
}
```

---

## 🎯 Phase 2 Success Criteria

Phase 2 will be considered complete when:
- [x] Real Twitter bookmarks display in app
- [ ] Real Reddit bookmarks display in app
- [ ] Full-text search works across all bookmarks
- [ ] Filter chips work correctly
- [ ] Quick actions menu is functional
- [ ] All tests pass
- [ ] App runs smoothly on test device

---

**END OF PHASE 2 ROADMAP**
**Last Updated:** 2026-01-02
**Status:** 29% complete, on track for full Phase 2 completion
