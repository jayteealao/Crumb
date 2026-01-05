# Crumbs Design System

Custom design system module for the Crumbs Android app with Roborazzi screenshot testing.

## Components

### Navigation & Layout
- **CrumbsBottomNav** - Bottom navigation with source-first organization
- **CrumbsTopBar** - Scroll-aware top app bar with inline expanding search and glassmorphic blur
- **SearchSuggestions** - Search suggestions dropdown showing recent searches and suggestions

### Core Components
- **CrumbsButton** - Primary action button with cut-corner styling
- **CrumbsCard** - Content container with bottom-end cut corner
- **CrumbsBookmarkCard** - Unified polymorphic card for Twitter and Reddit bookmarks

### Content Display
- **EmptyState** - Centered empty state with optional action button
- **LoadingCard** - Skeleton placeholder with shimmer animation
- **CrumbsVideoPlayer** - Embedded video playback with ExoPlayer
- **ThreadIndicator** - Shows "+ N more" for Twitter threads

### Filtering & Sorting
- **CrumbsFilterChip** - Tag-based filter chips (outlined or filled styles)
- **CrumbsTagChip** - Display-only tag chips for bookmark cards
- **CrumbsSortMenu** - Dropdown menu for sort options (Newest, Oldest, Popular, Random)

### Interactions
- **QuickActionMenu** - Context menu for long-press on bookmark cards

## Design Tokens

### Colors
**Modern Minimal Theme** - Neutral grays with electric cyan accent
- Light theme: Very light gray background (#F8F9FA), white surfaces (#FFFFFF), dark gray text (#1F2937), electric cyan accent (#00D9FF)
- Dark theme: Very dark gray background (#111827), dark gray surfaces (#1F2937), light gray text (#F9FAFB), electric cyan accent (#00D9FF)

### Shapes
Distinctive cut-corner system:
- Buttons: top-end cut (forward action)
- Cards: bottom-end cut (grounded content)
- Navigation: bottom-start cut (anchored)

### Typography
Standard scale from display (57sp) to caption (11sp)

### Spacing
Scale: 4dp, 8dp, 12dp, 16dp, 24dp, 32dp

## Usage

### Basic Components

```kotlin
CrumbsTheme {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    CrumbsButton(
        onClick = { /* action */ },
        text = "Click Me"
    )
}
```

### CrumbsTopBar with Scroll Behavior

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen() {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CrumbsTopBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSearchActive = isSearchActive,
                onSearchActiveChange = { isSearchActive = it },
                scrollBehavior = scrollBehavior,
                logoResId = R.drawable.ic_crumbs_logo  // Optional
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues)
        ) {
            // Your content
        }
    }
}
```

### Search Suggestions

```kotlin
SearchSuggestions(
    recentSearches = listOf("kotlin", "compose", "android"),
    suggestions = listOf("jetpack", "material design"),
    onSuggestionClick = { query ->
        // Handle suggestion click
    }
)
```

### CrumbsBookmarkCard

```kotlin
CrumbsBookmarkCard(
    bookmark = Bookmark(
        id = "123",
        source = BookmarkSource.Twitter,
        author = "@designpatterns",
        title = "Understanding SOLID Principles",
        previewText = "Let me explain the five SOLID principles...",
        contentType = ContentType.Text,
        savedAt = System.currentTimeMillis(),
        tags = listOf("programming", "design"),
        sourceUrl = "https://twitter.com/..."
    ),
    onCardClick = { url -> /* Open browser */ },
    onLongPress = { bookmark -> /* Show quick actions */ }
)
```

### Empty & Loading States

```kotlin
// Empty state with action
EmptyState(
    title = "No bookmarks yet",
    message = "Start saving content to see it here",
    icon = Icons.Default.BookmarkBorder,
    actionText = "Add first bookmark",
    onActionClick = { /* Navigate to add */ }
)

// Loading skeleton
LoadingCard(hasImage = true)
```

### Video Player

```kotlin
CrumbsVideoPlayer(
    videoUrl = "https://example.com/video.mp4",
    thumbnailUrl = "https://example.com/thumb.jpg",
    aspectRatio = 16f / 9f
)
```

### Filter Chips & Sorting

```kotlin
// Filter chips
var selectedSource by remember { mutableStateOf<BookmarkSource?>(null) }

Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CrumbsFilterChip(
        label = "Twitter",
        selected = selectedSource == BookmarkSource.Twitter,
        onClick = { selectedSource = BookmarkSource.Twitter },
        style = FilterChipStyle.Outlined
    )
    CrumbsFilterChip(
        label = "Reddit",
        selected = selectedSource == BookmarkSource.Reddit,
        onClick = { selectedSource = BookmarkSource.Reddit },
        style = FilterChipStyle.Outlined
    )
}

// Sort menu
var showSortMenu by remember { mutableStateOf(false) }
var currentSort by remember { mutableStateOf(SortOption.Newest) }

IconButton(onClick = { showSortMenu = true }) {
    Icon(Icons.Default.Sort, "Sort")
}

CrumbsSortMenu(
    expanded = showSortMenu,
    currentSort = currentSort,
    onDismiss = { showSortMenu = false },
    onSortSelected = { currentSort = it }
)
```

### Quick Actions

```kotlin
var showQuickActions by remember { mutableStateOf(false) }

CrumbsBookmarkCard(
    bookmark = bookmark,
    onCardClick = { /* Open */ },
    onLongPress = { showQuickActions = true }
)

QuickActionMenu(
    visible = showQuickActions,
    onDismiss = { showQuickActions = false },
    actions = listOf(
        QuickAction("Add Tags", Icons.Default.LocalOffer) { /* Tag dialog */ },
        QuickAction("Share", Icons.Default.Share) { /* Share */ },
        QuickAction("Delete", Icons.Default.Delete) { /* Delete */ }
    )
)
```

## Testing

Screenshot tests with Roborazzi:

```bash
# Record new screenshots
./gradlew :core:designsystem:recordRoborazziDebug

# Verify against existing
./gradlew :core:designsystem:verifyRoborazziDebug

# Compare and generate report
./gradlew :core:designsystem:compareRoborazziDebug
```

## Color Palette (Modern Minimal)

- `#F8F9FA` - Very light gray (light background)
- `#FFFFFF` - Pure white (light surfaces)
- `#111827` - Very dark gray (dark background)
- `#1F2937` - Dark gray (dark surfaces / light primary)
- `#F9FAFB` - Light gray (dark text primary)
- `#6B7280` - Medium gray (light text secondary)
- `#9CA3AF` - Light gray (dark text secondary)
- `#00D9FF` - Electric cyan (accent & nav indicator)
- `#EF4444` - Red (error)

## Platform Requirements

### Glassmorphic Blur Effect
The `CrumbsTopBar` component uses a glassmorphic blur effect that is only available on Android 12+ (API 31+).

- **Android 12+**: True blur effect with `Modifier.blur(16.dp)`
- **Android < 12**: Falls back to semi-transparent surface (no blur)

This provides a graceful degradation while maintaining visual consistency across all Android versions.
