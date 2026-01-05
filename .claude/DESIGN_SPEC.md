# Crumbs - Design Specification
**Version:** 2.0
**Date:** 2026-01-02
**Status:** Draft for Implementation

---

## Executive Summary

Crumbs is a **personal knowledge base** for social bookmarks, emphasizing **better search and retrieval** than native platform features. Users treat it as a **spatial archive** they browse for serendipitous rediscovery, not a temporal read-later queue. The redesign embraces the **breadcrumb metaphor** (return navigation/wayfinding) while maintaining an **expressive, content-first** aesthetic.

### Core Value Proposition
Users choose Crumbs over Twitter/Reddit's native bookmarks because:
- **Superior search**: Full-text search, visual filtering, saved searches, and smart collections
- **Cross-platform unification**: One place to find all saved content
- **Rediscovery mechanisms**: Browse mode optimized for finding forgotten gems

### Target Behavior
When a repeat user (50th visit) opens Crumbs, they **browse/discover** mode - scrolling through their collection for inspiration or rediscovery, not searching for something specific.

---

## 1. Information Architecture

### 1.1 Navigation Structure

**Primary Navigation: Source-First (Bottom Navigation Bar)**
```
┌─────────────────────────────────────┐
│ [Top App Bar: Logo + Search]       │
├─────────────────────────────────────┤
│                                     │
│         Content Feed Area           │
│                                     │
│                                     │
├─────────────────────────────────────┤
│  Twitter  │  Reddit  │  All  │  Map │
└─────────────────────────────────────┘
```

- **Bottom Nav Items:**
  - Twitter (filter to Twitter bookmarks only)
  - Reddit (filter to Reddit bookmarks only)
  - All (combined feed across sources)
  - Map (connection visualization - equal prominence)

- **Top App Bar:**
  - App name/logo (left)
  - Search icon (right) - expands to search field on tap
  - No center action, no hamburger menu

- **No FAB** - share sheet handles adding bookmarks, search is in app bar

### 1.2 View Modes

**List View (Default)**
- Medium-density feed (3-5 cards per screen nominal, but **variable based on content**)
- Cards with full-width images + substantial text (5-6 line previews)
- Flat design, outlined borders, no shadows

**Map View**
- Graph/network visualization showing connections between related bookmarks
- Accessible as equal tab in bottom nav (not hidden gesture or setting)
- Shows relationships via layout clustering, not literal connecting lines in list view

### 1.3 Sort & Filter Options

**Sort Options (User-Controlled)**
Users can toggle between:
- Newest first (default)
- Oldest first
- Most popular (by engagement metrics)
- Random (for serendipity)

**Filter Chips (Below App Bar)**
- Visual tag-based filtering
- Can combine: Source + Status + Tags + Date Range
- Filters persist across sessions

---

## 2. Content Card Design

### 2.1 Card Structure

```
┌─────────────────────────────────────┐
│ [Full-width hero image if present]  │ ← Variable height based on aspect ratio
├─────────────────────────────────────┤
│ Twitter Icon • @username • 2h ago   │ ← Metadata row (all critical fields shown)
│                                     │
│ Tweet Title or First Line          │ ← Strong contrast (bold, larger)
│                                     │
│ Preview text excerpt showing the   │
│ actual content of the tweet or     │ ← 5-6 lines substantial preview
│ reddit post to give context        │
│ without needing to tap through...  │
│                                     │
│ #tag1 #tag2 (if tagged)            │ ← Optional tags
└─────────────────────────────────────┘
```

**Card Style:**
- **Outlined with no shadows** (flat design)
- Rounded corners (12-16dp radius from design tokens)
- Thin border (1dp, subtle gray)
- White or very light background (contrast against textured app background)

**Variable Card Heights:**
- Image-heavy tweets with large photos: Taller cards
- Text-only Reddit posts: Shorter cards
- Twitter threads: First tweet + "14 more" indicator (medium height)
- Organic, content-driven rhythm

### 2.2 Image Treatment

**Full-width hero images** when media is present:
- Images span full card width at top
- Aspect ratio preserved (no cropping unless extreme)
- Progressive loading: Low-res placeholder → high-res on scroll stop
- No images in list for text-only content (not forced)

### 2.3 Critical Metadata (All Shown)

Display **all** of these in the metadata row:
- Source platform icon (Twitter bird, Reddit alien)
- Author/creator name (@username or u/username)
- When saved (relative timestamp: "2h ago", "3d ago")
- Content type indicator (thread icon, image icon, link icon)

**Additional Metadata (Optional):**
- User-applied tags (shown below preview text if present)
- Preview text excerpt (5-6 lines always shown)

**NOT Shown in List:**
- Engagement metrics (likes, retweets) - not critical for browsing
- Read/unread indicator - not needed for discovery flow

### 2.4 Twitter Thread Handling

**In List View:**
- Show first tweet with full-width image
- Preview text is first tweet's content
- Indicator: "+ 14 more tweets" badge/chip

**On Tap Indicator:**
- **Expand inline but limited height** (compromise)
- Card expands to show full thread
- If thread is very long (>10 tweets), cap at ~80vh with internal scroll
- Collapse button at bottom

---

## 3. Interaction Patterns

### 3.1 Primary Actions

**Tap Card:**
- Opens original source externally (Twitter app or browser)
- Crumbs is the index/launcher, not reading environment

**Tap-and-Hold Card:**
- If tapped briefly: Opens source immediately
- If held for 0.5+ seconds before release: Shows quick action menu

**Quick Action Menu (Context Menu):**
- Add/edit tags
- Share externally (share sheet)
- Delete/archive
- Appears as overlay/popup at touch point

### 3.2 Thread Interaction

**Tap "Show thread" indicator:**
- Expands thread inline within feed
- Shows all tweets with limited height (scrollable within card if >10 tweets)
- Does NOT navigate to new screen
- Collapse button to restore compact state

### 3.3 Scroll Behavior

**Scroll Animation:**
- **Scale/zoom from distance** effect as cards enter viewport
- Cards appear to zoom in from far away (plays into "finding" breadcrumb metaphor)
- Smooth 60fps performance prioritized - reduce effect quality if needed

**Parallax Background:**
- Subtle background texture shifts slightly while scrolling
- Adds depth without distraction
- Very light effect (20-30% movement rate)

---

## 4. Visual Design System

### 4.1 Color Palette

**Brand Colors (Preserved):**
- **Primary Accent:** `#D6FF00` (Neon yellow/lime) - SIGNATURE POP
  - **Usage restriction:** Non-text elements only (icons, highlights, badges, borders)
  - Never use for body text due to contrast issues
- **Secondary Accent:** `#000000` (Black) for text and strong contrast

**Backgrounds:**
- **Tone down current gradients** to subtle textures
- Very faint pastel gradient (10-20% opacity of current)
- Parallax shifts while scrolling
- No dynamic color theming - maintain brand identity (no Material You)

**Card Backgrounds:**
- Pure white `#FFFFFF` or very subtle gray `#F8F8F8`
- Strong contrast against app background

**Text Hierarchy:**
- Primary text: `#000000` (black)
- Secondary text: `#666666` (gray)
- Tertiary/metadata: `#999999` (light gray)

**Border & Dividers:**
- Card borders: `#E0E0E0` (light gray), 1dp stroke
- Minimal dividers (transparent when possible)

### 4.2 Typography System

**Custom Typography:**
- **Full custom typography system** for stronger visual identity
- **Voice: Geometric/Modern** (clean, tech-forward)
- Recommended fonts: Outfit, Space Grotesk, DM Sans, or similar geometric sans-serifs

**Hierarchy:**
```
App Name/Logo:        32sp, Bold, Letter-spacing +0.5sp
Section Headers:      24sp, Bold, Letter-spacing +0.3sp (h1Section)
Card Title:           18sp, Semi-Bold, Letter-spacing 0sp (emphasis)
Card Author/Meta:     14sp, Medium, Letter-spacing 0sp
Preview Text:         15sp, Regular, Line-height 22sp (readability)
Caption/Timestamps:   12sp, Regular, Letter-spacing +0.2sp
Button Text:          16sp, Medium, Letter-spacing +0.1sp
```

**Strong Title/Metadata Contrast:**
- Card titles are **bold and larger** (18sp vs 15sp body)
- Metadata row uses **medium weight** to anchor each card
- Easy to skim titles, then read previews if interested

### 4.3 Spacing Tokens

**Preserve existing spacing scale:**
```kotlin
object DudsSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val base: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}
```

**Card Internal Spacing:**
- Image top: 0dp (flush to card edge)
- Horizontal content padding: `base` (16dp)
- Vertical content padding: `md` (12dp)
- Space between elements: `sm` (8dp)

**Feed Spacing:**
- Card-to-card gap: `md` (12dp)
- Content padding (horizontal): `base` (16dp)
- Content padding (top): `sm` (8dp)
- Bottom padding for nav: 96dp

### 4.4 Border Radius

```kotlin
object DudsRadius {
    val sm: Dp = 12.dp    // Chips, small elements
    val md: Dp = 16.dp    // Cards (primary)
    val lg: Dp = 20.dp    // Modals, bottom sheets
    val full: Dp = 9999.dp // Circular elements
}
```

Cards use `md` (16dp) rounded corners.

### 4.5 Elevation & Shadows

**Flat design philosophy:**
- No drop shadows on cards
- No elevation tokens
- Depth created through borders and subtle background contrast

---

## 5. Breadcrumb Metaphor Implementation

### 5.1 Concept: "Return Navigation"

The breadcrumb metaphor emphasizes **finding your way back** to saved content:
- Not about linear trails or temporal sequences
- About **retrieval, search, and wayfinding**
- Makes finding forgotten bookmarks delightful

### 5.2 Visual Metaphor Expression

**Embrace Strongly (but tastefully):**

**In Map View:**
- Graph visualization shows relationships as "trails"
- Related bookmarks clustered spatially
- Dotted lines or subtle paths between connected items
- Zoom out to see full "map" of your knowledge

**In Iconography:**
- Breadcrumb/trail icons for saved searches/collections
- Path/waypoint imagery in empty states
- Navigation-themed illustrations in onboarding

**In Micro-interactions:**
- "Leaving breadcrumbs" animation when saving bookmark
- "Following the trail" animation when searching
- Subtle particle effects (breadcrumb fragments) on scroll

**NOT in regular UI:**
- No literal breadcrumb graphics in feed
- No cluttered trail lines between list items
- Keep list view clean and content-focused

### 5.3 Search & Retrieval Features (Core Differentiator)

**Critical Search Features:**
1. **Full-text search** across all bookmark content (not just titles)
   - Search within tweet text, Reddit post bodies, article excerpts
   - Instant results as you type
   - Highlight matching terms in results

2. **Visual/tag-based filtering**
   - Multi-select filter chips (Source + Tags + Date + Status)
   - Filters persist across sessions
   - Visual feedback for active filters

3. **Saved searches / Smart collections**
   - Create persistent filtered views
   - Examples: "AI articles from last month", "Twitter threads about design"
   - Auto-update as new bookmarks match criteria
   - Accessible from side drawer or dedicated section

**Search UI:**
- Search bar in top app bar (tap to expand)
- Full-screen search overlay with filter options
- Recent searches shown initially
- Suggestions based on tags and frequent terms

---

## 6. Navigation & Organization

### 6.1 Bottom Navigation Details

```
┌──────────┬──────────┬──────────┬──────────┐
│ Twitter  │  Reddit  │   All    │   Map    │
│  [icon]  │  [icon]  │  [icon]  │  [icon]  │
└──────────┴──────────┴──────────┴──────────┘
```

- 4 equal-width tabs
- Icons + labels for each
- Active state: Neon yellow underline or fill (non-text element)
- Material Design 3 bottom nav component (but custom colors)

### 6.2 Tag System

**Lightweight & Optional:**
- Users CAN add tags but it's not required
- Quick action menu has "Add tag" option
- Tag input is autocomplete from existing tags
- Tags shown as chips below card preview text
- Tapping tag filters to that tag
- No hierarchical categories or forced taxonomy

### 6.3 Source Architecture

**Focus on Twitter & Reddit only** (current state):
- Don't over-engineer for future sources
- Bottom nav perfectly accommodates 4 items (Twitter, Reddit, All, Map)
- If adding sources later, revisit navigation pattern

---

## 7. Edge Cases & States

### 7.1 Empty State (New Users)

**Onboarding Tutorial/Tour:**
- 3-4 screen carousel explaining:
  1. What Crumbs does (save & find social bookmarks)
  2. How to add bookmarks (share sheet from Twitter/Reddit)
  3. How search works (your personal knowledge retrieval)
  4. Breadcrumb metaphor (finding your way back)
- Illustrated with breadcrumb-themed graphics
- "Get Started" CTA at end

**After Tutorial:**
- Single placeholder card showing example bookmark
- "Add your first crumb" button
- Link to share sheet instructions

### 7.2 Deleted/Unavailable Content

**Keep card, show "unavailable" state:**
- Card remains in feed with preview text/image cached
- Overlay label: "Original tweet deleted" or "Post removed"
- Gray out the card slightly
- Tap still attempts to open (may show platform's "not found" page)
- Can still tag, search, organize these items
- Preserves memory of what you saved even if source is gone

### 7.3 Loading & Performance

**Progressive Enhancement:**
1. Show card skeleton/placeholder immediately
2. Load low-res preview image (fast)
3. Load text content and metadata
4. Load high-res image when scrolling slows/stops

**Pagination:**
- Infinite scroll with 20-30 items per page
- Load next page when 5 items from bottom

**Performance Priority:**
- Target 60fps scrolling on mid-range devices
- Reduce animation complexity if frame drops detected
- Lazy load images outside viewport

### 7.4 Thread Previews

**Collapsed State:**
```
┌─────────────────────────────────────┐
│ [Image from first tweet if present] │
│ @author • 2h ago                    │
│                                     │
│ First tweet content shown here     │
│ with the usual 5-6 lines of       │
│ preview text...                    │
│                                     │
│ [+14 more tweets] 👆               │
└─────────────────────────────────────┘
```

**Expanded State (Inline):**
```
┌─────────────────────────────────────┐
│ [First tweet image]                 │
│ @author • 2h ago                    │
│ First tweet full text...            │
├─────────────────────────────────────┤
│ Second tweet text...                │
├─────────────────────────────────────┤
│ Third tweet text...                 │
│ [... 12 more tweets]                │
│ (scrollable if >10 tweets)          │
├─────────────────────────────────────┤
│ [Collapse Thread] 👇                │
└─────────────────────────────────────┘
```

- Max height: ~80vh (entire viewport height minus nav)
- Internal scroll if thread exceeds height
- Tap "Collapse" to restore compact state

---

## 8. Platform & Technical Considerations

### 8.1 Android-Specific

**Material Design 3 (Selective Adoption):**
- Use MD3 components (bottom nav, search bar, cards)
- **No Material You dynamic theming** - maintain brand identity
- Custom color system overrides system theme

**System Integration:**
- Share sheet receiver for Twitter/Reddit URLs
- User preference: Open app to tag OR silent save with notification
- Quick Settings tile for "Add bookmark" (future)

### 8.2 Performance Targets

**Priority: Smoothness over visual quality**
- 60fps scroll on Pixel 5 or equivalent (2020 mid-range)
- Budget for 100-200 bookmarks in feed before pagination
- Image loading should not block scroll

**Optimization Strategies:**
- Coil image library with aggressive caching
- Jetpack Compose lazy column with key-based recomposition
- Bitmap pooling for preview images
- Background thread for search indexing

### 8.3 Accessibility

**Vision:**
- **Yellow accent for non-text only** (icons, dividers, highlights)
- All text uses black or gray (WCAG AA contrast)
- Strong title contrast for scanability
- Support system font scaling (up to 200%)

**Interaction:**
- Minimum touch targets: 48x48dp
- Clear focus indicators for keyboard navigation
- TalkBack optimized content descriptions
- Haptic feedback on long-press actions

---

## 9. User Flows

### 9.1 Adding a Bookmark

**Primary Flow (Share Sheet):**
1. User in Twitter app, sees interesting tweet
2. Tap share → Select Crumbs
3. **Option A (User Preference):**
   - Crumbs opens, shows quick "Add tags" screen → Save
4. **Option B (User Preference):**
   - Silent save, notification appears → Tap notification to tag

**Secondary Flow (Manual):**
- Copy tweet URL
- Open Crumbs → (removed FAB, so...)
- Use share sheet from clipboard OR paste in search bar (future)

### 9.2 Finding a Bookmark

**Scenario: "I remember saving a tweet about AI agents last month"**

1. Open Crumbs → Lands on "All" tab (last used)
2. Tap search icon in app bar → Search field expands
3. Type "ai agents" → Results filter in real-time
4. Optionally: Apply filter chip for Twitter + Last Month
5. Scan results (titles emphasized, matching terms highlighted)
6. Tap card → Opens original tweet in Twitter app

**Alternate: Browse Mode**
1. Open Crumbs → All tab
2. Scroll through feed, cards zoom in as they appear
3. Recognize bookmark by image or preview text
4. Tap to open source

### 9.3 Organizing Bookmarks

**Tagging:**
1. Browse feed, find bookmark to organize
2. Tap and hold card → Quick actions menu
3. Select "Add tags"
4. Type tag (autocomplete suggests existing)
5. Save → Tag chip appears below preview

**Creating Smart Collection:**
1. Set up filter combination (e.g., Twitter + #ai tag + Last 30 days)
2. From overflow menu → "Save as collection"
3. Name it "Recent AI Tweets"
4. Accessible from side drawer or collections view

### 9.4 Exploring Connections (Map View)

1. User has 50+ bookmarks with various tags
2. Tap "Map" tab in bottom nav
3. Graph visualization appears:
   - Bookmarks as nodes (sized by engagement or recency)
   - Related items clustered together
   - Tags create connections
4. Pinch-zoom to explore
5. Tap node → Opens detail or filters list to that cluster
6. Switch back to List tab for traditional view

---

## 10. Future Considerations (Out of Scope for v2.0)

**Explicitly NOT Designing For:**
- Additional sources beyond Twitter/Reddit
- Collaborative collections or social features
- AI summarization (mentioned but not committed)
- Desktop/web companion
- Export to other services

**Might Explore Later:**
- Offline reading mode (currently just index/launcher)
- Browser extension for direct saving
- API for third-party integrations
- Custom RSS/feed support

---

## 11. Design Deliverables Checklist

### 11.1 Visual Design
- [ ] Color palette definition (update Tokens.kt)
- [ ] Typography scale (select geometric font, define sizes)
- [ ] Card component variants (image vs text-only vs thread)
- [ ] Bottom navigation design
- [ ] Search UI mockups
- [ ] Map view concept designs
- [ ] Quick action menu design
- [ ] Empty state illustrations (breadcrumb theme)
- [ ] Onboarding tutorial screens

### 11.2 Interaction Design
- [ ] Tap vs tap-and-hold behavior specs
- [ ] Scroll animation parameters
- [ ] Thread expand/collapse animation
- [ ] Background parallax effect specs
- [ ] Search result highlighting
- [ ] Filter chip interaction states

### 11.3 Implementation Specs
- [ ] Update design system tokens (colors, spacing, radius)
- [ ] Define custom typography in Type.kt
- [ ] Card composable specification
- [ ] Bottom nav component
- [ ] Search architecture (full-text indexing)
- [ ] Map view graph library selection
- [ ] Image loading & caching strategy
- [ ] Performance budgets & metrics

### 11.4 Content & Copy
- [ ] Onboarding tutorial copy
- [ ] Empty state messaging
- [ ] Error state messages
- [ ] Deleted content labels
- [ ] Action button labels (Share, Tag, Delete)
- [ ] Navigation labels

---

## 12. Success Metrics

**Post-Launch Measurement:**
- **Search usage**: % of sessions including search (target >40%)
- **Retrieval success**: Bookmarks opened from search vs browse (target search >50%)
- **Retention**: DAU/MAU ratio (target >30% for power users)
- **Bookmark volume**: Avg bookmarks per user after 30 days (target 20+)
- **Tag adoption**: % of bookmarks with tags (target >30% despite lightweight approach)
- **Map view usage**: % of users who try map view (target >20%)

**UX Metrics:**
- Time to find specific bookmark (user testing)
- Feed scroll FPS (target >55fps on mid-range)
- Search result relevance (user satisfaction survey)

---

## 13. Open Questions / TBD

1. **Map View Implementation**: Which graph visualization library? (Compose-friendly options limited)
2. **Full-Text Search**: Client-side indexing (SQLite FTS) or server-side? Performance implications?
3. **Saved Collections**: Where do they live in IA? Drawer? Dedicated tab?
4. **Smart Sort Algorithm**: If implementing "smart sort" option, what signals? (Engagement + recency weighting?)
5. **Breadcrumb Animation Details**: Exact particle effect specs for scroll animations?
6. **Typography License**: Selected geometric font licensing for commercial app?

---

## Appendix A: Design Principles

1. **Content First**: Bookmarks are the star, UI recedes
2. **Findability Over Organization**: Search > Manual filing
3. **Serendipity Over Efficiency**: Rediscovery is a feature
4. **Expressive Minimalism**: Personality through subtle details, not clutter
5. **Performance as Design**: Smoothness is part of the aesthetic
6. **Breadcrumbs, Not Breadcrumb Trails**: Embrace metaphor without literalism

---

## Appendix B: Competitive Analysis

**vs. Twitter Native Bookmarks:**
- ❌ Weak search (only title search)
- ❌ No organization features
- ❌ No cross-platform (can't see Reddit)
- ✅ **Crumbs wins**: Full-text search, tags, unified view

**vs. Pocket/Instapaper:**
- ❌ Focused on articles, not social posts
- ❌ Reading mode changes content
- ✅ They have web clipper, offline mode
- ✅ **Crumbs wins**: Native social content, thread support, lighter weight

**vs. Notion/Obsidian:**
- ❌ Heavy note-taking focus
- ❌ High friction to save content
- ✅ Powerful organization
- ✅ **Crumbs wins**: Lightweight, share-sheet integration, designed for social

---

## Document History

- **v1.0** (Initial): Basic feature set, design tokens
- **v2.0** (2026-01-02): Complete redesign based on comprehensive UX interview
  - Defined breadcrumb metaphor implementation
  - Clarified search as core differentiator
  - Specified medium-density feed with variable heights
  - Established flat design language with custom typography
  - Added map view as equal navigation item
  - Refined interaction patterns (tap-and-hold, inline expansion)
