---
review: reliability
scope: slug-wide (git diff main...HEAD)
slug: brutalist-redesign
completed: 2026-05-18
verdict: REQUEST_CHANGES
---

# Reliability Review — brutalist-redesign

**Scope:** Full branch diff — all changed Kotlin sources, Maestro flows  
**Reviewer:** Reliability Agent  
**Date:** 2026-05-18

---

## Summary

The branch ships meaningful new reliability surfaces (SyncErrorBus, soft-delete/undo, Room migration 4→5, Paging3 error states). Several design choices are sound. However, there are four issues that need remediation before shipping: the `SyncErrorBus` event replay = 0 means a banner event emitted before the collector subscribes (e.g. during startup Firestore sync) is silently lost; the `startActivity` banner CTA has no `ActivityNotFoundException` guard; `LoginViewModel.refreshToken()` has a crash-level `!!` force-unwrap; and `DeletedBookmarkRepository.isDeleted` calls a blocking Room query from a potentially-IO coroutine context without a guaranteed IO dispatcher.

**Severity Breakdown:**
- BLOCKER: 2
- HIGH: 2
- MED: 3
- LOW: 2
- NIT: 2

**Merge Recommendation:** REQUEST_CHANGES

---

## Findings

---

### REL-01: SyncErrorBus replay=0 — startup errors silently dropped [BLOCKER]

**Severity:** BLOCKER | **Confidence:** High

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/SyncErrorBus.kt:13`

**Issue:**  
`MutableSharedFlow` is constructed with `replay = 0`. The collector in `HomeRoute.kt` is only registered when the Compose tree reaches that LaunchedEffect, which happens after navigation completes. The `Repository.init` block fires `syncFromFirestore()` immediately on construction (before the UI is ready). Any 401 or error raised during that startup Firestore sync calls `syncErrorBus.emit(...)`, but because no collector has yet subscribed and the buffer capacity is only 1 (with `DROP_OLDEST`), the event is discarded. The user never sees a reconnect banner for errors that happen at cold start.

**Evidence:**
```kotlin
// SyncErrorBus.kt:13
private val _events = MutableSharedFlow<SyncErrorEvent>(
    replay = 0,          // <-- no replay: event lost if no collector yet
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)

// Repository.kt:61
init {
    scope.launch(Dispatchers.IO) {
        try {
            ...
            syncFromFirestore()  // can call syncErrorBus.emit() before UI subscribes
        }
    }
}
```

**Failure Scenario:**
1. App cold-starts; Hilt constructs `Repository` singleton.
2. `init` block immediately launches Firestore sync on IO dispatcher.
3. Firestore returns 401 → `syncErrorBus.emit(SyncErrorEvent.TwitterAuth401())` → event dropped (buffer full/no subscriber).
4. UI finishes composing, `LaunchedEffect(Unit)` subscribes to `events`.
5. No event is replayed → banner never shown → user unaware session expired.

**Impact:**
- User impact: Silent auth failure at cold start; app appears to load normally but bookmarks never sync.
- Recovery: Only recoverable if user manually pull-to-refreshes and triggers a second 401.

**Fix:**
```kotlin
// SyncErrorBus.kt — add replay = 1 so the most-recent error survives until the UI subscribes
private val _events = MutableSharedFlow<SyncErrorEvent>(
    replay = 1,
    extraBufferCapacity = 0,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```
Alternatively, model it as a `MutableStateFlow<SyncErrorEvent?>` so the latest error is always observable.

---

### REL-02: `LoginViewModel.refreshToken()` force-unwraps nullable — crash on token refresh failure [BLOCKER]

**Severity:** BLOCKER | **Confidence:** High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/LoginViewModel.kt:46`

**Issue:**  
`authRepository.refreshAccessToken()` returns `Boolean` but the implementation at `AuthRepository.kt:75` returns `false` on failure — it does not throw. However `LoginViewModel.refreshToken()` contains a `!!` force-unwrap on the return. If any future refactor makes this function return `Boolean?`, or if Kotlin's contract inference changes, this becomes a `NullPointerException` crash. More critically, today `authRepository.refreshAccessToken()!!` is called from `LoginViewModel` without any try/catch; a `NullPointerException` from the `!!` would propagate uncaught through the coroutine and crash via the coroutine exception handler with no user-visible error.

**Evidence:**
```kotlin
// LoginViewModel.kt:46
suspend fun refreshToken(): Boolean {
    val refreshed = authRepository.refreshAccessToken()!!   // ← !! on non-null Boolean is a code smell; crashes if refactored
    refreshedTokens = refreshed
    return refreshed
}
```

**Impact:**
- Process crash if `refreshAccessToken()` is ever changed to return `Boolean?`.
- No user-facing error state on refresh failure (even today, the false path has no UI feedback).

**Fix:**
```kotlin
suspend fun refreshToken(): Boolean {
    val refreshed = authRepository.refreshAccessToken() ?: false
    refreshedTokens = refreshed
    return refreshed
}
```

---

### REL-03: Banner CTA `startActivity` unguarded — `ActivityNotFoundException` crash [HIGH]

**Severity:** HIGH | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:142-143`

**Issue:**  
When the banner CTA is tapped, `context.startActivity(loginViewModel.authIntent())` / `context.startActivity(redditViewModel.authIntent())` is called directly with no `try/catch`. If the device has no browser installed (e.g. work profile with browser disabled, minimal test device), or if the `Intent.createChooser` resolves to zero activities, this throws `ActivityNotFoundException` and crashes the app. This is especially relevant on restricted Android profiles commonly used in enterprise environments.

**Evidence:**
```kotlin
// HomeRoute.kt:141-144
onBannerCta = {
    when (activeBanner?.source) {
        BookmarkSource.TWITTER -> context.startActivity(loginViewModel.authIntent())   // ← no catch
        BookmarkSource.REDDIT  -> context.startActivity(redditViewModel.authIntent())  // ← no catch
        else -> Unit
    }
},
```
`TwitterAuthClientImpl.authIntent()` returns `Intent.createChooser(...)` which itself can throw if no activities resolve.

**Failure Scenario:**
1. User's session expires → banner shown.
2. User taps RECONNECT banner CTA.
3. Device has no browser → `ActivityNotFoundException` → unhandled crash.
4. App closes; user cannot re-authenticate.

**Fix:**
```kotlin
onBannerCta = {
    val intent = when (activeBanner?.source) {
        BookmarkSource.TWITTER -> loginViewModel.authIntent()
        BookmarkSource.REDDIT  -> redditViewModel.authIntent()
        else -> null
    }
    intent?.let {
        try {
            context.startActivity(it)
        } catch (e: android.content.ActivityNotFoundException) {
            // Show toast/snackbar: "No browser found. Please install a browser."
            Timber.e(e, "No activity to handle OAuth intent")
        }
    }
},
```

---

### REL-04: `DeletedBookmarkRepository.isDeleted` executes blocking Room query without dispatcher guard [HIGH]

**Severity:** HIGH | **Confidence:** Med

**Location:** `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepository.kt:33` and `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt:19`

**Issue:**  
`DeletedBookmarkDao.existsBlocking` is annotated as a non-suspend `@Query` function that executes a synchronous SQLite call. `DeletedBookmarkRepository.isDeleted()` wraps it without switching dispatchers. This function is called inside `syncFromFirestore()` and inside the `tweetEntitiesChannel.consumeEach` loop in `refreshBookmarksInternal()`, both of which already run on `Dispatchers.IO`. However, there is no guarantee the caller will always be on IO — the function signature exposes a synchronous blocking call that could be invoked on the main thread without any compile-time guard. If ever called on the main thread (e.g. from a hypothetical filter or search path), it will cause an ANR.

Additionally, Room does not allow synchronous queries on the main thread by default (unless `allowMainThreadQueries()` is explicitly set — which the test does set but production does not). On production builds, calling `existsBlocking` off Dispatchers.IO will crash with `IllegalStateException: Cannot access database on the main thread`.

**Evidence:**
```kotlin
// DeletedBookmarkDao.kt:19
@Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id)")
fun existsBlocking(id: String): Boolean   // ← non-suspend, blocking

// DeletedBookmarkRepository.kt:33
fun isDeleted(id: String): Boolean = dao.existsBlocking(id)  // ← no dispatcher switch
```

**Fix:**
Convert to a suspend function with explicit dispatcher:
```kotlin
// DeletedBookmarkDao.kt
@Query("SELECT EXISTS(SELECT 1 FROM deleted_bookmarks WHERE bookmarkId = :id)")
suspend fun exists(id: String): Boolean

// DeletedBookmarkRepository.kt
suspend fun isDeleted(id: String): Boolean =
    withContext(Dispatchers.IO) { dao.exists(id) }
```
All callers in `Repository.kt` and `RedditRepository.kt` are already in suspend contexts.

---

### REL-05: Twitter token refresh fires after banner emit but result is discarded [MED]

**Severity:** MED | **Confidence:** High

**Location:** `feature/twitter/src/main/java/com/github/jayteealao/twitter/utils/ApiResponseExt.kt:53-57`

**Issue:**  
On a 401, `onError` calls `onError()` which both emits the banner event AND calls `twitterAuthClient.refreshAccessToken(refreshToken)`. The refresh result (`TokenResponse?`) is silently discarded — if the refresh succeeds, the new tokens are never stored (saving tokens is handled in `AuthRepository.refreshAccessToken()`, not in `TwitterAuthClientImpl.refreshAccessToken()`). If the refresh fails, there is no second error event. The sequence emits a "reconnect" banner even when a silent token refresh might have succeeded, creating misleading UX.

**Evidence:**
```kotlin
// ApiResponseExt.kt:53-57
}.suspendOnError {
    if (response.code() in 401..404) {
        onError()   // emits SyncErrorBus event AND silently refreshes token
    }
    // refresh result is discarded — new tokens not persisted here
    // banner stays visible even if refresh succeeded
}
```

**Fix:**  
Separate the concerns: on 401, attempt a silent token refresh first; only emit the banner event if the refresh also fails. Or wire the refresh result back to update the DataStore credentials so the next sync attempt uses the new token.

---

### REL-06: Reddit `buildDatabase()` loop has no retry backoff — rapid 401 re-fetch possible [MED]

**Severity:** MED | **Confidence:** Med

**Location:** `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:94-133`

**Issue:**  
The `do-while` pagination loop in `buildDatabase()` calls `redditApiService.getSavedPosts(...)` on every iteration. On a 401, it calls `syncErrorBus.emit(...)` and `redditAuthClient.refreshAccessToken(refreshToken)` in a new fire-and-forget `scope.launch` coroutine, but does not `break` or suspend — `hasMore` is left `false` only because `onSuccess` was never reached, so the loop exits. However, if the API returns a transient 5xx (not captured by the 401 branch), the loop does not break and `hasMore` would remain whatever its last good value was. More critically, the `refreshAccessToken` is launched on a child coroutine that may not complete before `buildDatabase()` is called again (e.g., ViewModel `init` calls `buildDatabase()` twice if `checkAccessToken()` succeeds and triggers a second `buildDatabase()` call). There is no backoff at all on any error path.

**Evidence:**
```kotlin
// RedditRepository.kt:122-132
}.onError {
    if (statusCode.code == 401) {
        syncErrorBus.emit(SyncErrorEvent.RedditAuth401())
        if (refreshToken.isNotBlank()) {
            scope.launch {                     // ← fire-and-forget, not awaited
                redditAuthClient.refreshAccessToken(refreshToken)
            }
        }
    }
    // No break, no backoff for non-401 errors
}
```

**Fix:**  
Add `hasMore = false` (effectively `break`) to all `onError` branches to prevent continued iteration after any error. For the token refresh, `await` the result before deciding whether to retry or exit.

---

### REL-07: Soft-delete undo timer is purely UI-side — app kill during 5s window loses undo opportunity [MED]

**Severity:** MED | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:101-119` + `core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkRepository.kt`

**Issue:**  
The 5-second undo window is controlled entirely by `SnackbarDuration.Short` in the Compose layer. The tombstone is written to Room immediately on `softDelete`. If the app process is killed during the undo window (background kill, OOM, crash), the tombstone remains permanently. There is no TTL column in `deleted_bookmarks` and no cleanup job. The bookmark will never re-appear even if the user relaunches the app and expects it.

Additionally, the `SnackbarHostState.showSnackbar` call is sequential — if two soft-deletes are queued rapidly (two items long-pressed in succession), the second `UndoableDelete` event will be emitted to a `MutableSharedFlow` with `extraBufferCapacity = 1` and `DROP_OLDEST`. The first snackbar must complete before the second is shown; in the meantime the second `UndoableDelete` event sits in the buffer. If a third delete happens before the first snackbar finishes, the second event is silently dropped.

**Evidence:**
```kotlin
// DeletedBookmark.kt — no TTL
data class DeletedBookmark(
    @PrimaryKey val bookmarkId: String,
    val source: String,
    val deletedAt: Long,        // recorded but never used for cleanup
)

// DeletedBookmarkRepository — buffer capacity = 1
private val _events = MutableSharedFlow<SnackbarEvent>(
    replay = 0,
    extraBufferCapacity = 1,  // ← only 1 pending event at a time
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```

**Fix:**
1. Add a periodic cleanup job (or on-startup purge) that deletes tombstones older than a safe threshold (e.g. 24h) if they were never undone.
2. Increase `extraBufferCapacity` to a reasonable value (e.g. 16) to handle rapid multi-delete scenarios, or switch to a `Channel` with unlimited capacity.

---

### REL-08: Room migration 4→5 has no fallback strategy — partial migration leaves DB inconsistent [LOW]

**Severity:** LOW | **Confidence:** Med

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt:117-128`

**Issue:**  
`Room.databaseBuilder` is configured with `.addMigrations(...)` but no `.fallbackToDestructiveMigration()` or custom `MigrationException` handler. If `MIGRATION_4_5` throws (e.g. disk-full while creating `deleted_bookmarks` table), Room will propagate a `RuntimeException` that crashes the app at startup. There is no recovery path. On next launch, Room will fail again with "Migration didn't properly handle" and the app will be permanently unbootable without a fresh install.

The `MIGRATION_4_5` SQL only creates the `deleted_bookmarks` table and is idempotent (`CREATE TABLE IF NOT EXISTS`), so in practice this is low risk. However, the lack of a fallback strategy means a disk-full scenario at migration time permanently bricks the app.

**Fix:**  
Add `.fallbackToDestructiveMigration()` guarded by a build flavor flag, OR wrap the migration in a try/catch that logs and re-throws a descriptive exception the crash reporter can surface.

---

### REL-09: DataStore writes have no failure handling — disk-full silently drops token updates [LOW]

**Severity:** LOW | **Confidence:** Med

**Location:** `core/pref/src/main/java/com/github/jayteealao/pref/AuthPref.kt:23`

**Issue:**  
`Context.writeString` calls `dataStore.edit { ... }` without wrapping in try/catch. If the device is low on storage, `DataStore.edit` can throw `IOException`. This propagates up through `Prefs.setAccessAndRefreshToken()` and `AuthRepository.refreshAccessToken()` as an unhandled exception in a coroutine. On Android, unhandled exceptions in `viewModelScope.launch` or `scope.launch` are handled by the coroutine exception handler — in release builds this typically crashes the process or is silently swallowed depending on the scope configuration. In either case, the user gets no feedback that their tokens were not saved.

**Fix:**
```kotlin
suspend fun Context.writeString(key: String, value: String) {
    try {
        dataStore.edit { pref -> pref[stringPreferencesKey(key)] = value }
    } catch (e: IOException) {
        Timber.e(e, "DataStore write failed for key: $key")
        throw e  // re-throw so callers can surface to user
    }
}
```

---

### REL-10: Maestro `sync_error.yaml` banner SLA uses 2s timeout — stated SLA is 1s [NIT]

**Severity:** NIT | **Confidence:** High

**Location:** `maestro/sync_error.yaml:44-48`

**Issue:**  
The test comment says "SLA is 1s; 2× allowance" and sets `timeout: 2000`. If the product SLA is truly 1 second end-to-end, the test should assert at the SLA boundary (1000ms) with a documented CI flakiness allowance factor, not silently double it. As written, the test passes even if the banner takes 1.9 seconds, which would be an SLA violation.

**Fix:**  
Either lower the timeout to match the actual SLA:
```yaml
- extendedWaitUntil:
    visible:
      id: "banner"
    timeout: 1000  # matches 1s SLA
```
Or add a comment that the 2s allowance is explicitly accepted latency budget, not an SLA doubling.

---

### REL-11: `SyncErrorEvent.Other` is silently swallowed in the collector [NIT]

**Severity:** NIT | **Confidence:** High

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeRoute.kt:96`

**Issue:**  
The `when` branch for `SyncErrorEvent.Other -> Unit` discards the event without logging or showing any UI. If a future sync source emits `Other`, users will see no indication of failure.

**Fix:**  
At minimum, log `Other` events to aid debugging:
```kotlin
is SyncErrorEvent.Other -> Timber.w("Unhandled sync error from ${event.source}: ${event.message}")
```

---

## Dependency Analysis

**External Dependencies:**
- Twitter API (via `TwitterApiClient`): no timeout configured on HTTP client (not inspected in this diff — existing concern)
- Reddit API (via `RedditApiService`): no timeout configured — existing concern, not new in this diff
- Firestore: `syncFromFirestore()` uses SDK default timeouts
- Room (SQLite): local, no network timeout needed
- DataStore (JetpackPrefs): disk I/O, no timeout

**Single Points of Failure:**
- `SyncErrorBus`: singleton SharedFlow — if the ViewModel scope is cancelled before the collector subscribes, all events are lost (REL-01)
- `DeletedBookmarkRepository.isDeleted` synchronous query — SPF for the tombstone gate in sync paths (REL-04)

---

## Error Handling Coverage

| Path | Has try/catch | Has timeout | Has fallback | Risk |
|------|--------------|-------------|--------------|------|
| Twitter sync (refreshBookmarksInternal) | ✅ (outer try/finally) | ❌ | ❌ | MED |
| Reddit sync (buildDatabase) | ✅ (outer try/finally) | ❌ | ❌ | MED |
| Banner CTA startActivity | ❌ | n/a | ❌ | HIGH |
| Token refresh (Twitter) | ❌ (!! crash) | ❌ | ❌ | BLOCKER |
| Room migration 4→5 | ❌ | n/a | ❌ | LOW |
| DataStore writes | ❌ | n/a | ❌ | LOW |
| Soft-delete tombstone | ✅ | n/a | n/a | MED (undo window) |

---

## Recommendations

### Immediate (BLOCKER / HIGH)
1. **REL-01**: Change `SyncErrorBus` to `replay = 1` (or `MutableStateFlow<SyncErrorEvent?>`) so startup errors are not lost.
2. **REL-02**: Replace `authRepository.refreshAccessToken()!!` with `?: false` null-safe unwrap in `LoginViewModel.refreshToken()`.
3. **REL-03**: Wrap `context.startActivity(...)` in `try/catch(ActivityNotFoundException)` in `HomeRoute.onBannerCta`.
4. **REL-04**: Convert `DeletedBookmarkDao.existsBlocking` to a `suspend` function to eliminate the blocking-on-IO API and prevent potential main-thread DB access.

### Short-term (MED)
5. **REL-05**: Separate the token-refresh path from the banner-emit path in `ApiResponseExt.kt`. Only show reconnect banner if silent refresh also fails.
6. **REL-06**: Add explicit `hasMore = false` (loop break) in `RedditRepository.buildDatabase()` `onError` branch; await the refresh coroutine before exiting.
7. **REL-07**: Add a tombstone TTL cleanup (e.g. purge on startup if `deletedAt` > 24h old); increase `extraBufferCapacity` in `DeletedBookmarkRepository` to handle rapid multi-delete.

### Long-term (LOW)
8. **REL-08**: Add a fallback migration strategy for production builds.
9. **REL-09**: Wrap DataStore writes in try/catch to surface disk-full failures to the user.
