# Crumbs v2.0 - Implementation Plan
**Based on:** DESIGN_SPEC.md v2.0
**Target:** Q1 2026
**Status:** Ready for Development

---

## Phase Overview

**Phase 1: Foundation (Weeks 1-2)**
- Design system updates
- Navigation structure
- Basic card redesign

**Phase 2: Core Features (Weeks 3-5)**
- Search infrastructure
- Filtering system
- Performance optimizations

**Phase 3: Advanced Features (Weeks 6-7)**
- Map view
- Smart collections
- Animations & polish

**Phase 4: Edge Cases & Testing (Week 8)**
- Empty states
- Error handling
- Performance testing
- Bug fixes

---

## Technical Stack Decisions

### Core Technologies (Existing)
✅ Jetpack Compose - UI framework
✅ Kotlin 2.0.21 with KSP
✅ Hilt - Dependency injection
✅ Room - Local database
✅ Retrofit - Network calls
✅ Coil - Image loading
✅ Paging 3 - Infinite scroll

### New Dependencies Needed

**Typography:**
```gradle
// Option 1: Use Google Fonts downloadable fonts (recommended)
implementation "androidx.compose.ui:ui-text-google-fonts:1.5.4"

// Option 2: Bundle custom font
// Add font files to app/src/main/res/font/
```

**Recommended Geometric Fonts (Open Source):**
- **Outfit** (Google Fonts) - Clean, modern, excellent weights
- **Space Grotesk** (Google Fonts) - Tech-forward, distinctive
- **DM Sans** (Google Fonts) - Versatile, highly readable

**Full-Text Search:**
```gradle
// Room FTS (Full-Text Search) - already included with Room
// Just need to create FTS virtual tables
```

**Map View Graph Visualization:**
```gradle
// Option 1: Compose Graphs (lightweight, Compose-native)
implementation "io.github.ehsannarmani:compose-graphs:1.0.0"

// Option 2: Custom canvas drawing (more control, more work)
// Use Compose Canvas API

// Decision: Start with Compose Graphs, migrate to custom if needed
```

**Parallax Scrolling:**
```gradle
// Use Modifier.graphicsLayer with scroll offset
// No external dependency needed
```

---

## Phase 1: Foundation (Weeks 1-2)

### 1.1 Design System Updates

**File:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/`

#### Task 1.1.1: Update Color Tokens
**File:** `Color.kt`

```kotlin
object DudsColors {
    // Keep existing colors, add new ones:

    // Backgrounds (toned down gradients)
    val backgroundGradientStart = Color(0x33E8D5FF) // 20% opacity
    val backgroundGradientMiddle = Color(0x33D9FFD5)
    val backgroundGradientEnd = Color(0x33F5FFCC)

    // Card backgrounds
    val cardBackground = Color(0xFFFFFFFF)
    val cardBackgroundAlt = Color(0xFFF8F8F8)

    // Borders
    val cardBorder = Color(0xFFE0E0E0)

    // Keep neon yellow for non-text
    val accentYellow = Color(0xFFD6FF00) // Keep as-is

    // Text (strong hierarchy)
    val textPrimary = Color(0xFF000000)
    val textSecondary = Color(0xFF666666)
    val textTertiary = Color(0xFF999999)

    // Status colors
    val statusUnavailable = Color(0xFFCCCCCC)
}
```

**Estimated Time:** 1 hour

#### Task 1.1.2: Typography System with Custom Font
**File:** `Type.kt`

```kotlin
// Download and add font to res/font/outfit_*.ttf (or use Google Fonts)

val OutfitFontFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

object DudsTypography {
    val appName = TextStyle(
        fontFamily = OutfitFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )

    val sectionHeader = TextStyle(
        fontFamily = OutfitFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp
    )

    val cardTitle = TextStyle(
        fontFamily = OutfitFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )

    val cardMetadata = TextStyle(
        fontFamily = OutfitFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    )

    val cardPreview = TextStyle(
        fontFamily = OutfitFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    )

    val caption = TextStyle(
        fontFamily = OutfitFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp
    )

    val buttonText = TextStyle(
        fontFamily = OutfitFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )
}
```

**Steps:**
1. Download Outfit font from Google Fonts (all weights)
2. Add to `app/src/main/res/font/` directory
3. Update Typography.kt with new definitions
4. Test on different screen sizes (accessibility)

**Estimated Time:** 3 hours

#### Task 1.1.3: Update Spacing/Radius (Already Good)
**File:** `Tokens.kt`

✅ Existing spacing tokens are correct - no changes needed
✅ Existing radius tokens are correct - no changes needed

### 1.2 Bottom Navigation Redesign

**File:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/DudsBottomNav.kt`

#### Task 1.2.1: Create New Bottom Nav Component

```kotlin
@Composable
fun DudsBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = DudsColors.cardBackground,
        contentColor = DudsColors.textPrimary
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Twitter, contentDescription = "Twitter") },
            label = { Text("Twitter", style = DudsTypography.caption) },
            selected = selectedTab == BottomNavTab.Twitter,
            onClick = { onTabSelected(BottomNavTab.Twitter) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = DudsColors.accentYellow,
                indicatorColor = DudsColors.accentYellow.copy(alpha = 0.2f)
            )
        )

        NavigationBarItem(
            icon = { Icon(Icons.Default.Reddit, contentDescription = "Reddit") },
            label = { Text("Reddit", style = DudsTypography.caption) },
            selected = selectedTab == BottomNavTab.Reddit,
            onClick = { onTabSelected(BottomNavTab.Reddit) }
        )

        NavigationBarItem(
            icon = { Icon(Icons.Default.Apps, contentDescription = "All") },
            label = { Text("All", style = DudsTypography.caption) },
            selected = selectedTab == BottomNavTab.All,
            onClick = { onTabSelected(BottomNavTab.All) }
        )

        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountTree, contentDescription = "Map") },
            label = { Text("Map", style = DudsTypography.caption) },
            selected = selectedTab == BottomNavTab.Map,
            onClick = { onTabSelected(BottomNavTab.Map) }
        )
    }
}

enum class BottomNavTab {
    Twitter, Reddit, All, Map
}
```

**Steps:**
1. Replace existing bottom nav with Material 3 NavigationBar
2. Remove FAB from layout
3. Add Map tab
4. Update colors to use neon yellow accent
5. Test tap interactions

**Estimated Time:** 4 hours

#### Task 1.2.2: Update HomeScreen to Use New Nav

**File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt`

```kotlin
@Composable
fun HomeScreen(
    navController: NavController,
    // ... viewModels
) {
    var selectedTab by remember { mutableStateOf(BottomNavTab.All) }

    Scaffold(
        topBar = {
            DudsTopBar(
                title = "crumbs",
                showSearch = true, // New search icon
                onSearchClick = { /* TODO: Open search */ }
            )
        },
        bottomBar = {
            DudsBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DudsColors.backgroundGradient) // Updated gradient
                .padding(padding)
        ) {
            when (selectedTab) {
                BottomNavTab.Twitter -> TwitterFeed()
                BottomNavTab.Reddit -> RedditFeed()
                BottomNavTab.All -> CombinedFeed()
                BottomNavTab.Map -> MapView() // Phase 3
            }
        }
    }
}
```

**Estimated Time:** 3 hours

### 1.3 Card Component Redesign

**File:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/DudsCard.kt`

#### Task 1.3.1: Create New BookmarkCard Component

```kotlin
@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
                interactionSource = interactionSource,
                indication = null // Custom press state
            ),
        shape = RoundedCornerShape(DudsRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = DudsColors.cardBackground
        ),
        border = BorderStroke(1.dp, DudsColors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat
    ) {
        Column {
            // Full-width hero image (if present)
            bookmark.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .placeholder(R.drawable.image_placeholder)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )
            }

            // Content section
            Column(
                modifier = Modifier.padding(DudsSpacing.base)
            ) {
                // Metadata row (all critical fields)
                MetadataRow(
                    source = bookmark.source,
                    author = bookmark.author,
                    timestamp = bookmark.savedAt,
                    contentType = bookmark.contentType
                )

                Spacer(modifier = Modifier.height(DudsSpacing.sm))

                // Title (strong contrast)
                Text(
                    text = bookmark.title,
                    style = DudsTypography.cardTitle,
                    color = DudsColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(DudsSpacing.sm))

                // Preview text (5-6 lines)
                Text(
                    text = bookmark.previewText,
                    style = DudsTypography.cardPreview,
                    color = DudsColors.textSecondary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags (if present)
                if (bookmark.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(DudsSpacing.sm))
                    TagRow(tags = bookmark.tags)
                }

                // Thread indicator (if thread)
                if (bookmark.isThread && !bookmark.threadExpanded) {
                    Spacer(modifier = Modifier.height(DudsSpacing.sm))
                    ThreadIndicator(
                        tweetCount = bookmark.threadCount,
                        onClick = { /* Expand inline */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    source: BookmarkSource,
    author: String,
    timestamp: String,
    contentType: ContentType
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DudsSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source icon
        Icon(
            painter = painterResource(
                when (source) {
                    BookmarkSource.Twitter -> R.drawable.ic_twitter
                    BookmarkSource.Reddit -> R.drawable.ic_reddit
                }
            ),
            contentDescription = null,
            modifier = Modifier.size(DudsIconSize.small),
            tint = DudsColors.accentYellow // Non-text use of yellow
        )

        Text(
            text = "•",
            style = DudsTypography.cardMetadata,
            color = DudsColors.textTertiary
        )

        Text(
            text = author,
            style = DudsTypography.cardMetadata,
            color = DudsColors.textSecondary
        )

        Text(
            text = "•",
            style = DudsTypography.cardMetadata,
            color = DudsColors.textTertiary
        )

        Text(
            text = timestamp,
            style = DudsTypography.cardMetadata,
            color = DudsColors.textTertiary
        )

        // Content type icon
        Icon(
            painter = painterResource(
                when (contentType) {
                    ContentType.Thread -> R.drawable.ic_thread
                    ContentType.Image -> R.drawable.ic_image
                    ContentType.Link -> R.drawable.ic_link
                    ContentType.Text -> R.drawable.ic_text
                }
            ),
            contentDescription = null,
            modifier = Modifier.size(DudsIconSize.small),
            tint = DudsColors.textTertiary
        )
    }
}

@Composable
private fun ThreadIndicator(
    tweetCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DudsRadius.sm),
        color = DudsColors.accentYellow.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, DudsColors.accentYellow)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DudsSpacing.sm,
                vertical = DudsSpacing.xs
            ),
            horizontalArrangement = Arrangement.spacedBy(DudsSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_thread),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = DudsColors.textPrimary
            )
            Text(
                text = "+${tweetCount - 1} more tweets",
                style = DudsTypography.caption,
                color = DudsColors.textPrimary
            )
        }
    }
}
```

**Steps:**
1. Create new BookmarkCard component with all metadata
2. Implement full-width image with Coil
3. Add 5-6 line text preview
4. Add thread indicator chip
5. Implement tap vs tap-and-hold detection
6. Test with various content types

**Estimated Time:** 8 hours

### 1.4 Background with Parallax

**File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt`

#### Task 1.4.1: Implement Parallax Background

```kotlin
@Composable
fun ParallaxBackground(
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    val offset = remember {
        derivedStateOf {
            scrollState.firstVisibleItemScrollOffset.toFloat() * 0.3f // 30% parallax
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = -offset.value
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DudsColors.backgroundGradientStart,
                        DudsColors.backgroundGradientMiddle,
                        DudsColors.backgroundGradientEnd
                    )
                )
            )
    )
}

// Usage in feed:
@Composable
fun CombinedFeed() {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        ParallaxBackground(scrollState = listState)

        LazyColumn(state = listState) {
            // Feed items
        }
    }
}
```

**Estimated Time:** 2 hours

**Phase 1 Total: 21 hours (~2.5 work days)**

---

## Phase 2: Core Features (Weeks 3-5)

### 2.1 Full-Text Search Infrastructure

#### Task 2.1.1: Create FTS Database Tables

**File:** `app/src/main/java/com/github/jayteealao/crumbs/db/BookmarkFts.kt`

```kotlin
@Entity(tableName = "bookmark_fts")
@Fts4(contentEntity = Tweet::class) // Or create unified Bookmark entity
data class BookmarkFts(
    @ColumnInfo(name = "rowid") @PrimaryKey val rowid: Int,
    val title: String,
    val content: String,
    val author: String,
    val tags: String // Space-separated for FTS
)

@Dao
interface BookmarkFtsDao {
    @Query("SELECT * FROM bookmark_fts WHERE bookmark_fts MATCH :query")
    fun searchBookmarks(query: String): Flow<List<BookmarkFts>>

    @Query("""
        SELECT bookmark_fts.rowid FROM bookmark_fts
        WHERE bookmark_fts MATCH :query
        ORDER BY rank
    """)
    fun searchBookmarkIds(query: String): Flow<List<Int>>
}
```

**Steps:**
1. Create FTS4 virtual table for bookmark content
2. Implement sync between main tables and FTS table (triggers or manual)
3. Add search DAO methods
4. Test search performance with 100+ bookmarks

**Estimated Time:** 6 hours

#### Task 2.1.2: Search UI Component

**File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/SearchScreen.kt`

```kotlin
@Composable
fun SearchScreen(
    onDismiss: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DudsColors.cardBackground)
    ) {
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.updateQuery(it) },
            onBack = onDismiss,
            placeholder = "Search your crumbs...",
            modifier = Modifier
                .fillMaxWidth()
                .padding(DudsSpacing.base)
        )

        // Filter chips
        FilterChipRow(
            activeFilters = viewModel.activeFilters.collectAsState().value,
            onFilterToggle = { viewModel.toggleFilter(it) }
        )

        // Results or recent searches
        if (searchQuery.isEmpty()) {
            RecentSearches(
                searches = recentSearches,
                onSearchClick = { viewModel.updateQuery(it) }
            )
        } else {
            SearchResults(
                results = searchResults,
                query = searchQuery,
                onBookmarkClick = { /* Open source */ }
            )
        }
    }
}

@Composable
fun SearchResults(
    results: List<Bookmark>,
    query: String,
    onBookmarkClick: (Bookmark) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(DudsSpacing.base),
        verticalArrangement = Arrangement.spacedBy(DudsSpacing.md)
    ) {
        items(results, key = { it.id }) { bookmark ->
            BookmarkCard(
                bookmark = bookmark.copy(
                    // Highlight matching terms in preview
                    previewText = highlightMatches(bookmark.previewText, query)
                ),
                onTap = { onBookmarkClick(bookmark) },
                onLongPress = { /* Quick actions */ }
            )
        }
    }
}
```

**Steps:**
1. Create search screen composable
2. Implement search bar with clear button
3. Add filter chips integration
4. Implement text highlighting for matches
5. Add recent searches storage (SharedPreferences)
6. Test real-time search performance

**Estimated Time:** 10 hours

### 2.2 Filtering System

#### Task 2.2.1: Filter Chips Component

**File:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/FilterChips.kt`

```kotlin
data class Filter(
    val id: String,
    val label: String,
    val type: FilterType,
    val value: Any
)

enum class FilterType {
    Source, Tag, DateRange, Status
}

@Composable
fun FilterChipRow(
    activeFilters: List<Filter>,
    onFilterToggle: (Filter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = DudsSpacing.base),
        horizontalArrangement = Arrangement.spacedBy(DudsSpacing.sm)
    ) {
        items(availableFilters) { filter ->
            FilterChip(
                selected = filter in activeFilters,
                onClick = { onFilterToggle(filter) },
                label = { Text(filter.label) },
                leadingIcon = if (filter in activeFilters) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DudsColors.accentYellow.copy(alpha = 0.2f),
                    selectedLabelColor = DudsColors.textPrimary
                )
            )
        }
    }
}
```

**Steps:**
1. Create Filter data model
2. Implement filter chip row with Material 3 FilterChip
3. Add filter state management in ViewModel
4. Persist active filters across sessions
5. Test multi-select filtering logic

**Estimated Time:** 6 hours

#### Task 2.2.2: Filter Logic in Repository

**File:** `app/src/main/java/com/github/jayteealao/crumbs/data/BookmarkRepository.kt`

```kotlin
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val bookmarkFtsDao: BookmarkFtsDao
) {
    fun getFilteredBookmarks(
        sources: List<BookmarkSource> = emptyList(),
        tags: List<String> = emptyList(),
        dateRange: DateRange? = null,
        searchQuery: String? = null,
        sortOrder: SortOrder = SortOrder.NewestFirst
    ): Flow<PagingData<Bookmark>> {
        return Pager(
            config = PagingConfig(pageSize = 20, prefetchDistance = 5),
            pagingSourceFactory = {
                // Build dynamic query based on filters
                when {
                    searchQuery != null -> bookmarkFtsDao.searchPaged(searchQuery)
                    else -> bookmarkDao.getFilteredPaged(
                        sources, tags, dateRange, sortOrder
                    )
                }
            }
        ).flow
    }
}
```

**Estimated Time:** 8 hours

### 2.3 Quick Actions Menu

#### Task 2.3.1: Long-Press Context Menu

**File:** `app/src/main/java/com/github/jayteealao/crumbs/components/QuickActionsMenu.kt`

```kotlin
@Composable
fun QuickActionsMenu(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onAddTag: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(DudsColors.cardBackground)
    ) {
        DropdownMenuItem(
            text = { Text("Add tag", style = DudsTypography.bodyPrimary) },
            onClick = {
                onAddTag()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Default.Label, contentDescription = null)
            }
        )

        DropdownMenuItem(
            text = { Text("Share", style = DudsTypography.bodyPrimary) },
            onClick = {
                onShare()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Default.Share, contentDescription = null)
            }
        )

        Divider()

        DropdownMenuItem(
            text = { Text("Delete", style = DudsTypography.bodyPrimary) },
            onClick = {
                onDelete()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = DudsColors.destructive
                )
            }
        )
    }
}

// Usage in BookmarkCard:
var showQuickActions by remember { mutableStateOf(false) }

BookmarkCard(
    bookmark = bookmark,
    onTap = { /* Open source */ },
    onLongPress = { showQuickActions = true }
)

if (showQuickActions) {
    QuickActionsMenu(
        bookmark = bookmark,
        onDismiss = { showQuickActions = false },
        onAddTag = { /* Show tag dialog */ },
        onDelete = { /* Delete bookmark */ },
        onShare = { /* Share externally */ }
    )
}
```

**Estimated Time:** 5 hours

### 2.4 Performance Optimizations

#### Task 2.4.1: Image Loading Strategy

**File:** `app/src/main/java/com/github/jayteealao/crumbs/util/ImageUtils.kt`

```kotlin
// Configure Coil for optimal performance
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.25) // 25% of app memory
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache"))
            .maxSizeBytes(50 * 1024 * 1024) // 50MB
            .build()
    }
    .respectCacheHeaders(false)
    .build()

// In BookmarkCard AsyncImage:
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(imageUrl)
        .crossfade(300)
        .placeholder(ColorDrawable(0xFFF0F0F0)) // Gray placeholder
        .error(R.drawable.error_placeholder)
        .size(Size.ORIGINAL) // Load full size
        .precision(Precision.AUTOMATIC)
        .build(),
    contentDescription = null,
    modifier = Modifier.fillMaxWidth(),
    contentScale = ContentScale.Crop
)
```

**Steps:**
1. Configure Coil with aggressive caching
2. Implement progressive loading (placeholder → full image)
3. Add error state handling
4. Test scroll performance with 50+ images

**Estimated Time:** 4 hours

#### Task 2.4.2: LazyColumn Optimization

**File:** Update feed composables

```kotlin
LazyColumn(
    state = listState,
    contentPadding = PaddingValues(
        start = DudsSpacing.base,
        end = DudsSpacing.base,
        top = DudsSpacing.sm,
        bottom = 96.dp
    ),
    verticalArrangement = Arrangement.spacedBy(DudsSpacing.md)
) {
    items(
        items = bookmarks,
        key = { it.id }, // Important for recomposition optimization
        contentType = { it.contentType } // Helps with recycling
    ) { bookmark ->
        BookmarkCard(
            bookmark = bookmark,
            onTap = { openSource(bookmark) },
            onLongPress = { showQuickActions(bookmark) },
            modifier = Modifier
                .fillMaxWidth()
                .animateItemPlacement() // Smooth reordering
        )
    }
}
```

**Estimated Time:** 3 hours

**Phase 2 Total: 42 hours (~5 work days)**

---

## Phase 3: Advanced Features (Weeks 6-7)

### 3.1 Map View

#### Task 3.1.1: Graph Visualization

**File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/MapViewScreen.kt`

**Add dependency:**
```gradle
implementation "io.github.ehsannarmani:compose-graphs:1.0.0"
```

```kotlin
@Composable
fun MapViewScreen(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit
) {
    // Convert bookmarks to graph nodes
    val graphData = remember(bookmarks) {
        buildGraphData(bookmarks)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DudsColors.cardBackground)
    ) {
        ForceDirectedGraph(
            nodes = graphData.nodes,
            edges = graphData.edges,
            modifier = Modifier.fillMaxSize(),
            nodeContent = { node ->
                MapNodeCard(
                    bookmark = node.bookmark,
                    onClick = { onBookmarkClick(node.bookmark) }
                )
            }
        )
    }
}

data class GraphData(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

data class GraphNode(
    val id: String,
    val bookmark: Bookmark,
    val x: Float = 0f,
    val y: Float = 0f
)

fun buildGraphData(bookmarks: List<Bookmark>): GraphData {
    val nodes = bookmarks.map { GraphNode(it.id, it) }
    val edges = mutableListOf<GraphEdge>()

    // Create edges based on shared tags
    bookmarks.forEachIndexed { i, bookmark1 ->
        bookmarks.drop(i + 1).forEach { bookmark2 ->
            val sharedTags = bookmark1.tags.intersect(bookmark2.tags.toSet())
            if (sharedTags.isNotEmpty()) {
                edges.add(
                    GraphEdge(
                        from = bookmark1.id,
                        to = bookmark2.id,
                        weight = sharedTags.size.toFloat()
                    )
                )
            }
        }
    }

    return GraphData(nodes, edges)
}

@Composable
fun MapNodeCard(
    bookmark: Bookmark,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DudsColors.cardBackground,
        border = BorderStroke(2.dp, DudsColors.accentYellow),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(
                    when (bookmark.source) {
                        BookmarkSource.Twitter -> R.drawable.ic_twitter
                        BookmarkSource.Reddit -> R.drawable.ic_reddit
                    }
                ),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = DudsColors.textPrimary
            )
        }
    }
}
```

**Steps:**
1. Integrate graph visualization library
2. Build graph data structure from bookmarks (tags create connections)
3. Implement force-directed layout algorithm
4. Add zoom/pan gestures
5. Implement node tap to filter list view
6. Test with 50+ bookmarks

**Estimated Time:** 16 hours

**Note:** If library doesn't work well, fallback to custom Canvas implementation (add 8 more hours).

### 3.2 Smart Collections (Saved Searches)

#### Task 3.2.1: Collections Data Model

**File:** `app/src/main/java/com/github/jayteealao/crumbs/db/Collection.kt`

```kotlin
@Entity(tableName = "collections")
data class Collection(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filters: FilterSet, // JSON of active filters
    val createdAt: Long = System.currentTimeMillis()
)

@TypeConverter
data class FilterSet(
    val sources: List<BookmarkSource> = emptyList(),
    val tags: List<String> = emptyList(),
    val dateRange: DateRange? = null,
    val searchQuery: String? = null
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): FilterSet =
            Json.decodeFromString(json)
    }
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun getAllCollections(): Flow<List<Collection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCollection(collection: Collection)

    @Delete
    suspend fun deleteCollection(collection: Collection)
}
```

**Steps:**
1. Create Collection entity and DAO
2. Implement filter serialization
3. Add "Save as collection" to filter UI
4. Create collections list screen
5. Implement dynamic collection evaluation

**Estimated Time:** 8 hours

#### Task 3.2.2: Collections UI

**File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/CollectionsScreen.kt`

```kotlin
@Composable
fun CollectionsScreen(
    collections: List<Collection>,
    onCollectionClick: (Collection) -> Unit,
    onCreateNew: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Collections") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DudsColors.cardBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = DudsColors.accentYellow
            ) {
                Icon(Icons.Default.Add, contentDescription = "New collection")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(DudsSpacing.base),
            verticalArrangement = Arrangement.spacedBy(DudsSpacing.md)
        ) {
            items(collections) { collection ->
                CollectionCard(
                    collection = collection,
                    onClick = { onCollectionClick(collection) }
                )
            }
        }
    }
}
```

**Estimated Time:** 6 hours

### 3.3 Scroll Animations

#### Task 3.3.1: Scale-Zoom Card Entry Animation

**File:** Update BookmarkCard

```kotlin
@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ).value

                scaleX = scale
                scaleY = scale
                alpha = scale
            }
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        // ... rest of card
    ) {
        // Card content
    }
}
```

**Steps:**
1. Add scale animation to card entry
2. Tune spring physics for "zoom from distance" feel
3. Ensure animation doesn't impact scroll performance
4. Test on low-end device

**Estimated Time:** 4 hours

**Phase 3 Total: 34 hours (~4 work days)**

---

## Phase 4: Edge Cases & Polish (Week 8)

### 4.1 Empty States

#### Task 4.1.1: Onboarding Tutorial

**File:** `app/src/main/java/com/github/jayteealao/crumbs/screens/OnboardingScreen.kt`

```kotlin
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPage(
                page = OnboardingPages.values()[page]
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DudsSpacing.base),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(onClick = { /* Previous page */ }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(80.dp))
            }

            Button(
                onClick = {
                    if (pagerState.currentPage == 3) {
                        onComplete()
                    } else {
                        // Next page
                    }
                }
            ) {
                Text(if (pagerState.currentPage == 3) "Get Started" else "Next")
            }
        }
    }
}

enum class OnboardingPages(
    val title: String,
    val description: String,
    val illustration: Int
) {
    Welcome(
        "Leave breadcrumbs",
        "Save tweets and Reddit posts you want to find again",
        R.drawable.onboarding_1
    ),
    Search(
        "Find your way back",
        "Search across all your saved content with powerful filters",
        R.drawable.onboarding_2
    ),
    Organize(
        "Create your knowledge base",
        "Tag and organize bookmarks into smart collections",
        R.drawable.onboarding_3
    ),
    Map(
        "Discover connections",
        "Visualize relationships between your saved ideas",
        R.drawable.onboarding_4
    )
}
```

**Steps:**
1. Design 4 onboarding illustrations (breadcrumb theme)
2. Implement pager with smooth transitions
3. Add "Skip" option
4. Store completion state in preferences
5. Test on first launch

**Estimated Time:** 8 hours (including illustrations)

#### Task 4.1.2: Empty Feed State

**File:** Create EmptyState composable

```kotlin
@Composable
fun EmptyFeedState(
    source: BookmarkSource?,
    onAddBookmark: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DudsSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_empty_breadcrumbs),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = DudsColors.textTertiary
        )

        Spacer(modifier = Modifier.height(DudsSpacing.lg))

        Text(
            text = when (source) {
                BookmarkSource.Twitter -> "No Twitter bookmarks yet"
                BookmarkSource.Reddit -> "No Reddit bookmarks yet"
                null -> "No crumbs yet"
            },
            style = DudsTypography.h2CardTitle,
            color = DudsColors.textPrimary
        )

        Spacer(modifier = Modifier.height(DudsSpacing.sm))

        Text(
            text = "Share posts from Twitter or Reddit\nto start your collection",
            style = DudsTypography.bodySecondary,
            color = DudsColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(DudsSpacing.xl))

        Button(
            onClick = onAddBookmark,
            colors = ButtonDefaults.buttonColors(
                containerColor = DudsColors.accentYellow,
                contentColor = DudsColors.textPrimary
            )
        ) {
            Text("Learn how to add bookmarks")
        }
    }
}
```

**Estimated Time:** 3 hours

### 4.2 Deleted Content Handling

#### Task 4.2.1: Unavailable State UI

**File:** Update BookmarkCard

```kotlin
@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    // ... other params
) {
    Card(
        // ... card setup
    ) {
        Box {
            // Regular card content
            Column {
                // ... existing content
            }

            // Overlay if deleted
            if (bookmark.isDeleted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            DudsColors.statusUnavailable.copy(alpha = 0.7f)
                        )
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(DudsSpacing.base),
                        shape = RoundedCornerShape(DudsRadius.sm),
                        color = DudsColors.cardBackground
                    ) {
                        Row(
                            modifier = Modifier.padding(DudsSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(DudsSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = DudsColors.textSecondary
                            )
                            Text(
                                text = when (bookmark.source) {
                                    BookmarkSource.Twitter -> "Tweet deleted"
                                    BookmarkSource.Reddit -> "Post removed"
                                },
                                style = DudsTypography.caption,
                                color = DudsColors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
```

**Steps:**
1. Add `isDeleted` flag to Bookmark model
2. Implement background check for deleted content (daily job)
3. Add overlay UI for deleted items
4. Keep preview text/image cached
5. Test with actually deleted tweets

**Estimated Time:** 6 hours

### 4.3 Thread Inline Expansion

#### Task 4.3.1: Expandable Thread Component

**File:** `app/src/main/java/com/github/jayteealao/crumbs/components/ExpandableThread.kt`

```kotlin
@Composable
fun ExpandableThread(
    thread: TwitterThread,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Column {
        // First tweet (always visible)
        TweetContent(tweet = thread.tweets.first())

        // Expand/collapse indicator
        ThreadIndicator(
            tweetCount = thread.tweets.size,
            isExpanded = isExpanded,
            onClick = onToggleExpand
        )

        // Expanded thread (with limited height)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 600.dp) // ~80vh equivalent
            ) {
                LazyColumn {
                    items(thread.tweets.drop(1)) { tweet ->
                        TweetContent(tweet = tweet)
                        Divider(
                            color = DudsColors.border,
                            modifier = Modifier.padding(vertical = DudsSpacing.sm)
                        )
                    }
                }

                // Collapse button
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Collapse thread")
                    Icon(Icons.Default.ExpandLess, contentDescription = null)
                }
            }
        }
    }
}
```

**Estimated Time:** 6 hours

### 4.4 Performance Testing & Optimization

#### Task 4.4.1: Performance Benchmarking

**Test Scenarios:**
1. Scroll through 100 bookmarks with images
2. Search across 200 bookmarks
3. Apply multiple filters
4. Expand/collapse threads
5. Switch between tabs
6. Open map view with 50+ nodes

**Performance Targets:**
- Scroll FPS: >55fps on Pixel 5
- Search latency: <200ms
- Image load time: <500ms (cached)
- Tab switch time: <100ms
- Memory usage: <150MB with 100 bookmarks

**File:** Create performance test suite

```kotlin
@RunWith(AndroidJUnit4::class)
class PerformanceTest {
    @Test
    fun testScrollPerformance() {
        // Use Macrobenchmark library
        composeTestRule.setContent {
            CombinedFeed(bookmarks = generateMockBookmarks(100))
        }

        composeTestRule.onNodeWithTag("bookmarkFeed")
            .performScrollToIndex(50)

        // Assert frame drops < 10
    }

    @Test
    fun testSearchPerformance() {
        val startTime = System.currentTimeMillis()
        viewModel.search("test query")
        val results = viewModel.searchResults.first()
        val duration = System.currentTimeMillis() - startTime

        assertTrue(duration < 200) // < 200ms
    }
}
```

**Estimated Time:** 8 hours

#### Task 4.4.2: Optimization Round

**Based on test results, optimize:**
1. Image loading (reduce size, better caching)
2. LazyColumn key strategy
3. Reduce recompositions (remember, derivedStateOf)
4. Database query optimization
5. Background thread usage

**Estimated Time:** 8 hours

**Phase 4 Total: 39 hours (~5 work days)**

---

## Migration Strategy

### From v1.0 to v2.0

**Database Migration:**

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns
        database.execSQL("ALTER TABLE bookmarks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE bookmarks ADD COLUMN tags TEXT DEFAULT ''")

        // Create FTS table
        database.execSQL("""
            CREATE VIRTUAL TABLE bookmark_fts USING fts4(
                content='bookmarks',
                title,
                content,
                author,
                tags
            )
        """)

        // Create collections table
        database.execSQL("""
            CREATE TABLE collections (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                filters TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)
    }
}
```

**User Settings Migration:**

```kotlin
// Preserve user preferences
val oldSelectedTab = prefs.getInt("selected_tab", 0)
val newSelectedTab = when (oldSelectedTab) {
    0, 1 -> BottomNavTab.All // "for you" and "twitter" → All
    2 -> BottomNavTab.Reddit
    else -> BottomNavTab.All
}
```

**Gradual Rollout:**
1. Ship v2.0 as beta track
2. Monitor crash rates and performance
3. Collect user feedback
4. Fix critical bugs
5. Roll out to 10% → 50% → 100%

---

## Testing Checklist

### Unit Tests
- [ ] Search query parsing
- [ ] Filter combination logic
- [ ] Graph data generation
- [ ] Date formatting
- [ ] Tag validation

### Integration Tests
- [ ] Database queries (all scenarios)
- [ ] Repository layer (filters + search)
- [ ] Image loading pipeline
- [ ] Navigation flows

### UI Tests
- [ ] Card tap vs long-press detection
- [ ] Thread expand/collapse
- [ ] Search real-time filtering
- [ ] Bottom nav switching
- [ ] Quick actions menu

### Manual Testing
- [ ] Onboarding flow (new user)
- [ ] Add bookmark via share sheet
- [ ] Search with various queries
- [ ] Create smart collection
- [ ] Explore map view
- [ ] Scroll performance (100+ items)
- [ ] Low memory scenario
- [ ] Airplane mode (cached content)
- [ ] Deleted tweet handling

### Accessibility Testing
- [ ] TalkBack navigation
- [ ] Font scaling (up to 200%)
- [ ] Color contrast (WCAG AA)
- [ ] Touch target sizes
- [ ] Keyboard navigation

---

## Deployment Plan

### Pre-Release
- [ ] Complete all Phase 4 tasks
- [ ] Pass all tests (unit + integration + UI)
- [ ] Performance benchmarks meet targets
- [ ] Accessibility audit passed
- [ ] Internal dogfooding (1 week)

### Beta Release
- [ ] Create release branch `release/v2.0`
- [ ] Upload to Play Console beta track
- [ ] Invite 50-100 beta testers
- [ ] Monitor crash reports daily
- [ ] Collect feedback via in-app survey
- [ ] Fix P0/P1 bugs

### Production Release
- [ ] Increment version to 2.0.0
- [ ] Tag release in git
- [ ] Upload to Play Console production
- [ ] Staged rollout: 10% → 25% → 50% → 100%
- [ ] Monitor metrics (retention, engagement, crashes)
- [ ] Prepare hotfix process

---

## Success Metrics (30 Days Post-Launch)

**Engagement:**
- Search usage: >40% of sessions
- Map view discovery: >20% of users try it
- Smart collections created: >10% of users

**Performance:**
- Crash-free rate: >99%
- ANR rate: <0.5%
- Average scroll FPS: >55

**Retention:**
- D1 retention: >60%
- D7 retention: >40%
- D30 retention: >25%

**Satisfaction:**
- Play Store rating: >4.3 stars
- Net Promoter Score: >40

---

## Resources Needed

### Design Assets
- [ ] 4 onboarding illustrations (breadcrumb theme)
- [ ] Empty state illustrations
- [ ] Source icons (Twitter, Reddit) - high-res
- [ ] Content type icons (thread, image, link, text)
- [ ] Error state illustrations

### Font Files
- [ ] Outfit Regular, Medium, SemiBold, Bold (OTF/TTF)
- [ ] Font license verification

### API Keys / Credentials
- [ ] Twitter API v2 credentials (existing)
- [ ] Reddit API credentials (existing)
- [ ] Firebase (if adding analytics)

### Development Tools
- [ ] Android Studio Hedgehog or newer
- [ ] Kotlin 2.0.21
- [ ] Gradle 8.5
- [ ] Physical test devices (mid-range + high-end)

---

## Timeline Summary

| Phase | Duration | Tasks | Focus |
|-------|----------|-------|-------|
| Phase 1: Foundation | 2 weeks | Design system, nav, cards | Visual redesign |
| Phase 2: Core Features | 3 weeks | Search, filters, actions | Functionality |
| Phase 3: Advanced | 2 weeks | Map view, collections | Differentiation |
| Phase 4: Polish | 1 week | Edge cases, testing | Quality |
| **Total** | **8 weeks** | **~136 hours** | **Full v2.0** |

**Team Size:** 1 developer full-time = 8 weeks
**Team Size:** 2 developers = 4-5 weeks with parallel work

---

## Risk Mitigation

### High Risk Areas

**1. Map View Performance**
- **Risk:** Graph visualization with 100+ nodes may be laggy
- **Mitigation:** Start with library, fallback to custom Canvas, limit nodes to 50 initially
- **Contingency:** Ship map view as experimental beta feature

**2. Full-Text Search Latency**
- **Risk:** Search may be slow with large databases
- **Mitigation:** Use Room FTS4, index optimization, background threading
- **Contingency:** Add loading indicator, implement debounced search

**3. Image Loading Memory**
- **Risk:** Loading many high-res images could cause OOM
- **Mitigation:** Aggressive Coil caching, image downsampling, pagination
- **Contingency:** Reduce image quality or remove from some views

**4. Migration Bugs**
- **Risk:** v1 → v2 migration could lose user data
- **Mitigation:** Thorough migration testing, database backups, staged rollout
- **Contingency:** Quick rollback plan, restore from backup

---

## Post-Launch Roadmap

**v2.1 (Optional Enhancements):**
- Batch tagging mode
- Export collections (JSON, CSV)
- Dark theme improvements
- Widget for quick add

**v2.2 (Exploration):**
- AI-powered smart summaries
- Browser extension
- Offline reading mode
- Custom RSS feeds

---

## Contact & Support

**Development Questions:** Review DESIGN_SPEC.md
**Technical Issues:** File GitHub issue or check implementation notes
**Design Clarifications:** Refer to interview answers in spec appendix

---

## Appendix: Quick Reference

### Key Files by Phase

**Phase 1:**
- `core/designsystem/theme/Color.kt`
- `core/designsystem/theme/Type.kt`
- `core/designsystem/components/DudsBottomNav.kt`
- `core/designsystem/components/DudsCard.kt`
- `app/screens/HomeScreen.kt`

**Phase 2:**
- `app/db/BookmarkFts.kt`
- `app/screens/SearchScreen.kt`
- `core/designsystem/components/FilterChips.kt`
- `app/components/QuickActionsMenu.kt`
- `app/util/ImageUtils.kt`

**Phase 3:**
- `app/screens/MapViewScreen.kt`
- `app/db/Collection.kt`
- `app/screens/CollectionsScreen.kt`

**Phase 4:**
- `app/screens/OnboardingScreen.kt`
- `app/components/EmptyState.kt`
- `app/components/ExpandableThread.kt`

### Color Reference (Quick Copy)

```kotlin
val accentYellow = Color(0xFFD6FF00)
val textPrimary = Color(0xFF000000)
val textSecondary = Color(0xFF666666)
val textTertiary = Color(0xFF999999)
val cardBackground = Color(0xFFFFFFFF)
val cardBorder = Color(0xFFE0E0E0)
```

### Spacing Reference

```
xs = 4.dp
sm = 8.dp
md = 12.dp
base = 16.dp
lg = 24.dp
xl = 32.dp
xxl = 48.dp
```

---

**END OF IMPLEMENTATION PLAN**
**Ready for handoff to development team.**
**Estimated Total Effort: 136 hours (8 weeks solo / 4-5 weeks with 2 devs)**
