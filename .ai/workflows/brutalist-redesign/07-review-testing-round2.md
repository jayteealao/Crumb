---
review: testing
slug: brutalist-redesign
round: 2
date: 2026-05-18
scope: slug-wide
target: git diff main...HEAD
verdict: APPROVE_WITH_COMMENTS
related:
  round1: 07-review-testing.md
  fix-status: 07-review.md
---

# Testing Review — brutalist-redesign (Round 2)

**Reviewed:** slug-wide / `git diff main...HEAD` (410 files, +27 691 / −9 455)
**Date:** 2026-05-18
**Focus:** validate Round-1 claimed-fix commits, audit coverage of new code added during the fix sequence.

---

## 0) Scope & Context

Round 1 surfaced 4 unfixed testing findings (one HIGH composite + three MED). The fix sequence added a substantial amount of new production code (single-flight refresh, paged Firestore cursor, shared Coil ImageLoader, MIGRATION_6_7 / MIGRATION_7_8, idempotent Firestore tweet upload, debug-seed tombstone preservation, batched tag loads, etc.). This round validates the new tests AND audits whether the freshly-added production code is itself covered.

**Round-1 status summary (per 07-review.md "Fix Status"):**
- H20 (SyncErrorBus + banner CTA) — claimed fixed in `7dcf586`
- TEST-04 (MigrationTest constraints) — claimed fixed in `6c367a7`
- TEST-05 (LongPressPopup callbacks) — claimed fixed in `6c367a7`
- TEST-06 (DebugDataInjectorTest vacuous assertion) — claimed fixed in `6c367a7`
- TEST-03 (Turbine migration) — explicitly **deferred**

---

## 1) Executive Summary

**Verdict: APPROVE_WITH_COMMENTS**

All four Round-1 claimed-fix commits land genuine, correctly-asserting tests. The H20, TEST-05, and TEST-06 patches are well-targeted. However, **TEST-03's deferral rationale is contested**: the yield-loop pattern is not "functional and intentional" — it is structurally racy, and that exact race pattern was copied verbatim into the new `SyncErrorBusTest` (one of the H20 fix tests). The race rarely manifests because `MutableSharedFlow(replay=1)` masks the timing window, but the test is right for the wrong reason. The TEST-04 migrate6To7 test is partially tautological: the FK-check half exercises an empty DB and so cannot detect any real FK violation.

Separately, **the fix sequence introduced ~200 LOC of new production code that has zero direct unit-test coverage** — `refreshTokenSingleFlight` (both repos), `FirestoreRepository.getAllTweetIds` paged cursor (with bounds `MAX_BOOKMARK_READ = 10_000` / `MAX_PAGE_HOPS = 50`), idempotent Firestore tweet upload (`SetOptions.merge()` + deterministic doc id), `CrumbApplication.newImageLoader`, MIGRATION_7_8, and the debug-seed tombstone preservation path. None of these are integration- or unit-tested.

**Findings:**
- HIGH: 1
- MED: 4
- LOW: 3
- NIT: 2

---

## 2) Validation of Round-1 Claimed Fixes

### H20 (SyncErrorBus + banner CTA) — `7dcf586`

**Verdict: ACCEPTED with one caveat (see R2-TEST-01).**

Files inspected:
- `app/src/test/java/com/github/jayteealao/crumbs/data/SyncErrorBusTest.kt` (3 tests, 88 lines)
- `app/src/test/java/com/github/jayteealao/crumbs/screens/HomeScreenTest.kt` (new test `homeScreen_bannerCta_click_invokesCallback` at line 135–177)

**What lands:**
1. `SyncErrorBusTest.emit_delivers_event_to_active_collector` — exercises the active-subscriber path.
2. `SyncErrorBusTest.emit_before_subscriber_is_replayed_to_late_collector` — proves the B2 cold-start replay fix: emit happens **before** `events.first()`, then a late subscriber receives the replayed event. This is structurally correct because `replay = 1` deterministically caches the latest emission for late subscribers — no race.
3. `SyncErrorBusTest.multiple_emits_keep_latest_for_late_collector` — proves DROP_OLDEST replay semantics (latest wins for late subscriber).
4. `HomeScreenTest.homeScreen_bannerCta_click_invokesCallback` — pins that tapping `banner-cta` invokes `onBannerCta`.

**Caveat:** test #1 reuses the `yield()` busy-wait idiom (see R2-TEST-01). It will not fail in practice because emit #1 will still be in the replay slot when the deferred subscribes, but the *intent* of the test (active subscriber receives a real-time emission, not a replay) is undermined — `replay = 1` makes "active subscriber" and "late subscriber" indistinguishable in this test.

**Pipeline coverage concern (carries from Round 1):** The HomeScreen test sets up `BannerState` directly via `HomeUiState`. It does **not** verify the SyncErrorBus → HomeRoute → HomeScreen pipeline end-to-end. The LaunchedEffect that observes `SyncErrorBus.events` and mutates `twitterBanner` / `redditBanner` in `HomeRoute` remains untested. See R2-TEST-04.

### TEST-04 (MigrationTest constraints) — `6c367a7`

**Verdict: PARTIALLY ACCEPTED. Index assertion is solid; the foreign_key_check half is tautological.**

`MigrationTest.migrate6To7_indexesOrderColumns` does two things:

1. **Index existence (lines 86–99):** Queries `sqlite_master` for `index_tweetEntity_order` and `index_reddit_posts_order`, asserts both exist via `Set` equality. **This is correct and load-bearing** — if MIGRATION_6_7 forgets either `CREATE INDEX`, this fails. Also catches a future rename of either index without updating the FTS query path.

2. **`PRAGMA foreign_key_check` after `PRAGMA foreign_keys = ON` (lines 105–111):** Asserts `cursor.count == 0`. This is **structurally tautological** for this specific test setup. The test creates the DB empty at v6 (`helper.createDatabase(TEST_DB, 6).apply { close() }`), then migrates to v7. No rows exist in `pollIds`, `mediaKeys`, `tweet_tags`, or any FK child table. `PRAGMA foreign_key_check` reports rows that violate FK constraints; with an empty schema it reports zero rows by definition, regardless of whether the migration is correct or not.

The fix would be to insert representative rows at v6 (or v4 → migrate to v6) — particularly `tweetEntity` parents and `pollIds` / `mediaKeys` / `tweet_tags` children — and then verify FK enforcement passes after migrating. See R2-TEST-02.

Even more concerning: `PRAGMA foreign_keys = ON` must be set on the connection level **before** the table is touched, and SQLite may also need a `BEGIN; PRAGMA foreign_keys = ON; END;` cycle to take effect mid-connection. Whether Room's wrapped connection honors mid-test `execSQL("PRAGMA foreign_keys = ON")` is implementation-dependent. If FK enforcement is silently off, even seeded rows would not catch a real violation. See R2-TEST-02.

The 5→6 test (`migrate5To6_compositePkAndDataSurvives`) is genuinely solid — it seeds a row at v5, migrates, and verifies the row survives. That is the correct pattern.

### TEST-05 (LongPressPopup callbacks) — `6c367a7`

**Verdict: ACCEPTED.**

`LongPressPopupTest.popup_action_clicks_invoke_callbacks_and_dismiss` (lines 64–102) clicks all four cells (`popup-action-tag`, `-open`, `-share`, `-delete`), asserts (a) the callbacks fire in order via `assertEquals(listOf("tag", "open", "share", "delete"), firedActions)` and (b) `dismissCount == 4`. This is a meaningful behavior assertion. Both contracts are pinned:
- Per-cell callback wiring (refutes a copy-paste bug where two cells share a lambda).
- Dismissal on click (refutes the regression where the popup stays open after a tap).

One minor gap: no test verifies the `headerKicker` / `headerHandle` / `headerAge` render path interacts correctly with dismiss-on-back-press / dismiss-on-click-outside. Low priority — those are framework-provided `PopupProperties`. See R2-TEST-07.

### TEST-06 (DebugDataInjectorTest set-equality) — `6c367a7`

**Verdict: ACCEPTED.**

The patch replaces the vacuous `assertEquals(listOf(...).size, 4)` with a real DAO query at line 56–61:
```kotlin
val tweetIds = db.tweetDao().getAllTweetIds().toSet()
assertEquals(
    "Seed should insert exactly the four debug tweets",
    setOf("debug-tweet-1", "debug-tweet-2", "debug-tweet-3", "debug-tweet-4"),
    tweetIds,
)
```
This actually fails if the seed inserts 0, 3, or 5 rows, or if any id is mistyped. Together with the `db.tweetDao().getLatestBookmark()?.id == "debug-tweet-1"` and `db.redditDao().getPostCount() == 4` assertions, the test now meaningfully covers the seed contract. The hardcoded source set is justified because `DebugDataInjector.seedTweets()` literally writes those four ids (verified in `app/src/debug/.../DebugDataInjector.kt:78–115`).

### TEST-03 (Turbine migration) — Deferred

**Verdict: DEFERRAL CONTESTED. See R2-TEST-01.**

The deferral rationale in `07-review.md` line 263 says:
> "Turbine migration adds a test-only dep across two modules with no functional change; existing `yield`-loop pattern is functional and intentional."

This is wrong on two counts:

1. **"functional"** — The pattern at `DeletedBookmarkRepositoryTest.kt:84–96` is a busy-wait race. It does `scope.async { snackbarBus.events.first() }` then `yield()` + `repeat(20) { yield() }` before `repo.softDelete(...)`. The `MutableSharedFlow` has `replay = 0` and `DROP_OLDEST` (confirmed at `core/data/SnackbarBus.kt` per the H12 fix), so a `tryEmit` that races ahead of the subscriber is **silently dropped**. The 20-yield bound is a guess. On a stressed CI worker (parallel JVM forks, GC pause, slow Robolectric SDK init) the subscriber may not have started collecting in 21 dispatcher loops. When that happens, `deferredEvent.await()` blocks forever and the test hangs until the suite-level JUnit timeout fires — not a clean failure.

2. **"intentional"** — Reusing the same pattern in `SyncErrorBusTest.emit_delivers_event_to_active_collector` accidentally tests a different thing than intended. `SyncErrorBus` has `replay = 1` (post-B2 fix), so the deferred subscriber will get the event from the replay slot regardless of timing. The test passes for the wrong reason — replay semantics, not active subscription semantics.

This deferral hides a real flakiness risk and an accidental loss of test intent. Re-recommend fixing in a small follow-up — Turbine is not the only solution; the simpler `kotlinx.coroutines.test.runTest { backgroundScope.launch { ... } ; advanceUntilIdle() }` pattern works without adding a dependency.

---

## 3) New Findings (Round 2)

### Findings Table

| ID | Severity | Confidence | Category | File:Line | Issue |
|----|----------|------------|----------|-----------|-------|
| R2-TEST-01 | MED | High | Flakiness / wrong-reason | `SyncErrorBusTest.kt:32–48` + `DeletedBookmarkRepositoryTest.kt:84–96` | `repeat(20) { yield() }` pre-subscription race copied into new test; deferral rationale invalid |
| R2-TEST-02 | MED | High | Vacuous assertion | `MigrationTest.kt:105–111` | `PRAGMA foreign_key_check` on empty in-memory DB cannot detect any FK violation |
| R2-TEST-03 | HIGH | High | Coverage gap | `Repository.kt:293–316` + `RedditRepository.kt:219–238` | `refreshTokenSingleFlight` (both repos) — concurrency-critical new code, zero tests |
| R2-TEST-04 | MED | High | Coverage gap | `HomeRoute.kt` LaunchedEffect | SyncErrorBus → HomeRoute banner-state pipeline still untested end-to-end |
| R2-TEST-05 | MED | High | Coverage gap | `FirestoreRepository.kt:getAllTweetIds + tweet-upload merge path` | New paged cursor (MAX_PAGE_HOPS / MAX_BOOKMARK_READ) and idempotent SetOptions.merge upload have no tests |
| R2-TEST-06 | LOW | High | Coverage gap | `CrumbApplication.newImageLoader` | New Coil ImageLoaderFactory config (crossfade 180ms, ~20%/~2% caches) unverified |
| R2-TEST-07 | LOW | High | Coverage gap | `MigrationTest.kt` | No test exists for MIGRATION_7_8 (pollIds + mediaKeys FK indexes) |
| R2-TEST-08 | LOW | Med | Coverage gap | `DebugDataInjector` tombstone-preservation across wipe | Round-1 fix path (CONC-8 / DATA-04) is not asserted by `DebugDataInjectorTest` |
| R2-TEST-09 | NIT | High | Test scope clarity | `HomeScreenTest.homeScreen_bannerCta_click_invokesCallback` | Test name says "banner CTA"; test does not assert the OAuth Intent is dispatched — only the lambda fires |
| R2-TEST-10 | NIT | High | Test scope clarity | `LongPressPopupTest` | Cell tap behavior tested; popup `onDismissRequest` (back press, click outside) untested |

---

## 4) Findings (Detailed)

### R2-TEST-01: yield-loop race carried into new tests; TEST-03 deferral rationale is wrong [MED]

**Locations:**
- `app/src/test/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepositoryTest.kt:84–96` (Round-1 pattern, unchanged)
- `app/src/test/java/com/github/jayteealao/crumbs/data/SyncErrorBusTest.kt:32–48` (new in `7dcf586`, copies the pattern)

**The problematic idiom:**
```kotlin
val scope = CoroutineScope(Dispatchers.Default)
val deferredEvent = scope.async { bus.events.first() }
// Give the collector a chance to subscribe before emission.
yield()
repeat(20) { yield() }
val accepted = bus.emit(SyncErrorEvent.TwitterAuth401())
```

**Why this is racy:**
- `MutableSharedFlow` collectors register via `collect`, which suspends at the channel acquisition. The `async { ... .first() }` launches on `Dispatchers.Default` (a thread pool, not the calling test thread), so `yield()` on the test thread does not directly schedule the collector — it only suspends the test coroutine briefly.
- 21 yields is a *guess at scheduler progress*. On a constrained CI worker with parallel forks, GC pauses, or class-loading delays (Robolectric's first SDK init is notoriously slow), the collector may not be active when `tryEmit` runs.
- For `SyncErrorBus` (replay = 1), the test masks the race because the emission lands in the replay slot and the deferred sees it regardless. **The test always passes — but for the wrong reason.** It is not testing the "active collector" contract; it is testing replay.
- For `DeletedBookmarkRepositoryTest` (snackbarBus has `replay = 0` per H12 / SnackbarBus.kt), there is no replay slot. If the collector misses the emit, `deferredEvent.await()` hangs until the JUnit suite-level timeout.

**Why the TEST-03 deferral rationale fails:**

Round-1 deferral (in `07-review.md` line 263) claims:
> "Turbine migration adds a test-only dep across two modules with no functional change; existing `yield`-loop pattern is functional and intentional."

Both claims are false:
- **Not functional:** the pattern hides a CI hang risk that does not surface locally.
- **Not intentional:** the same pattern was copied into `SyncErrorBusTest.emit_delivers_event_to_active_collector` where it accidentally tests replay instead of active subscription. If the deferral were valid, why does the copy lose its intent?

**Fix (no Turbine needed):**
```kotlin
@Test
fun emit_delivers_event_to_active_collector() = runTest {
    val bus = SyncErrorBus()
    val received = mutableListOf<SyncErrorEvent>()
    val job = backgroundScope.launch { bus.events.toList(received) }
    runCurrent()  // guarantees collector is registered before emit
    bus.emit(SyncErrorEvent.TwitterAuth401())
    advanceUntilIdle()
    assertTrue(received.first() is SyncErrorEvent.TwitterAuth401)
    job.cancel()
}
```

`kotlinx-coroutines-test` is already on the classpath (used elsewhere in the suite). No new dep required.

**Severity:** MED — the test passes today but the determinism guarantee is fictional, and a future test added without `replay = 1` (e.g., for a future `replay = 0` bus) will inherit the same pattern and hang in CI.
**Confidence:** High — race verified by reading the implementation.

---

### R2-TEST-02: PRAGMA foreign_key_check on empty DB is tautological [MED]

**Location:** `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt:105–111`

**The assertion:**
```kotlin
db.execSQL("PRAGMA foreign_keys = ON")
db.query("PRAGMA foreign_key_check").use { cursor ->
    assertTrue(
        "PRAGMA foreign_key_check should return no rows (no FK violations)",
        cursor.count == 0,
    )
}
```

**Why this does not prove what the comment claims:**

The comment above the block says:
> "this validates that the migration did not orphan any rows from earlier joins on tweet_tags/reddit_posts."

But the test setup (line 73) is `helper.createDatabase(TEST_DB, 6).apply { close() }` — an empty v6 database. There are no rows in `tweetEntity`, `pollIds`, `mediaKeys`, `tweet_tags`, `reddit_posts`, or `deleted_bookmarks`. `PRAGMA foreign_key_check` returns rows that violate FK constraints. With zero rows in any child table, the check is vacuously satisfied — it would pass even if MIGRATION_6_7 dropped every FK declaration. The test cannot distinguish a correct migration from an FK-removing one.

**Secondary concern:** `PRAGMA foreign_keys = ON` issued mid-connection via `execSQL` is silently ignored on some SQLite/Room configurations (it must be set before any table is accessed). Whether `MigrationTestHelper.runMigrationsAndValidate` resets this on its returned connection is undocumented. Even with seeded rows the check might silently no-op.

**Fix:**
Either remove the FK check (the index-existence half is the load-bearing assertion) **or** seed representative rows. Example for the seed-rows path:
```kotlin
helper.createDatabase(TEST_DB, 6).apply {
    execSQL("INSERT INTO tweetEntity (id, text, createdAt, authorId, conversationId, ...) VALUES ('t1', 't', '2026-01-01', 'u1', 't1', ...)")
    execSQL("INSERT INTO pollIds (id, tweetId) VALUES ('p1', 't1')")
    execSQL("INSERT INTO mediaKeys (media_key, tweet_id) VALUES ('m1', 't1')")
    close()
}
val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)
// Now FK check is meaningful — orphaned children would surface.
db.execSQL("PRAGMA foreign_keys = ON")
db.query("PRAGMA foreign_key_check").use { c -> assertEquals(0, c.count) }

// Also assert the rows survived and the indexes can serve a query plan:
db.query("EXPLAIN QUERY PLAN SELECT * FROM tweetEntity ORDER BY `order` DESC LIMIT 1")
    .use { c -> /* assert plan mentions index_tweetEntity_order */ }
```

**Severity:** MED — false confidence in FK integrity across the migration.
**Confidence:** High — the test is mechanically tautological under its current setup.

---

### R2-TEST-03: New `refreshTokenSingleFlight` has zero tests in either repository [HIGH]

**Locations:**
- `feature/twitter/.../data/Repository.kt:293–316`
- `feature/reddit/.../data/RedditRepository.kt:219–238`

**Untested behavior** (Twitter; Reddit is structurally identical):
```kotlin
private suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean {
    if (!refreshMutex.tryLock()) {
        Timber.d("refreshTokenSingleFlight: another refresh in flight, deferring")
        return true
    }
    return try {
        _isRefreshing.value = true
        val newTokens = authRepository.refreshAccessToken(currentRefreshToken)
        if (newTokens != null) {
            authPref.setAccessAndRefreshToken(newTokens.accessToken, newTokens.refreshToken)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Timber.e(e, "refreshTokenSingleFlight: exception during refresh")
        false
    } finally {
        refreshMutex.unlock()
    }
}
```

This was added to fix REL-05 / REL-06 / REL-07 / CONC-6 in commit `d417330`. It implements two non-trivial concurrency invariants:

1. **Single-flight:** parallel 401s converge to one network call (the other callers see `tryLock() == false` and return `true` to indicate "another refresh in progress, treat as success").
2. **Token persistence:** the previous fire-and-forget refresh dropped the new token; this version persists it via `authPref.setAccessAndRefreshToken` before returning `true`.

**Scenarios not tested:**
- N parallel callers under contention → exactly one network call dispatched; remaining N−1 return `true` without calling `authRepository.refreshAccessToken`.
- Network success → token persisted and `true` returned.
- Network returns null → `false` returned and token NOT persisted (so the caller can decide to fall through to the banner).
- Exception during refresh → `false` returned, `refreshMutex` released (via `finally`), `_isRefreshing` released.
- Caller B observes `tryLock() == false` while caller A holds the lock → B returns `true` but A may still fail; correctness of B's "optimistic true" depends on whether A succeeds. (This is actually a subtle correctness question: should B re-try if A returns false?)
- Cancellation during refresh → mutex released, no leak.

**Why it matters:** This is the primary auth recovery path. A regression — e.g., moving `unlock()` outside the `finally`, or removing the `_isRefreshing.value = true` line — would silently break refresh on every device. The Reddit Auth banner only appears after refresh fails; if refresh erroneously returns `true`, users see no banner and no recovery.

**Suggested test (one for each repo):**
```kotlin
@Test
fun refresh_singleFlight_collapsesParallelCalls() = runTest {
    val networkCallCount = AtomicInteger(0)
    val fakeAuth = object : AuthRepository {
        override suspend fun refreshAccessToken(rt: String): TokenResponse? {
            networkCallCount.incrementAndGet()
            delay(50)
            return TokenResponse("new-access", "new-refresh", ...)
        }
    }
    val repo = Repository(..., authRepository = fakeAuth, ...)

    val results = (1..10).map {
        async { repo.refreshTokenSingleFlight("rt-${it}") }
    }.awaitAll()

    assertEquals("single-flight should produce exactly one network call", 1, networkCallCount.get())
    assertTrue("all callers should observe success", results.all { it })
    // Verify the new token was persisted (not dropped by the deferring callers):
    assertEquals("new-access", authPref.getAccessToken())
}
```

**Severity:** HIGH — concurrency-critical, in the auth-recovery path, no test.
**Confidence:** High.

---

### R2-TEST-04: SyncErrorBus → HomeRoute LaunchedEffect pipeline still untested end-to-end [MED]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt` (LaunchedEffect block that observes `services.syncErrorBus.events`)

**What the H20 fix tests:**
- `SyncErrorBusTest` — unit-level bus contract.
- `HomeScreenTest.homeScreen_bannerCta_click_invokesCallback` — the leaf composable receives a pre-built `BannerState` and the click invokes the callback.

**What is NOT tested:** The integration between the two — specifically, the `LaunchedEffect` in `HomeRoute` that observes `syncErrorBus.events` and updates `twitterBanner` / `redditBanner` state, plus the `LaunchedEffect(twitterAccess) { if (twitterAccess) banner = null }` clear-on-reauth logic (which was the H4/CR-3 fix).

The Round-1 finding TEST-01 explicitly called this gap out:
> "HomeRoute.kt:78-98 — LaunchedEffect collects events and mutates banner state … is also untested."

The H20 fix added a leaf-composable test (good, but it only tests the leaf), and a unit test for the bus (good, but it only tests the bus). Neither test exercises the LaunchedEffect that ties them together. A regression where the LaunchedEffect collects from the wrong flow, or where the `when` branch fails to match `SyncErrorEvent.TwitterAuth401`, would slip through both tests.

**Suggested test (Compose, with a fake bus + fake services VM):**
```kotlin
@Test
fun homeRoute_twitterAuth401_renders_twitter_banner() = runTest {
    val bus = SyncErrorBus()
    val services = fakeHomeServicesViewModel(syncErrorBus = bus)
    composeTestRule.setContent { CrumbsTheme { HomeRoute(services, ...) } }

    bus.emit(SyncErrorEvent.TwitterAuth401())
    composeTestRule.awaitIdle()

    composeTestRule.onNodeWithTag("banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("banner-cta").assertTextContains("RECONNECT", substring = true)
}

@Test
fun homeRoute_twitterAuthRestored_clears_banner() = runTest {
    val bus = SyncErrorBus()
    val twitterAccess = MutableStateFlow(false)
    composeTestRule.setContent { /* HomeRoute with these */ }
    bus.emit(SyncErrorEvent.TwitterAuth401())
    composeTestRule.awaitIdle()
    composeTestRule.onNodeWithTag("banner").assertIsDisplayed()

    twitterAccess.value = true  // simulate successful re-auth
    composeTestRule.awaitIdle()
    composeTestRule.onNodeWithTag("banner").assertDoesNotExist()
}
```

**Severity:** MED — pipeline gap remains. Round-1 partial fix is better than nothing, but the core integration is still uncovered.
**Confidence:** High.

---

### R2-TEST-05: FirestoreRepository paged cursor + idempotent upload have no tests [MED]

**Locations:**
- `feature/twitter/.../data/firestore/FirestoreRepository.kt` — new `getAllTweetIds()` paged cursor with `MAX_BOOKMARK_READ = 10_000`, `MAX_PAGE_HOPS = 50`, page size 500. Landed in commit `3512352` to fix SEC-04.
- Same file, lines 232–235 — `batch.set(tweetRef, ..., SetOptions.merge())` with deterministic doc id from tweet id. Landed in commit `32e01af` to fix MIG-04 / DATA-idempotency.

**Untested scenarios:**

**Paged cursor (SEC-04 fix):**
- Empty collection → returns empty set, no infinite loop.
- < 500 docs → single page, terminates cleanly.
- Exactly 500 docs → second query returns empty, terminates.
- 501–10 000 docs → terminates within MAX_PAGE_HOPS, returns full set.
- > 10 000 docs → terminates at `MAX_BOOKMARK_READ`, returns partial set. (Is this the intended fail-safe? Or should it raise?)
- > 25 000 docs (MAX_PAGE_HOPS × 500) → terminates at MAX_PAGE_HOPS first. Which limit fires? Behavior under both should be documented and tested.
- Cursor-token reuse across pages — startAfter(lastDoc) wired correctly.

**Idempotent merge upload (MIG-04 fix):**
- Same tweet uploaded twice → exactly one parent doc in Firestore (deterministic id).
- Sub-collections only on first write — but the comment says "Sub-collections only fan out on the first write" — how is "first write" detected? If it's `exists()` check, that itself has a race window. If it's by transaction, the test should pin that.
- Concurrent uploads of the same tweet → both succeed via merge; no doc duplication.

**Why it matters:**
- SEC-04 was a paid-API cost containment fix. If the page-hop loop has an off-by-one (e.g., `<= MAX_PAGE_HOPS` instead of `< MAX_PAGE_HOPS`), a future runaway upload could exceed the cap and burn billable reads.
- MIG-04 was a data-correctness fix. If `SetOptions.merge()` is dropped or the doc-id strategy regresses to `add()`, every sync creates a duplicate parent doc.

**Suggested approach:** Firestore Emulator + Robolectric, or a fake Firestore wrapper. The repository already accepts `FirebaseFirestore` (line 11 import) so it can be injected.

**Severity:** MED — both code paths are in the "fix landed but unverified" zone, on the primary cloud-sync path.
**Confidence:** High.

---

### R2-TEST-06: CrumbApplication.newImageLoader unverified [LOW]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/CrumbApplication.kt`

Commit `0ff5431` added an `ImageLoaderFactory` impl on `CrumbApplication` with `crossfade(180ms)`, memory cache ~20% of available, disk cache ~2%. There is no test verifying:
- The factory is wired (CrumbApplication implements ImageLoaderFactory).
- The cache sizes are within expected bounds.
- Crossfade is enabled (vs the default disabled).

**Why low severity:** This is a configuration concern, not behavior. A misconfig manifests as visual lag (placeholders popping) or memory pressure — caught by manual QA, not unit tests. But a one-line Robolectric test would pin the contract:

```kotlin
@Test
fun application_provides_imageLoader_with_crossfade() {
    val app = ApplicationProvider.getApplicationContext<CrumbApplication>()
    val loader = app.newImageLoader()
    // crossfade is set via a Transition.Factory — assert it's not the default no-op:
    assertNotNull(loader.defaults.transitionFactory)
}
```

**Severity:** LOW.
**Confidence:** High.

---

### R2-TEST-07: MIGRATION_7_8 has no test [LOW]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt:163–168`

`MIGRATION_7_8` was added in commit `32e01af` to index FK columns on `pollIds` and `mediaKeys`. `MigrationTest.kt` covers 4→5, 5→6, 6→7 but stops there. The migration is short (two `CREATE INDEX IF NOT EXISTS` statements) but the test gap is asymmetric — every other migration has a test, this one does not.

**Suggested test (mirror migrate6To7):**
```kotlin
@Test
fun migrate7To8_indexesAttachmentFKs() {
    helper.createDatabase(TEST_DB, 7).apply { close() }
    val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

    val expected = setOf("index_pollIds_tweetId", "index_mediaKeys_tweet_id")
    val found = mutableSetOf<String>()
    db.query(
        "SELECT name FROM sqlite_master WHERE type='index' AND name IN ('index_pollIds_tweetId', 'index_mediaKeys_tweet_id')"
    ).use { c -> while (c.moveToNext()) found += c.getString(0) }
    assertEquals(expected, found)
    db.close()
}
```

**Severity:** LOW.
**Confidence:** High.

---

### R2-TEST-08: Debug-seed tombstone preservation untested [LOW]

**Location:** `app/src/debug/.../DebugDataInjector.kt` (wipe path)

Commit `32e01af` added a tombstone snapshot-and-restore inside the debug seed wipe (CONC-8 / DATA-04 fix). The intent is that a developer who permanently deletes a bookmark, then re-runs the seed with `wipe = true`, does not see the deleted bookmark reappear.

`DebugDataInjectorTest.run_wipeTrue_seedsDeterministicCounts` exercises the seed but does NOT exercise the wipe-preserves-tombstones contract.

**Suggested test:**
```kotlin
@Test
fun run_wipeTrue_preservesDeletedBookmarks() = runBlocking {
    injector.run(wipe = true)  // seeds 4 tweets
    db.deletedBookmarkDao().insert(DeletedBookmark("debug-tweet-2", "twitter", System.currentTimeMillis()))

    injector.run(wipe = true)  // re-seed, should preserve tombstone

    assertTrue(
        "Tombstone for debug-tweet-2 should survive a wipe-and-reseed",
        db.deletedBookmarkDao().exists("debug-tweet-2", "twitter"),
    )
}
```

**Severity:** LOW — debug-only path, but the round-1 fix specifically targets developer ergonomics.
**Confidence:** Med (assumes the wipe path actually re-runs; check the injector implementation).

---

### R2-TEST-09 [NIT]: Banner CTA test name overstates assertion scope

**Location:** `app/src/test/.../HomeScreenTest.kt:135`

Test name: `homeScreen_bannerCta_click_invokesCallback`. The test asserts `ctaFired = true` after click. It does **not** assert that an OAuth intent was actually dispatched — that requires either a unit test against the higher-level `HomeRoute.onBannerCta` lambda (which calls `loginViewModel.authIntent()` + `context.startActivity`) or a Maestro flow with intent monitoring.

The Round-1 TEST-02 finding was about the OAuth intent dispatch — strictly, this fix covers the leaf-composable callback wiring, not the intent dispatch. The Round-1 fix status table calls H20 (TEST-02 part) "Fixed" but the fix is a partial fix for the actual concern.

Not a blocker — the test does verify the click path reaches the callback boundary, which is the correct level for a leaf-composable test. But name + comment ("OAuth re-entry path") imply broader coverage than the test delivers.

**Severity:** NIT.

---

### R2-TEST-10 [NIT]: Popup dismiss-on-outside / back-press untested

**Location:** `core/designsystem/.../LongPressPopupTest.kt`

The new `popup_action_clicks_invoke_callbacks_and_dismiss` test verifies dismissal on action click. The popup also dismisses on back press and outside click (`PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = true)` at `CrumbsLongPressPopup.kt:104–107`). Neither is tested. Compose `Popup` is a separate window so behavior cannot be verified through `onNodeWithTag("popup").performKeyPress` straightforwardly — likely needs an instrumentation test. Documenting the gap rather than recommending a fix.

**Severity:** NIT.

---

## 5) Round-Over-Round Coverage Summary

| Behaviour | R1 Status | R2 Status | Note |
|---|---|---|---|
| SyncErrorBus.emit() | ❌ Untested | ✅ 3 unit tests | R2 finds one test masks real intent (R2-TEST-01) |
| Banner CTA leaf-composable callback | ❌ Untested | ✅ Tested | OAuth intent dispatch still indirect (R2-TEST-09) |
| HomeRoute LaunchedEffect (bus → banner) | ❌ Untested | ❌ Still untested | R2-TEST-04 |
| HomeRoute banner clear-on-reauth (H4) | ❌ Untested | ❌ Still untested | R2-TEST-04 |
| Migration 4→5 | ✅ Row-count assertion | ✅ Same | Adequate |
| Migration 5→6 (composite PK) | n/a (new) | ✅ Solid (seed + survive) | Good pattern |
| Migration 6→7 (order indexes) | n/a (new) | ✅ Index half; ❌ FK-check half tautological | R2-TEST-02 |
| Migration 7→8 (FK indexes) | n/a (new) | ❌ No test | R2-TEST-07 |
| LongPressPopup callback wiring | ❌ Screenshot only | ✅ Solid | All 4 cells + dismiss |
| DebugDataInjector seed | ❌ Vacuous assertion | ✅ Set-equality | Adequate |
| DebugDataInjector tombstone preservation | n/a (new fix) | ❌ Untested | R2-TEST-08 |
| refreshTokenSingleFlight | n/a (new fix) | ❌ Untested | R2-TEST-03 |
| FirestoreRepository paged cursor | n/a (new fix) | ❌ Untested | R2-TEST-05 |
| FirestoreRepository merge-upload | n/a (new fix) | ❌ Untested | R2-TEST-05 |
| Coil ImageLoaderFactory | n/a (new) | ❌ Untested | R2-TEST-06 |
| `DeletedBookmarkRepositoryTest.softDelete_emitsUndoableDeleteEvent` | ⚠️ Yield-race | ⚠️ Still yield-race | R2-TEST-01; deferral contested |

**Net change:** R1 had 10 findings (2 HIGH / 4 MED / 2 LOW / 2 NIT). R2 has 10 findings (1 HIGH / 4 MED / 3 LOW / 2 NIT). Aggregate severity is lower, but **one HIGH new gap (refreshTokenSingleFlight) appeared** because the fix sequence added critical concurrency code without tests.

---

## 6) Maestro Flow Assessment (delta from R1)

No Maestro changes since R1 review. The R1 finding about `sync_error.yaml` lacking a post-CTA assertion remains valid; it was not in the Fix decision set and was not addressed.

---

## 7) Recommendations

### Must Fix (HIGH)
1. **R2-TEST-03** — Add `refreshTokenSingleFlight` unit test for both repos. Concurrency-critical, on the auth-recovery path.

### Should Fix (MED)
2. **R2-TEST-01** — Replace the `yield()` busy-wait in both `SyncErrorBusTest.emit_delivers_event_to_active_collector` and `DeletedBookmarkRepositoryTest.softDelete_emitsUndoableDeleteEvent` with `runTest { backgroundScope.launch + runCurrent() + emit + advanceUntilIdle }`. Either re-open TEST-03 or add a focused remediation. Estimated effort: 15 min, no new deps.
3. **R2-TEST-02** — Either drop the `PRAGMA foreign_key_check` half of `migrate6To7_indexesOrderColumns` (its FK claim is unsupported by the empty-DB setup) or seed representative parent + child rows before migrating. Estimated effort: 20 min.
4. **R2-TEST-04** — Add a Compose test that injects a fake `SyncErrorBus` into `HomeRoute` and asserts banner visibility after `bus.emit(TwitterAuth401())`. Same scaffolding as the existing `HomeScreenTest` rule. Estimated effort: 30 min.
5. **R2-TEST-05** — Add FirestoreRepository emulator/fake test for paged cursor + merge upload. Two scenarios: (a) `getAllTweetIds()` terminates within `MAX_PAGE_HOPS`, (b) duplicate upload produces one doc. Estimated effort: 45–60 min.

### Consider (LOW / NIT)
6. **R2-TEST-06** — One-line Robolectric assertion on `CrumbApplication.newImageLoader()`.
7. **R2-TEST-07** — Symmetry: add `migrate7To8_indexesAttachmentFKs` mirroring the 6→7 pattern.
8. **R2-TEST-08** — Add `DebugDataInjectorTest.run_wipeTrue_preservesDeletedBookmarks` for the CONC-8 / DATA-04 fix.
9. **R2-TEST-09 / R2-TEST-10** — Naming + scope clarifications; non-functional.

### Open Question for Triage
The TEST-03 deferral was justified by "yield-loop pattern is functional and intentional." This review contests both claims. **Recommendation:** re-open TEST-03 as a 15-minute fix bundled with R2-TEST-01 — Turbine is not required, and the cost is one method per affected test.

---

## 8) Positive Observations (Round 2)

- `migrate5To6_compositePkAndDataSurvives` is exemplary: seeds a row at the old schema, migrates, asserts the row survives with full column values. This is the right pattern.
- `LongPressPopupTest.popup_action_clicks_invoke_callbacks_and_dismiss` is precise: asserts both per-cell ordering (`firedActions == listOf("tag", "open", "share", "delete")`) and dismiss count (`== 4`). No flakiness, no over-mocking.
- `DebugDataInjectorTest.run_wipeTrue_seedsDeterministicCounts` now has three meaningful DAO-backed assertions; the vacuous literal-to-itself comparison is gone.
- `SyncErrorBusTest.emit_before_subscriber_is_replayed_to_late_collector` is correctly structured — exercises the B2 cold-start replay contract without timing dependencies.
- `HomeScreenTest.homeScreen_bannerCta_click_invokesCallback` is well-scoped to the leaf composable.

---

## 9) Verdict

**APPROVE_WITH_COMMENTS.**

Round-1 testing-finding fixes land correctly, with the caveats that:
- TEST-03's deferral is wrong; the same race pattern was carried into the H20 fix test.
- TEST-04's FK-check assertion is vacuous and should be either fixed or removed.

The substantively unaddressed gap is **R2-TEST-03 (refreshTokenSingleFlight)** — new concurrency-critical code added during the fix sequence has no test. This should not block ship if manual verification is complete (the fix passed reviewer judgment for REL-05/06/07/CONC-6), but **a test should land before the next ship** because regressions here would silently break auth recovery on every device.

---

*Review date: 2026-05-18*
*Round 2 of 2 against `feat/brutalist-redesign`*
