---
review: testing
slug: brutalist-redesign
date: 2026-05-18
scope: slug-wide
target: git diff main...HEAD
verdict: APPROVE_WITH_COMMENTS
---

# Testing Review — brutalist-redesign

**Reviewed:** slug-wide / `git diff main...HEAD`
**Date:** 2026-05-18

---

## 0) Scope & Context

**What was reviewed:** Full branch diff against `main` — 380 files, +16 821 / −9 318 lines.

**Test frameworks present:**
- Robolectric + Compose test rules (Roborazzi screenshot + behaviour tests) — host-side
- AndroidTest instrumentation — `MigrationTest`, `DebugDataInjectorTest`
- Maestro E2E YAML flows: `happy_path`, `long_press`, `filter_overlay`, `sync_error`, `_probe`

**New behaviours introduced on this branch:**
1. `DeletedBookmarkRepository` — soft-delete / undo / event emission
2. `SyncErrorBus` — 401 event emission → banner flow in `HomeRoute`
3. Banner CTA → OAuth intent dispatch (`HomeRoute.onBannerCta`)
4. Type-filter chips in `HomeScreen` / `AllBookmarksScreen` / `CrumbsFilterBar`
5. `CrumbsLongPressPopup` — four-action popup on long-press
6. DB Migration 4 → 5 (`deleted_bookmarks` table)
7. `DebugDataInjector` — deterministic seed / corrupt-token helper
8. Sort-menu slot in `HomeScreen` (`onSortClick` deferred to follow-up)

---

## 1) Executive Summary

**Verdict: APPROVE_WITH_COMMENTS**

The branch ships meaningful coverage for the two highest-risk behaviours (soft-delete tombstone round-trip has a proper Robolectric unit test; migration has an instrumentation test). Screenshot tests are thorough in breadth (~130 golden images). However, every screen and component test outside `DeletedBookmarkRepository`, `LoginScreen`, and `AllBookmarksScreen` is **screenshot-only** — they assert pixels, not behaviour. Several critical interaction paths have zero behaviour assertions. There is one structurally flaky test idiom (busy-wait `yield()` loop in `DeletedBookmarkRepositoryTest`), and `DebugDataInjectorTest` contains a vacuous assertion that always passes.

**Findings summary:**
- HIGH: 2
- MED: 4
- LOW: 2
- NIT: 2

---

## 2) Coverage Analysis

| Behaviour | Source File:Line | Tested? | Test Level | Notes |
|---|---|---|---|---|
| Soft-delete tombstone write + read | `DeletedBookmarkRepository.kt:24–36` | ✅ Yes | Unit (Robolectric) | Happy + undo covered |
| Soft-delete event emission | `DeletedBookmarkRepository.kt:26` | ⚠️ Partial | Unit | Race-prone yield loop |
| `SyncErrorBus.emit()` | `SyncErrorBus.kt:21` | ❌ No | — | No unit test |
| Banner state mutation in `HomeRoute` | `HomeRoute.kt:78–98` | ❌ No | — | Only screenshot static; no event→state assertion |
| Banner CTA OAuth intent dispatch | `HomeRoute.kt:141–146` | ❌ No | — | Maestro covers UI tap but not intent content |
| Type-filter chip toggle callback | `HomeScreen` / `HomeRoute` | ❌ No | — | Maestro taps chip + asserts visible; no filter-applied assertion |
| DB migration 4→5 | `AppDatabase` | ✅ Yes | Instrumentation | Table existence only; schema not validated |
| `DebugDataInjector.run(wipe=true)` | `DebugDataInjector.kt:31` | ⚠️ Partial | Instrumentation | One assertion is vacuous (see TEST-06) |
| Long-press popup action callbacks | `CrumbsLongPressPopup` | ❌ No | — | Screenshot only; onDelete/onTag not asserted |
| LoginScreen OAuth CTA callbacks | `LoginScreen` | ✅ Yes | Compose | `performClick` + `assertTrue` — good |
| Empty-state CTA callback | `AllBookmarksScreen` | ✅ Yes | Compose | Good behaviour assertion |
| Sort menu (deferred) | `HomeRoute.kt:139` | N/A | — | Intentionally deferred |

---

## 3) Findings Table

| ID | Severity | Confidence | Category | File:Line | Issue |
|---|---|---|---|---|---|
| TEST-01 | HIGH | High | Coverage Gap | `SyncErrorBus.kt:21` | No test for SyncErrorBus.emit() or the HomeRoute banner-state flow |
| TEST-02 | HIGH | High | Coverage Gap | `HomeRoute.kt:141–146` | Banner CTA OAuth intent dispatch has no assertion |
| TEST-03 | MED | High | Flakiness | `DeletedBookmarkRepositoryTest.kt:85–91` | Busy-wait `yield()` loop before `tryEmit` is non-deterministic |
| TEST-04 | MED | High | Weak Assertion | `MigrationTest.kt:33–36` | Migration only checks row count = 0; column schema not verified |
| TEST-05 | MED | High | Coverage Gap | `LongPressPopupTest.kt` | Popup action callbacks (onDelete, onTag, onOpen, onShare) not exercised |
| TEST-06 | MED | High | Vacuous Assertion | `DebugDataInjectorTest.kt:53` | Second `assertEquals` compares a hardcoded list size literal to itself |
| TEST-07 | LOW | High | Coverage Gap | `FilterBarTest.kt` | `onChipToggled` callback never fired in any Compose test |
| TEST-08 | LOW | Med | Coverage Gap | `Repository.kt:95,186` | `isDeleted` guard in sync paths not covered by any test |
| TEST-09 | NIT | High | Renders-only | `TwitterBookmarksScreenTest`, `RedditBookmarksScreenTest` | Only `loggedOut` state tested; no logged-in-with-data screenshot or callback test |
| TEST-10 | NIT | High | Naming | `HomeScreenTest` | Tests named `homeScreen_withSyncErrorBanner_*` render static `BannerState`; name implies dynamic but test is static |

---

## 4) Findings (Detailed)

### TEST-01: No Tests for SyncErrorBus or Banner State Flow [HIGH]

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt:21`  
and `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:77–98`

**Untested behaviour:**
```kotlin
// SyncErrorBus.kt:21
fun emit(event: SyncErrorEvent): Boolean = _events.tryEmit(event)
```
```kotlin
// HomeRoute.kt:78-98 — LaunchedEffect collects events and mutates banner state
is SyncErrorEvent.TwitterAuth401 -> {
    twitterBanner = BannerState(
        source = BookmarkSource.TWITTER,
        kicker = "ERR · RECONNECT TWITTER",
        ...
    )
}
```

`SyncErrorBus` has zero tests. The `HomeRoute` banner-state mutation driven by `SyncErrorBus.events` is also untested — `HomeScreenTest.homeScreen_withSyncErrorBanner_*` renders a pre-built `BannerState` directly, bypassing the event→state pipeline entirely.

**Scenarios not covered:**
1. `SyncErrorBus.emit(TwitterAuth401())` → `events` SharedFlow delivers to collector
2. `SyncErrorBus.emit()` when buffer is full (DROP_OLDEST) — returns `true` but event silently discarded
3. `HomeRoute` LaunchedEffect: TwitterAuth401 emitted → `twitterBanner` is non-null → `HomeScreen.bannerState` is non-null

**Why it matters:** This is the primary user-visible error recovery path. If the bus drops events silently or the LaunchedEffect fails to collect, the banner never appears and users are stuck without feedback.

**Suggested test (unit — `SyncErrorBus`):**
```kotlin
@Test
fun emit_delivers_event_to_collector() = runTest {
    val bus = SyncErrorBus()
    val received = mutableListOf<SyncErrorEvent>()
    val job = launch { bus.events.collect { received.add(it) } }
    bus.emit(SyncErrorEvent.TwitterAuth401())
    advanceUntilIdle()
    assertEquals(1, received.size)
    assertTrue(received[0] is SyncErrorEvent.TwitterAuth401)
    job.cancel()
}
```

**Suggested test (Compose — `HomeRoute` banner flow):** Use a fake `SyncErrorBus` injected via Hilt test module, emit an event, then assert `onNodeWithTag("banner").assertIsDisplayed()`.

---

### TEST-02: Banner CTA OAuth Intent Not Asserted [HIGH]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:141–146`

**Untested code:**
```kotlin
onBannerCta = {
    when (activeBanner?.source) {
        BookmarkSource.TWITTER -> context.startActivity(loginViewModel.authIntent())
        BookmarkSource.REDDIT  -> context.startActivity(redditViewModel.authIntent())
        else -> Unit
    }
},
```

The Maestro `sync_error.yaml` taps `banner-cta` but only takes a screenshot — it does not assert an OAuth intent was actually fired. There is no Compose or unit test that calls `onBannerCta` with a known `activeBanner` and verifies an `Intent` was delivered.

**Why it matters:** The CTA is the only escape from a broken sync state. If `authIntent()` returns null or `startActivity` throws, the user is silently stuck.

**Suggested test:**
```kotlin
@Test
fun bannerCta_twitter_firesAuthIntent() {
    var fired = false
    composeTestRule.setContent {
        CrumbsTheme {
            HomeScreen(
                uiState = HomeUiState(bannerState = BannerState(source = BookmarkSource.TWITTER, ...)),
                onBannerCta = { fired = true },
                ...
            )
        }
    }
    composeTestRule.onNodeWithTag("banner-cta").performClick()
    assertTrue(fired)
}
```

---

### TEST-03: Flaky Busy-Wait in Soft-Delete Event Test [MED]

**Location:** `app/src/test/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepositoryTest.kt:85–91`

**Problematic code:**
```kotlin
val deferredEvent = scope.async { repo.events.first() }
// Give the collector a chance to subscribe before we emit.
yield()
// Small busy-wait fallback in case the dispatcher hasn't scheduled the
// collector yet (sharedFlow.first() must be active when tryEmit runs).
repeat(20) { yield() }

repo.softDelete("reddit-abc", BookmarkSource.REDDIT)
```

`repeat(20) { yield() }` is a non-deterministic timing mechanism. On a loaded CI worker, 20 `yield()` calls may still leave the collector unsubscribed when `tryEmit` fires. `MutableSharedFlow(replay=0)` with `tryEmit` means a miss is silent — the deferred will then hang indefinitely (no timeout), causing test suite hangs in CI.

**Fix:** Use `kotlinx-coroutines-test`'s structured `runTest` + `turbine` (or `TestScope.backgroundScope`) to guarantee subscription before emission:
```kotlin
@Test
fun softDelete_emitsUndoableDeleteEvent() = runTest {
    val events = repo.events.test()  // Turbine
    repo.softDelete("reddit-abc", BookmarkSource.REDDIT)
    val event = events.awaitItem()
    assertTrue(event is SnackbarEvent.UndoableDelete)
    events.cancel()
}
```
Alternatively, switch the flow to `replay = 1` if the semantics allow late subscribers.

---

### TEST-04: Migration Test Only Checks Row Count, Not Schema [MED]

**Location:** `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt:33–36`

**Current assertion:**
```kotlin
db.query("SELECT count(*) FROM deleted_bookmarks").use { cursor ->
    assertTrue("deleted_bookmarks table missing after migration", cursor.moveToFirst())
    assertEquals(0, cursor.getInt(0))
}
```

This only confirms the table exists and is empty. It does not verify that the columns (`id TEXT`, `source TEXT`, `deleted_at INTEGER`) are present and correctly typed, nor that the NOT NULL constraints are enforced.

**Suggested addition:**
```kotlin
// Verify column schema
db.query("PRAGMA table_info(deleted_bookmarks)").use { cursor ->
    val names = mutableListOf<String>()
    while (cursor.moveToNext()) names.add(cursor.getString(cursor.getColumnIndex("name")))
    assertTrue("id column missing", "id" in names)
    assertTrue("source column missing", "source" in names)
    assertTrue("deleted_at column missing", "deleted_at" in names)
}
// Verify NOT NULL constraint fires
try {
    db.execSQL("INSERT INTO deleted_bookmarks (id, source, deleted_at) VALUES (NULL, 'twitter', 0)")
    fail("Expected constraint violation for NULL id")
} catch (e: SQLiteConstraintException) { /* expected */ }
```

---

### TEST-05: LongPressPopup Action Callbacks Not Exercised [MED]

**Location:** `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/LongPressPopupTest.kt`

`LongPressPopupTest` contains two screenshot tests only. The `CrumbsLongPressPopup` component accepts `actions: List<PopupAction>` where each action has an `onClick: () -> Unit`. None of these callbacks are clicked and asserted in tests.

**Gap:** No Compose test verifies that tapping DELETE/TAG/OPEN/SHARE fires the correct lambda. The Maestro `long_press.yaml` covers DELETE and UNDO at E2E level but not TAG, OPEN, or SHARE.

**Suggested test:**
```kotlin
@Test
fun popup_deleteAction_invokesCallback() {
    var deleteFired = false
    composeTestRule.setContent {
        CrumbsTheme {
            CrumbsLongPressPopup(
                visible = true,
                onDismiss = {},
                actions = listOf(PopupAction("DELETE", icon = ..., onClick = { deleteFired = true })),
                ...
            )
        }
    }
    // Popup renders in Compose tree (not a separate Window in this test mode)
    composeTestRule.onNodeWithText("DELETE").performClick()
    assertTrue(deleteFired)
}
```

---

### TEST-06: Vacuous Assertion in DebugDataInjectorTest [MED]

**Location:** `app/src/androidTest/java/com/github/jayteealao/crumbs/debug/DebugDataInjectorTest.kt:53`

**The assertion:**
```kotlin
assertEquals(listOf("debug-tweet-1", "debug-tweet-2", "debug-tweet-3", "debug-tweet-4").size, 4)
```

This compares `4` (the size of a hardcoded list literal) to `4`. It never touches the database and always passes regardless of what `DebugDataInjector.run()` actually inserted. This is a copy-paste error — the intent was likely:
```kotlin
assertEquals(4, db.tweetDao().getAllTweets().size)
```

**Why it matters:** The test provides false confidence. If the seed logic inserts 0 tweets this assertion still passes.

---

### TEST-07: FilterBar onChipToggled Callback Never Exercised [LOW]

**Location:** `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/FilterBarTest.kt`

All three `FilterBarTest` tests pass `onChipToggled = {}` and only capture screenshots. No test clicks a chip and asserts the callback fires. The same gap exists in `HomeScreenTest` which passes `onChipToggled = {}`.

**Suggested test:**
```kotlin
@Test
fun filterBar_chip_click_invokesCallback() {
    var toggledId: String? = null
    composeTestRule.setContent {
        CrumbsTheme {
            CrumbsFilterBar(
                chips = sampleChips,
                selectedChipIds = emptySet(),
                onChipToggled = { toggledId = it },
                ...
            )
        }
    }
    composeTestRule.onNodeWithTag("filter-bar-chip-text").performClick()
    assertEquals("text", toggledId)
}
```

---

### TEST-08: Repository.kt Sync-Path `isDeleted` Guard Untested [LOW]

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:95, 186`

```kotlin
// Line 95 (Firestore sync):
if (!deletedBookmarkRepository.isDeleted(orderedEntities.tweetEntity.id)) {
    saveTweetEntities(orderedEntities, uploadToFirestore = false)
}
// Line 186 (Twitter API sync):
if (!deletedBookmarkRepository.isDeleted(it.tweetEntity.id)) {
    saveTweetEntities(...)
}
```

Both sync paths gate on `isDeleted()` but there is no test that seeds a tombstone and then exercises the sync to confirm the guard actually filters the deleted bookmark out. `DeletedBookmarkRepositoryTest` proves the data-layer contract in isolation, but the integration between repository and sync is not tested.

---

### TEST-09 [NIT]: Feature Screen Tests Only Cover Logged-Out State

**Location:** `feature/twitter/src/test/…/TwitterBookmarksScreenTest.kt` and `feature/reddit/src/test/…/RedditBookmarksScreenTest.kt`

Both files contain only `loggedOut_light` / `loggedOut_dark` screenshots. The `loggedIn` + paged data state is not covered even by a screenshot. Consider adding at least a logged-in empty-list screenshot.

---

### TEST-10 [NIT]: Banner Screenshot Tests Named as if Dynamic

**Location:** `app/src/test/…/HomeScreenTest.kt:97–166`

Test names `homeScreen_withSyncErrorBanner_light/dark` suggest the test exercises the dynamic event→banner flow, but in reality the `BannerState` is passed directly as a constructor argument. The name is not wrong, but it can mislead a reader into thinking the SyncErrorBus integration is covered. Consider a name suffix like `_staticRender` or a comment clarifying this is a render-only test.

---

## 5) Coverage Gaps Summary

### Critical (HIGH)
1. **TEST-01**: `SyncErrorBus` and `HomeRoute` banner-state flow — no behaviour test whatsoever
2. **TEST-02**: Banner CTA → OAuth intent dispatch — only E2E screenshot, no callback assertion

### Important (MED)
3. **TEST-03**: Flaky yield-loop in event emission test — potential CI hang
4. **TEST-04**: Migration assertion incomplete — schema/constraints unverified
5. **TEST-05**: LongPressPopup action callbacks never clicked in any test
6. **TEST-06**: Vacuous `assertEquals(list.size, 4)` in DebugDataInjectorTest

### Minor (LOW/NIT)
7. **TEST-07**: FilterBar `onChipToggled` never exercised
8. **TEST-08**: Repository sync guard (isDeleted) has no integration coverage

---

## 6) Maestro Flow Assessment

| Flow | Selector Strategy | Resilience | Notes |
|---|---|---|---|
| `happy_path.yaml` | `id:`-based (testTag) + text for popup items | Good | Popup text selectors (`"DELETE"`) necessary due to Compose `Popup{}` separate-window limitation |
| `long_press.yaml` | `id:`-based + text | Good | `index: 0` on `bookmark-card` is brittle if seeded list order changes |
| `filter_overlay.yaml` | `id:`-based chips | Good | Tags/Collection overlay deferred — hedged with `runFlow: when: visible` guard |
| `sync_error.yaml` | `id:`-based + swipe | Good | Swipe relies on `twitter-bookmarks-feed` id existing — correctly gated |
| `_probe.yaml` | `id:`-based only | Excellent | Minimal, correct |

**Brittle item:** `longPressOn: id: "bookmark-card" index: 0` in `happy_path.yaml:62` and `long_press.yaml:31`. If the seed order changes, index 0 may be a different item or the list could render differently. Low risk given the seed is deterministic, but note for future seed changes.

**Missing assertion in `sync_error.yaml`:** The flow taps `banner-cta` but has no subsequent assertion (no `assertVisible` or `extendedWaitUntil` checking that an OAuth browser/chooser opened). The flow silently passes even if the intent was never dispatched.

---

## 7) Positive Observations

- `DeletedBookmarkRepositoryTest` is structurally solid: in-memory Room, `@Before`/`@After` lifecycle, clear Given/When/Then naming, meaningful `assertTrue`/`assertFalse` with message strings.
- `LoginScreenTest` and `AllBookmarksScreenTest` demonstrate the correct pattern — screenshot + `performClick` + `assertTrue` for CTA callbacks.
- Screenshot golden count (~130+) is substantial and will catch visual regressions efficiently.
- Maestro flows are overwhelmingly `id:`-based (kebab-case testTags), avoiding brittle text-based selectors except where architecturally necessary (Compose Popup separate window).
- `MigrationTestHelper` usage is correct: creates v4 schema first, then runs migration, then validates.

---

## 8) Recommendations

### Must Fix (HIGH)
1. **TEST-01**: Add unit test for `SyncErrorBus.emit()` and a Compose test asserting that emitting `TwitterAuth401` causes `banner` node to appear in the UI.
2. **TEST-02**: Add `performClick` on `banner-cta` + `assertTrue(fired)` in a Compose test (mirrors the existing `loginScreen_connectTwitter_invokesCallback` pattern).

### Should Fix (MED)
3. **TEST-03**: Replace `repeat(20) { yield() }` with Turbine or `runTest`+`launch`+`advanceUntilIdle`.
4. **TEST-04**: Add PRAGMA column verification and a NOT NULL constraint check to `MigrationTest`.
5. **TEST-05**: Add one `performClick` + callback assertion test to `LongPressPopupTest`.
6. **TEST-06**: Fix the vacuous assertion — query the DAO and assert on actual inserted count.

### Consider (LOW/NIT)
7. **TEST-07**: Add a chip-click callback test to `FilterBarTest`.
8. **TEST-08**: Add an integration test for the `Repository` sync guard (can be done with Robolectric + fake `DeletedBookmarkRepository`).
9. **TEST-09**: Add a logged-in screenshot for `TwitterBookmarksScreen` and `RedditBookmarksScreen`.
10. Add a post-CTA assertion to `maestro/sync_error.yaml` (e.g., `assertVisible: "OAuth"` or intent monitoring).

---

*Review date: 2026-05-18*
