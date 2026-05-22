---
schema: sdlc/v1
type: plan
slug: cloud-function-bookmark-sync
slice-slug: pending-delete
status: complete
stage-number: 4
created-at: "2026-05-22T20:00:19Z"
updated-at: "2026-05-22T20:00:19Z"
metric-files-to-touch: 14
metric-step-count: 18
has-blockers: false
revision-count: 0
tags: [android, room, migration, compose, gesture, swipe, brutalist, firestore, maestro, roborazzi, accessibility]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-pending-delete.md
  siblings:
    - 04-plan-auth-foundation.md
    - 04-plan-functions-oauth.md
    - 04-plan-daily-poll.md
    - 04-plan-poll-correctness.md
    - 04-plan-android-reader.md
  implement: 05-implement-pending-delete.md
next-command: wf-review
next-invocation: "/wf review cloud-function-bookmark-sync"
---

# Plan: pending-delete

The pending-delete slice closes the **server → user → device** half of the X-removal round-trip established by `daily-poll`. `daily-poll` already writes `pending_delete: true` on a tweet doc when it disappears from a fresh X bookmarks page. This slice adds: (a) the Room v9→v10 schema change that surfaces the flag on the device side, (b) the brutalist strikethrough rendering that signals "X removed this — confirm?", and (c) the swipe gestures that write the user's per-item decision back to Firestore. Sub-agent exploration confirmed all supporting infrastructure already exists (`DeletedBookmarkRepository` tombstone path, `MigrationTestHelper` 2.8.4, `CrumbsBookmarkCard` pointerInput scaffolding, `androidx.compose.material3.SwipeToDismissBox`, `DebugIntentHandler` reflection bridge). No new libraries are introduced.

## Current State

- **Room DB at v9** ([app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt:42](app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt:42)). Five existing `MIGRATION_X_Y` objects in [Migrations.kt](app/src/main/java/com/github/jayteealao/crumbs/db/Migrations.kt), all hand-rolled. Schema export at [app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/9.json](app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/9.json). `MigrationTest` ([app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt](app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt)) covers every existing migration with the canonical pattern.
- **`TweetEntity`** at [feature/twitter/src/main/java/com/github/jayteealao/twitter/models/Tweet.kt:42](feature/twitter/src/main/java/com/github/jayteealao/twitter/models/Tweet.kt:42) has 9 fields; no `pendingDelete` column. **`TweetDao`** at [feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt) exposes `getTweetsTombstoneAware()` (LEFT JOIN against `deleted_bookmarks`, WHERE tombstone IS NULL). Projection does not include any pending-delete signal.
- **`FirestoreTweet` DTO** at [feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreModels.kt:15](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreModels.kt:15) has no `pending_delete` property. **`FirestoreRepository`** ([feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt)) currently reads tweets but writes only via the batched `uploadTweet` path — no single-field write helper exists yet.
- **`CrumbsBookmarkCard`** ([core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt:49](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt:49)) uses `pointerInput { detectTapGestures(onTap, onLongPress) }` (lines 78–81). No swipe handler. `bookmark.isDeleted` already drives a semi-transparent overlay (line 221) — the deleted-overlay pattern is the closest precedent for the pending-delete strikethrough.
- **`BookmarksViewModel`** ([feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt)) exposes `softDelete` + `undoDelete`. Those write tombstones via `DeletedBookmarkRepository` but DO NOT write to Firestore — `pending-delete`'s swipe handlers must add Firestore writes alongside the Room/tombstone writes.
- **Long-press popup** ([maestro/long_press.yaml:30](maestro/long_press.yaml:30)) already exists. Swipe must NOT consume touches before the long-press detector commits. `SwipeToDismissBox` arbitrates this via its internal `AnchoredDraggable` slop, so layering the swipe-box AROUND the card (not replacing the card's pointerInput) keeps the existing long-press path intact.
- **Debug seed bridge** ([app/src/debug/.../DebugIntentHandler.kt:21](app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt:21) — discovered via Explore sub-agent 3) already supports four `debug_action` keys (`seed`, `wipe`, `corrupt_token`, `seed_sync_status`). Maestro flows wire them via `launchApp.arguments`. Adding `seed_pending_delete` follows the established pattern.
- **9 Maestro flows** at [maestro/](maestro/) — no `pending_delete_swipe.yaml` yet. **2 committed Roborazzi PNGs** under [feature/twitter/src/test/screenshots/](feature/twitter/src/test/screenshots/) for `TwitterBookmarksScreen_loggedOut_{light,dark}.png`. android-reader's logged-in PNGs live under `app/src/test/screenshots/` per its implement record.
- **CI** ([.github/workflows/pr_check.yml:76](.github/workflows/pr_check.yml:76)) gates on `assembleDebug + lintDebug + verifyRoborazziDebug`. No `connectedDebugAndroidTest`. MigrationTest stays operator-manual on the verify-stage checklist.

## Reuse Opportunities

- [core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt](core/data/src/main/java/com/github/jayteealao/crumbs/data/DeletedBookmarkDao.kt) → `insert(DeletedBookmark)` + `delete(bookmarkId, source)` — **reuse as-is** for swipe-right tombstone write. Already wired into `Repository.softDelete()` and `Repository.undoDelete()`.
- [app/src/main/java/com/github/jayteealao/crumbs/db/Migrations.kt](app/src/main/java/com/github/jayteealao/crumbs/db/Migrations.kt) MIGRATION_8_9 pattern → **reuse as template** for the new MIGRATION_9_10 object. Same shape: `object : Migration(9, 10) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE …") } }`.
- [feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt) `softDelete(id)` / `undoDelete(id)` → **reuse with modification** — call the existing tombstone path from the new `confirmDeletePending(id)` handler, and bolt on a Firestore write. `cancelDeletePending(id)` is a fresh ViewModel method but composes existing Repository deps.
- [core/designsystem/.../CrumbsBookmarkCard.kt:221](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt:221) deleted-overlay rendering → **reuse the conditional-render pattern** (don't reuse the overlay itself — strikethrough is a different decoration). Same place in the composable tree.
- [app/src/androidTest/.../MigrationTest.kt:149-196](app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt:149) `migrate8To9_addsRedditTagCrossRefTable()` → **reuse as template** for `migrate9To10_addsPendingDeleteColumn()`.
- [app/src/debug/.../DebugIntentHandler.kt:54](app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt:54) `seed_sync_status` branch → **reuse as template** for new `seed_pending_delete` action.
- [maestro/pull_to_refresh.yaml](maestro/pull_to_refresh.yaml) + [maestro/connect_x_blocking.yaml](maestro/connect_x_blocking.yaml) → **reuse flow scaffold** (appId + launchApp.arguments + extendedWaitUntil + testTag taps) for `pending_delete_swipe.yaml`.

## Likely Files / Areas to Touch

**Modified (9):**
1. [app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt](app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt) — bump `version = 9` → `10`.
2. [app/src/main/java/com/github/jayteealao/crumbs/db/Migrations.kt](app/src/main/java/com/github/jayteealao/crumbs/db/Migrations.kt) — add `MIGRATION_9_10` + extend `ALL_MIGRATIONS` array.
3. [feature/twitter/src/main/java/com/github/jayteealao/twitter/models/Tweet.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/models/Tweet.kt) — `TweetEntity` gains `@ColumnInfo(name = "pending_delete") val pendingDelete: Boolean = false`.
4. [feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt) — extend `getTweetsTombstoneAware()` projection to surface `pending_delete`. Add `updatePendingDelete(id, value)` suspend method for cancel-swipe Room write.
5. [feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreModels.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreModels.kt) — `FirestoreTweet` gains `@PropertyName("pending_delete") val pendingDelete: Boolean? = null`. `toTweetEntity()` maps `pendingDelete ?: false`.
6. [feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt) — add typed `markDeleted(tweetId)` and `cancelPendingDelete(tweetId)` suspend methods.
7. [feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt) — add `confirmDeletePending(id)` (Firestore `markDeleted` + existing tombstone path) and `cancelDeletePending(id)` (Firestore `cancelPendingDelete` + Room `updatePendingDelete(id, false)`).
8. [feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt) — two new methods wrapping Repository calls; expose `snackbarEvents` line for offline-queue messaging.
9. [feature/twitter/src/main/java/com/github/jayteealao/twitter/data/model/Bookmark.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/model/Bookmark.kt) (or wherever the UI `Bookmark` model lives — confirmed via mapper trail; if it doesn't exist as a separate file the mapper is inline in `Repository`) — add `pendingDelete: Boolean = false`. Update the mapper from `TweetEntity` → `Bookmark`.
10. [core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt) — wrap card content in `SwipeToDismissBox` keyed by `bookmark.id` (gated by new `pendingDelete` param being true — when false the SwipeToDismissBox is skipped entirely so Reddit's call-site is a no-op). Apply `Modifier.brutalistStrikethrough(active = pendingDelete)` to the title Text. Add `Modifier.semantics { stateDescription = ...; liveRegion = LiveRegionMode.Polite }` to the card root.
11. [feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt) — wire two new lambdas `onConfirmDeletePending: (id) -> Unit` + `onCancelDeletePending: (id) -> Unit` from the route down into `CrumbsBookmarkCard`. Pass `pendingDelete = bookmark.pendingDelete`.
12. [app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt](app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt) — add `seedPendingDelete()` method (inserts 2 dedicated rows with `pendingDelete = true` + ids `debug-pending-1` / `debug-pending-2`).
13. [app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt](app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt) — branch on `"seed_pending_delete"` → `injector.seedPendingDelete()`.

**New (5):**
14. `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/modifiers/BrutalistStrikethrough.kt` — `Modifier.brutalistStrikethrough(active, color, strokeWidthDp)` extension built on `drawWithContent` + `drawLine(StrokeCap.Square)` at `size.height / 2f`. ~25 LOC.
15. `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt` — extend the existing class with `migrate9To10_addsPendingDeleteColumn()` method (NOT a new file — the existing file just grows).
16. `feature/twitter/src/test/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreenPendingDeleteTest.kt` — Robolectric + Roborazzi test class with `withPendingDelete_light/dark` + `noPendingDelete_light/dark` (4 PNGs).
17. `feature/twitter/src/test/java/com/github/jayteealao/twitter/data/SwipeHandlerTest.kt` — unit test for `Repository.confirmDeletePending` + `cancelDeletePending` (MockK fakes for `FirestoreRepository`, `TweetDao`, `DeletedBookmarkDao`). 4 cases.
18. `maestro/pending_delete_swipe.yaml` — Maestro flow: seed pending-delete docs via `debug_action: "seed_pending_delete"`, assert strikethrough visible, swipe-right (assert row gone), swipe-left on second item (assert normal styling).

**Indirectly touched (3):**
- `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/10.json` (new, auto-generated by KSP at build time) — must be committed.
- [feature/twitter/src/test/screenshots/](feature/twitter/src/test/screenshots/) — 4 new PNG references committed.
- [.ai/workflows/cloud-function-bookmark-sync/00-index.md](00-index.md) — workflow-files + progress fields.

## Proposed Change Strategy

Five surgical phases, each ending in a verifiable build artifact. No architectural reshuffle — every addition piggybacks on a pattern that already exists in the repo.

**Phase 0 — Data layer (Room v9 → v10):**
Bump the DB version, add the manual `MIGRATION_9_10` object, add the column to `TweetEntity`, extend `TweetDao` projection + add `updatePendingDelete()`. Add the instrumented `migrate9To10_*` test. Build target: `:app:compileDebugKotlin` clean + the new schema JSON checked in. This phase is a pure DB-layer change with zero UI impact.

**Phase 1 — Read path (Firestore → Room mapping):**
Extend `FirestoreTweet` DTO with `pending_delete: Boolean?` (nullable to handle pre-existing docs without the field). Extend `toTweetEntity()` mapper to propagate `pendingDelete ?: false`. Build target: existing daily-poll-written docs with `pending_delete: true` now flow into Room with the column set. Verified by android-reader's existing reader unit tests still passing.

**Phase 2 — Write path (Firestore field writes):**
Add typed `markDeleted(tweetId)` + `cancelPendingDelete(tweetId)` methods on `FirestoreRepository`. Each uses `.collection("users").document(uid).collection("tweets").document(tweetId).update(map).await()`. `markDeleted` writes `mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp())`. `cancelPendingDelete` writes `mapOf("pending_delete" to false)`. Add `Repository.confirmDeletePending(id)` + `cancelDeletePending(id)` composing Firestore + tombstone/Room paths. New `SwipeHandlerTest.kt` covers all four method-level paths.

**Phase 3 — UI (gesture + strikethrough + a11y):**
Create the `Modifier.brutalistStrikethrough` extension. Extend `CrumbsBookmarkCard` with `pendingDelete: Boolean = false` param + `onSwipeConfirmDelete: ((id) -> Unit)? = null` + `onSwipeCancelPending: ((id) -> Unit)? = null` lambdas. When `pendingDelete = true`, wrap the card content in `SwipeToDismissBox(state = rememberSwipeToDismissBoxState(...))` with `confirmValueChange` routing `StartToEnd` → cancel, `EndToStart` → confirm. Apply `brutalistStrikethrough(active = pendingDelete)` to the title Text. Add `Modifier.semantics { stateDescription = …; liveRegion = LiveRegionMode.Polite }` to the card root. Suppress Material ripple on the inner clickable via `indication = null`. Wire the lambdas + flag through `TwitterBookmarksScreen` from `BookmarksViewModel`. Reddit's call-site keeps defaults (no swipe, no strikethrough).

**Phase 4 — Tests + Maestro:**
Robolectric + Roborazzi test class with 4 PNG captures (`withPendingDelete_light/dark` + `noPendingDelete_light/dark` — the second pair establishes the regression baseline since the existing screenshots are logged-out states). DebugDataInjector + DebugIntentHandler wiring. Maestro `pending_delete_swipe.yaml`. CI gate (`verifyRoborazziDebug`) catches strikethrough rendering regressions. MigrationTest stays operator-manual.

Cross-cutting invariants this slice MUST preserve:
- **Bookmark id stability** — `SwipeToDismissBox` state keyed by `bookmark.id` so dismissed items don't replay state on neighbors (web research §2 hoist-keyed pattern).
- **Offline write safety** — Firestore SDK queues `update()` calls; Room write is synchronous and the visible UI state is correct regardless of when Firestore acks. Documented in `BookmarksViewModel.snackbarEvents` if a queue-pending toast is desired (defer to verify-stage operator feedback).
- **Long-press takes priority** — `SwipeToDismissBox` arbitrates against the card's existing `detectTapGestures` (which detects both tap + long-press) by consuming horizontal motion past slop only. Maestro flow exercises both `longPressOn` and `swipe` against the same row to catch regressions.

## Step-by-Step Plan

**Phase 0 — Room v9 → v10 (data layer)**

1. **Add `pendingDelete` to `TweetEntity`.** [feature/twitter/.../models/Tweet.kt:42](feature/twitter/src/main/java/com/github/jayteealao/twitter/models/Tweet.kt:42). Add `@ColumnInfo(name = "pending_delete") val pendingDelete: Boolean = false`.

2. **Bump DB version + register migration.** [app/.../AppDatabase.kt](app/src/main/java/com/github/jayteealao/crumbs/db/AppDatabase.kt): change `version = 9` → `version = 10`. [app/.../Migrations.kt](app/src/main/java/com/github/jayteealao/crumbs/db/Migrations.kt): add `MIGRATION_9_10` as `object : Migration(9, 10) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE tweetEntity ADD COLUMN pending_delete INTEGER NOT NULL DEFAULT 0") } }`. Append to `ALL_MIGRATIONS` array.

3. **Build + commit schema 10.** Run `./gradlew :app:compileDebugKotlin` (or `:app:assembleDebug`) to trigger KSP schema export. Commit `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/10.json`.

4. **Extend `TweetDao`.** [feature/twitter/.../data/TweetDao.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/TweetDao.kt): add `@Query("UPDATE tweetEntity SET pending_delete = :value WHERE id = :id") suspend fun updatePendingDelete(id: String, value: Boolean)`. Verify the existing `getTweetsTombstoneAware()` `@Query` already does `SELECT t.* FROM tweetEntity t LEFT JOIN deleted_bookmarks d ON …` — if it uses `t.*` the new column is auto-projected; if it lists explicit columns, add `t.pending_delete` to the SELECT list.

5. **Add `migrate9To10` instrumented test.** [app/src/androidTest/.../db/MigrationTest.kt](app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt): add new `@Test fun migrate9To10_addsPendingDeleteColumn()`. Create v9 DB, insert one TweetEntity-shaped row via raw `db.execSQL("INSERT INTO tweetEntity …")`, run `helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)`, query post-migration to assert `pending_delete` column exists + the seed row's value is 0.

**Phase 1 — Firestore read mapping**

6. **Extend `FirestoreTweet` DTO.** [feature/twitter/.../firestore/FirestoreModels.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreModels.kt): add `@PropertyName("pending_delete") val pendingDelete: Boolean? = null` (nullable for backward-compat with pre-poll-correctness docs). Extend `toTweetEntity()` to set `pendingDelete = this.pendingDelete ?: false`.

**Phase 2 — Firestore write API**

7. **Add typed Firestore write methods.** [feature/twitter/.../firestore/FirestoreRepository.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/firestore/FirestoreRepository.kt): add:
   ```kotlin
   suspend fun markDeleted(tweetId: String) {
       val uid = auth.currentUser?.uid ?: return
       db.collection("users").document(uid).collection("tweets").document(tweetId)
           .update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp())).await()
   }
   suspend fun cancelPendingDelete(tweetId: String) {
       val uid = auth.currentUser?.uid ?: return
       db.collection("users").document(uid).collection("tweets").document(tweetId)
           .update("pending_delete", false).await()
   }
   ```
   Both methods are fire-and-forget from the caller's perspective; the SDK auto-queues offline.

8. **Add Repository swipe handlers.** [feature/twitter/.../data/Repository.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt):
   ```kotlin
   suspend fun confirmDeletePending(id: String) {
       deletedBookmarkRepository.softDelete(id, Source.Twitter) // existing tombstone path
       firestoreRepository.markDeleted(id)                       // Firestore write
   }
   suspend fun cancelDeletePending(id: String) {
       tweetDao.updatePendingDelete(id, false)                   // Room update
       firestoreRepository.cancelPendingDelete(id)               // Firestore write
   }
   ```
   Room write FIRST so the visible state flips immediately even if Firestore is offline.

**Phase 3 — Bookmark UI model + ViewModel**

9. **Propagate `pendingDelete` into UI Bookmark.** Locate the `Bookmark` UI model (Explore sub-agent A flagged it as either a separate `data/model/Bookmark.kt` or inline in the Repository's mapper — confirm at implement-time). Add `val pendingDelete: Boolean = false`. Update the `TweetEntity` → `Bookmark` mapper to copy the field. Reddit's `RedditPostEntity` → `Bookmark` mapper stays unchanged (default `false`).

10. **Add ViewModel handlers.** [feature/twitter/.../screens/BookmarksViewModel.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/BookmarksViewModel.kt): add `fun confirmDeletePending(id: String) = viewModelScope.launch { repository.confirmDeletePending(id) }` and `fun cancelDeletePending(id: String) = viewModelScope.launch { repository.cancelDeletePending(id) }`. No coroutine context override needed — `Repository` methods already use `Dispatchers.IO` internally.

**Phase 4 — Strikethrough modifier + Card + Screen**

11. **Create `Modifier.brutalistStrikethrough`.** New file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/modifiers/BrutalistStrikethrough.kt`:
    ```kotlin
    fun Modifier.brutalistStrikethrough(
        active: Boolean,
        color: Color = Color.Black,
        strokeWidthDp: Dp = 2.dp,
    ): Modifier = this.drawWithContent {
        drawContent()
        if (active) {
            val strokePx = strokeWidthDp.toPx()
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokePx,
                cap = StrokeCap.Square,
            )
        }
    }
    ```

12. **Extend `CrumbsBookmarkCard` signature.** [core/designsystem/.../CrumbsBookmarkCard.kt](core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBookmarkCard.kt): add params `pendingDelete: Boolean = false`, `onSwipeConfirmDelete: ((String) -> Unit)? = null`, `onSwipeCancelPending: ((String) -> Unit)? = null`. Wrap the existing card body inside an `if (pendingDelete) { SwipeToDismissBox(...) { body() } } else { body() }` branch — Reddit's call-site is the `else` branch. Inside `SwipeToDismissBox`, key the state by `bookmark.id`: `val state = rememberSwipeToDismissBoxState(...)` with `confirmValueChange = { value -> when (value) { SwipeToDismissBoxValue.StartToEnd -> { onSwipeCancelPending?.invoke(bookmark.id); false }; SwipeToDismissBoxValue.EndToStart -> { onSwipeConfirmDelete?.invoke(bookmark.id); false }; else -> false } }`. `confirmValueChange` returns `false` so the box snaps back; the actual list-item disappearance is driven by the Room/tombstone update via the paging Flow. Apply `Modifier.brutalistStrikethrough(active = pendingDelete)` to the title Text (around line 175). Add `Modifier.semantics { stateDescription = if (pendingDelete) "Pending removal" else ""; liveRegion = LiveRegionMode.Polite }` to the card root. Suppress the Material ripple on the inner clickable: change `Modifier.clickable {...}` to `Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {...}`.

13. **Wire screen → card.** [feature/twitter/.../screens/TwitterBookmarksScreen.kt](feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt): add `onConfirmDeletePending: (String) -> Unit` and `onCancelDeletePending: (String) -> Unit` params to the screen composable. Pass them + `pendingDelete = bookmark.pendingDelete` to `CrumbsBookmarkCard`. In the route function (line ~168), wire them to `viewModel.confirmDeletePending` / `viewModel.cancelDeletePending`.

**Phase 5 — Tests + Maestro**

14. **`SwipeHandlerTest.kt` unit tests.** New file at `feature/twitter/src/test/java/com/github/jayteealao/twitter/data/SwipeHandlerTest.kt`. 4 cases:
    - `confirmDeletePending_writesFirestoreAndTombstone` — verify both `firestoreRepository.markDeleted` AND `deletedBookmarkRepository.softDelete` called.
    - `cancelDeletePending_writesRoomAndFirestore` — verify both `tweetDao.updatePendingDelete(id, false)` AND `firestoreRepository.cancelPendingDelete` called.
    - `confirmDeletePending_swallowsFirestoreError` — Firestore throws; tombstone still persists; method does not propagate.
    - `cancelDeletePending_roomBeforeFirestore` — assert call ordering (Room write first so UI flips immediately offline).
    MockK + Robolectric runner (matches `SyncStatusRepositoryTest.kt` pattern).

15. **Roborazzi snapshot test.** New file at `feature/twitter/src/test/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreenPendingDeleteTest.kt`. Same Robolectric + Roborazzi scaffolding as `TwitterBookmarksScreenTest.kt`. 4 test methods → 4 PNGs:
    - `withPendingDelete_light` → `TwitterBookmarksScreen_pendingDelete_light.png`
    - `withPendingDelete_dark` → `TwitterBookmarksScreen_pendingDelete_dark.png`
    - `noPendingDelete_light` → `TwitterBookmarksScreen_signedInLinked_light.png` (or coexist with android-reader's existing signed-in PNG if it covers the case)
    - `noPendingDelete_dark` → `TwitterBookmarksScreen_signedInLinked_dark.png` (same)
    Seed a `Bookmark` flow with one `pendingDelete = true` row and three `pendingDelete = false` rows.

16. **DebugDataInjector + DebugIntentHandler wiring.**
    - [app/src/debug/.../DebugDataInjector.kt](app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt): add `suspend fun seedPendingDelete() = withContext(Dispatchers.IO) { db.tweetDao().insertTweet(TweetEntity(id = "debug-pending-1", text = "Pending removal — swipe to confirm", createdAt = "2026-05-21T00:00:00Z", authorId = "debug-user-twitter", conversationId = "debug-pending-1", inReplyToUserId = null, lang = "en", referenced = false, order = 1100, pendingDelete = true)); db.tweetDao().insertTweet(TweetEntity(id = "debug-pending-2", text = "Another removal candidate", createdAt = "2026-05-21T00:01:00Z", authorId = "debug-user-twitter", conversationId = "debug-pending-2", inReplyToUserId = null, lang = "en", referenced = false, order = 1099, pendingDelete = true)) }`.
    - [app/src/debug/.../DebugIntentHandler.kt](app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt): add a new branch `"seed_pending_delete" -> activity.lifecycleScope.launch { injector.seedPendingDelete() }` modeled on the existing `seed_sync_status` branch.

17. **Maestro flow.** New file `maestro/pending_delete_swipe.yaml`:
    ```yaml
    appId: com.github.jayteealao.crumbs
    ---
    - launchApp:
        clearState: true
        arguments:
          debug_action: "seed_sync_status"
          linked: "true"
    - launchApp:
        arguments:
          debug_action: "seed_pending_delete"
    - extendedWaitUntil:
        visible:
          id: "twitter-bookmarks-screen"
        timeout: 10000
    - assertVisible:
        id: "bookmark-card-strikethrough"  # new testTag on pending-delete row
    - swipe:
        direction: LEFT          # swipe-right-to-confirm; gesture is finger LEFT on screen
        from:
          id: "bookmark-card-pending-1"
    - extendedWaitUntil:
        notVisible:
          id: "bookmark-card-pending-1"
        timeout: 5000
    - swipe:
        direction: RIGHT         # swipe-left-to-cancel; finger RIGHT
        from:
          id: "bookmark-card-pending-2"
    - extendedWaitUntil:
        notVisible:
          id: "bookmark-card-strikethrough"
        timeout: 5000
    ```
    Adds two new testTags on `CrumbsBookmarkCard`: `bookmark-card-pending-{id}` and `bookmark-card-strikethrough` (when `pendingDelete = true`).

18. **Update `00-index.md` workflow-files + progress.** Add `04-plan-pending-delete.md` to `workflow-files`. Update `progress.plan` comment (6/7 planned). Bump `updated-at`. This is the per-stage index update — the master `04-plan.md` update is described in Step 19 of this plan's caller, not here.

**Operator checklist (at verify time):**
- Run `./gradlew :app:connectedDebugAndroidTest --tests "com.github.jayteealao.crumbs.db.MigrationTest.migrate9To10_addsPendingDeleteColumn"` on a connected emulator. Capture stdout under `verify-evidence/pending-delete/migration-test.log`.
- Run `maestro test maestro/pending_delete_swipe.yaml` on the same emulator. Capture under `verify-evidence/pending-delete/pending_delete_swipe.{maestro.log,screenshot.png}`.
- Manual one-shot: un-bookmark a known X tweet → wait for next daily poll → confirm doc transitions to `pending_delete: true` → confirm app renders strikethrough on next foreground → swipe-right → confirm doc transitions to `deleted: true` + tombstone present. This closes the daily-poll runtime-evidence deferral for AC7-server end-to-end.

## Test / Verification Plan

### Automated checks

- **Lint/typecheck:** `./gradlew :app:lintDebug :feature:twitter:lintDebug :core:designsystem:lintDebug :app:compileDebugKotlin`. New `Modifier.brutalistStrikethrough` lives in `core/designsystem`; `SwipeToDismissBox` consumption requires `@ExperimentalMaterial3Api` opt-in on `CrumbsBookmarkCard`.
- **Unit tests:** `./gradlew :feature:twitter:testDebugUnitTest`. Covers `SwipeHandlerTest` (4 cases). `BookmarksViewModelTest` may need a single new case asserting `confirmDeletePending` / `cancelDeletePending` dispatch to Repository (extension of the existing test).
- **Roborazzi:** `./gradlew :feature:twitter:verifyRoborazziDebug`. Validates 4 new PNGs (2 strikethrough + 2 baseline). 5% pixel-diff threshold inherited from `gradle.properties`.
- **MigrationTest (operator-manual, not CI-gated):** `./gradlew :app:connectedDebugAndroidTest --tests "*MigrationTest.migrate9To10*"`. Documented in operator checklist; not gated in CI per the explicit posture decision (Round 3 Q11).
- **Build:** `./gradlew :app:assembleDebug`. Confirms schema-export round-trip + the `MIGRATION_9_10` registration compiles.
- **CI gate:** [.github/workflows/pr_check.yml](.github/workflows/pr_check.yml) already runs `assembleDebug + lintDebug + verifyRoborazziDebug`; no CI changes in this slice.

### Interactive verification (human-in-the-loop)

Per `stack.platforms: [android, service]` + `stack.testing: [junit, roborazzi, maestro, jest]` + `stack.observability: [lazylogcat]`. Confirmed at intake, user-confirmed: true.

- **What to verify (AC1: strikethrough renders on pendingDelete row):**
  - Platform & tool: Android (Pixel emulator API 34+) + Maestro flow `maestro/pending_delete_swipe.yaml` (asserts `bookmark-card-strikethrough` testTag visible after seeding).
  - Companion skills: `lazylogcat` for capturing UI tree + Firebase Auth state during the flow (`lazylogcat -t "BookmarksViewModel"`).
  - Bootstrap: `./gradlew :app:installDebug` (debug seed bridge is gated by `BuildConfig.DEBUG`).
  - Steps:
    1. Operator: `./gradlew :app:installDebug && adb shell am start -a android.intent.action.MAIN -n com.github.jayteealao.crumbs/.MainActivity --es debug_action "seed_sync_status" --es linked "true"`.
    2. Operator: `adb shell am broadcast -a com.github.jayteealao.crumbs.DEBUG_ACTION --es debug_action "seed_pending_delete"` (or rely on the Maestro flow's two-`launchApp` chain).
    3. Operator: `maestro test maestro/pending_delete_swipe.yaml`.
  - Evidence capture: `.ai/workflows/cloud-function-bookmark-sync/verify-evidence/pending-delete/{flow}.maestro.log` + `{flow}.lazylogcat.txt` + `{flow}.screenshot.png`. Layout matches the android-reader convention.
  - Pass: Maestro flow exits 0; strikethrough screenshot shows the ink-stroked line over the pending-delete row title; lazylogcat shows `confirmDeletePending`/`cancelDeletePending` dispatched once each.

- **What to verify (AC2: swipe-right writes `{deleted: true, deletedAt}` + tombstone):**
  - Platform & tool: Android + Maestro (covered by the same flow). Manual Firestore inspection via `gcloud firestore documents describe users/{uid}/tweets/{id}` after the flow runs.
  - Steps: Maestro flow swipes left-to-right on the row (finger movement). Operator inspects Firestore for `deleted: true` + non-null `deletedAt`. Inspects local Room via `adb shell run-as com.github.jayteealao.crumbs sqlite3 …` for `deleted_bookmarks` row.
  - Evidence: Firestore JSON snapshot + Room query output under `verify-evidence/pending-delete/`.
  - Pass: Both writes present; row no longer visible in `twitter-bookmarks-screen`.

- **What to verify (AC3: swipe-left writes `pending_delete: false` + Room update):**
  - Same as AC2 but right-to-left swipe. Operator verifies Firestore doc has `pending_delete: false` (not deleted) + Room `tweetEntity.pending_delete = 0`.
  - Evidence: same layout.
  - Pass: Both writes present; row returns to normal styling (no strikethrough); `bookmark-card-strikethrough` testTag no longer visible.

- **What to verify (AC4: Room v9 → v10 migration succeeds + rows survive):**
  - Platform & tool: Android (operator-manual `connectedDebugAndroidTest`).
  - Steps: `./gradlew :app:connectedDebugAndroidTest --tests "*MigrationTest.migrate9To10*"`.
  - Evidence: Gradle test report + stdout under `verify-evidence/pending-delete/migration-test.log`.
  - Pass: Test exits 0; seed row's `pending_delete` is 0 post-migration.

- **What to verify (AC5: Roborazzi PNGs match + Maestro pass + MigrationTest pass):**
  - Captured by the three preceding entries. Composite pass = all three exit 0.

- **What to verify (AC6 / AC11 portion: brutalist conformance — no Material ripple leak on swipe):**
  - Platform & tool: Visual inspection of the Maestro screenshot at the moment a swipe is in progress. Manual review at verify time.
  - Pass: No ripple decoration visible on swipe; only the ink-stroked strikethrough and the swipe affordance background.

- **NFR — gesture conflict regression check:**
  - Platform & tool: Android + Maestro re-run of `maestro/long_press.yaml` against a seeded pending-delete row (manually edited variant — long-press on a `pending_delete = true` row should still pop the long-press menu; swipe should still work; no double-fire).
  - Pass: Long-press menu visible; subsequent swipe also functional; no Maestro flake.

## Risks / Watchouts

- **Bookmark id key collision with `SwipeToDismissBox` state.** Reusing `bookmark.id` as the swipe-box key relies on stable ids. Twitter tweet ids are stable (X snowflakes), so this is safe in practice. Mitigation: explicit `key1 = bookmark.id` on `rememberSwipeToDismissBoxState` per the web-research §2 LazyColumn-key pattern.
- **Long-press vs swipe arbitration.** `SwipeToDismissBox` consumes horizontal drag past slop; the inner `pointerInput` for tap+long-press still receives touch-down + vertical motion. If the long-press detector competes with the swipe slop accumulator (both check the same down event), the user might trigger one inadvertently. Mitigation: Maestro flow `pending_delete_swipe.yaml` tests both gestures on the same row; verify-stage operator re-runs `long_press.yaml` against a seeded pending-delete row.
- **Offline Firestore writes queued indefinitely.** Web research §6 confirmed firebase-bom 34.x keeps queued mutations in SQLite cache, survives process death, no TTL. Risk: writes queued after reboot may not drain until app foreground. Mitigation: `MainActivity.onCreate` already calls `FirebaseFirestore.getInstance()` which initializes the client and drains the queue. Document the behavior in the plan; no code change needed in this slice. Verify-stage: optional power-cycle test (deferred to operator discretion).
- **`@ExperimentalMaterial3Api` opt-in surface.** `SwipeToDismissBox` requires `@OptIn(ExperimentalMaterial3Api::class)` on the composable that consumes it. Apply at `CrumbsBookmarkCard`. No cascading opt-ins.
- **Reddit's `CrumbsBookmarkCard` call-site receives the new params with defaults.** Reddit's screens pass `pendingDelete = false` implicitly; `SwipeToDismissBox` branch is skipped; no behavioral leak. Mitigation: regression Roborazzi pass on `RedditBookmarksScreen` (existing snapshots should remain pixel-identical because the new params default to no-op).
- **Strikethrough drawn over multi-line wrapped title.** Single-line `drawLine(y = size.height / 2f)` is correct for the current single-line title. If the brutalist card ever wraps to 2 lines, the strike misses the second line. Out of scope this slice — but flag as a known limitation in the implement record.
- **Material ripple disable.** Replacing `Modifier.clickable {...}` with `Modifier.clickable(interactionSource, indication = null) {...}` removes focus indication along with ripple. The brutalist card already provides its own visual feedback (border emphasis); confirm at Roborazzi-test time that no focus-state regression appears on `feature/twitter/src/test/screenshots/`.
- **`a11y stateDescription` semantics correction vs slice spec text.** Slice spec line 50 says "accessibility content description 'pending removal'". This plan uses `stateDescription` + `LiveRegion.Polite` instead — a deliberate correction documented in PO Round 2 Q4. Verify-stage AC text must be re-read with this correction; the slice file itself does not need an edit (the plan supersedes).
- **DB version race during install.** If the user has v9 installed and upgrades to a build with v10 schema, Room runs the migration on first DB access. This is the standard happy path; the `migrate9To10_*` test proves it. Manual real-device upgrade test (operator) is the belt-and-braces evidence.

## Dependencies on Other Slices

**Inbound (this slice consumes):**
- `android-reader` (commit `cd107da`) — Firestore reader path + `FirestoreRepository` constructor + `users/{uid}/tweets/` path layout + `TwitterBookmarksScreen` + `BookmarksViewModel` + auth wiring. **Required.** This slice strictly extends; nothing in android-reader needs to change.
- `daily-poll` (commit `6af35ed`) — server-side `pending_delete: true` writes on missing-from-poll detection. Without this, the device never receives a `pending_delete` doc to render. **Required.**
- `poll-correctness` (verified — partial) — BigInt boundary fix + chunked pdBatch precondition reads ensure the server-side `pending_delete` writes succeed. Without it, the daily-poll defects would block AC1 verification. **Required.**

**Outbound (slices that will consume this):**
- `cutover-migration` — inherits the `pendingDelete` projection + the typed Firestore write methods. `cutover-migration`'s deletion sweep of `TwitterApiServiceImpl` happens after this slice's `Repository` swipe handlers are merged; no surface conflict.

## Assumptions

- **TweetEntity is the only entity affected.** The slice spec scopes the migration to "the tweets table." Verified — `pending_delete` is a Twitter-only concept (X bookmarks). Reddit + tags + cross-refs untouched.
- **The `users/{uid}/tweets/{id}` Firestore path is canonical.** Established by `daily-poll` (PO Round 1 Q2) and consumed by `android-reader`. No path drift in this slice.
- **`Source.Twitter` enum exists in `core/data` for `deletedBookmarkRepository.softDelete()`.** Verified at Explore sub-agent A — `Repository.softDelete` calls into this path. Sub-agent confirmed the existing tombstone surface is correct.
- **Bookmark UI model lives in a stable place mappable from TweetEntity.** Implement step 9 locates the exact file at implement time; if it's inline in `Repository.kt`, the change is one mapper-fn edit instead of a separate-file edit. Step count budget tolerates either.
- **`SwipeToDismissBox` available in current `material3` BOM.** android-reader bumped `firebase-bom` to 34.13.0; `compose-bom` is the pre-existing `core/designsystem` BOM. Verify at implement-time that the imported `material3` version exports `SwipeToDismissBox` + `rememberSwipeToDismissBoxState`. Fallback: hand-rolled `AnchoredDraggable` if missing (not anticipated).

## Blockers

None at plan time. All AC have viable tooling within the confirmed `stack:` block. No new libraries required. No external operational steps (Firestore allowlist update, SA bindings, scheduler creation, etc.) — daily-poll already wrote the server-side pieces.

## Freshness Research

- **`androidx.compose.material3.SwipeToDismissBox` (2026)** — still `@ExperimentalMaterial3Api`; idiomatic list-item swipe primitive; built on `AnchoredDraggable`; arbitrates against LazyColumn nested-scroll for free. Key fix: hoist `rememberSwipeToDismissBoxState` keyed by item id to avoid sibling-state replay.
  - Sources: https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss · https://developer.android.com/develop/ui/compose/touch-input/pointer-input/migrate-swipeable
  - Plan takeaway: `rememberSwipeToDismissBoxState(key1 = bookmark.id)`, `confirmValueChange` returns `false` to snap back; the actual UI removal happens through the Room/tombstone-driven Flow.

- **Room MigrationTestHelper canonical pattern (2026)** — `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)` + `runMigrationsAndValidate(name, targetVersion, validateDroppedTables = true, migration)`. Requires v9 + v10 JSONs under `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/`. Room Gradle plugin `room { schemaDirectory(...) }` is the 2026-idiomatic export config — confirm at implement-time that the project already declares it (existing migrations test working ⇒ it does).
  - Sources: https://developer.android.com/training/data-storage/room/migrating-db-versions · https://medium.com/androiddevelopers/testing-room-migrations-be93cdb0d975
  - Plan takeaway: Reuse the `migrate8To9_*` test as a template; assert pendingDelete column = 0 by re-reading a seed row.

- **Manual `Migration(9, 10)` vs `@AutoMigration` (2026)** — Both supported. Manual chosen for this slice (PO Round 1 Q2) for consistency with the 5 existing migrations + an explicit Migration object the test can pass to `runMigrationsAndValidate`.
  - Source: https://developer.android.com/training/data-storage/room/migrating-db-versions
  - Plan takeaway: `db.execSQL("ALTER TABLE tweetEntity ADD COLUMN pending_delete INTEGER NOT NULL DEFAULT 0")`.

- **`FieldValue.serverTimestamp()` for `deletedAt`** — Monotonic, drift-free across devices. Local cache resolves as `null` until ack unless read with `ServerTimestampBehavior.ESTIMATE`. The pending-delete card disappears on swipe-right, so local reads of `deletedAt` are not expected.
  - Source: https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/DocumentSnapshot.ServerTimestampBehavior
  - Plan takeaway: `update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp()))`. No client-side branch on `deletedAt == null`.

- **Firestore offline write queue durability (firebase-bom 34.x)** — `update()`/`set(merge = true)` calls performed offline are journalled to local SQLite cache, replayed on reconnect, survive process death, no documented TTL. Known wrinkle (issue #3221): post-reboot drain may not start until Firestore client init triggers — first foreground after reboot is enough.
  - Sources: https://firebase.google.com/docs/firestore/manage-data/enable-offline · https://github.com/firebase/firebase-android-sdk/issues/3221
  - Plan takeaway: No retry logic in the slice; Room is the source of UI truth. Optional power-cycle verification at verify time.

- **Compose `stateDescription` + `LiveRegion.Polite` for transient row state** — `contentDescription` REPLACES the visible label for TalkBack (wrong for a row with a visible title); `stateDescription` LAYERS after the natural label. `LiveRegionMode.Polite` auto-announces state changes while the row remains on-screen.
  - Source: https://developer.android.com/develop/ui/compose/accessibility/semantics
  - Plan takeaway: Spec correction documented. Card root gets `Modifier.semantics { stateDescription = …; liveRegion = LiveRegionMode.Polite }`.

- **Brutalist ink-stroke strikethrough via `Modifier.drawWithContent`** — `TextDecoration.LineThrough` has no stroke control; the 2025/2026 idiom for chunky strike is `drawWithContent { drawContent(); drawLine(color, start, end, strokeWidth, StrokeCap.Square) }` overlaying at `size.height / 2f`.
  - Source: https://blog.stylingandroid.com/compose-strikethru-animation/
  - Plan takeaway: Standalone `Modifier.brutalistStrikethrough` in `core/designsystem/.../modifiers/`. ~25 LOC.

- **Material ripple suppression (2026)** — `Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {...}` is the most surgical fix; `LocalRippleConfiguration provides null` is broader (subtree); custom Indication is broadest (kills focus too).
  - Source: https://developer.android.com/develop/ui/compose/touch-input/user-interactions/migrate-indication-ripple
  - Plan takeaway: Per-clickable fix on the inner card content. Confirm TalkBack focus indication still renders at Roborazzi-test time.

- **`ALTER TABLE … ADD COLUMN <name> INTEGER NOT NULL DEFAULT 0` for Kotlin Boolean** — canonical SQL; Room maps Kotlin `Boolean` to SQLite `INTEGER`. Default 0 = `false`. Confirmed unchanged in Room 2.8.x.
  - Source: https://developer.android.com/training/data-storage/room/migrating-db-versions
  - Plan takeaway: Single-line `db.execSQL(…)` body.

## Revision History
*(none — initial plan)*

## Recommended Next Stage

- **Option A (default):** `/wf implement cloud-function-bookmark-sync pending-delete` — execute the 18-step plan across 5 phases. Run `/compact` first; the planning research (alternatives, web searches, codebase exploration) is noise for implementation. PreCompact hook preserves workflow state.
- **Option B:** `/wf plan cloud-function-bookmark-sync cutover-migration` — plan the final slice in parallel before implementing pending-delete. cutover-migration consumes the typed Firestore write methods this slice introduces, so planning it before this slice's code lands risks a stale assumption if the typed methods' signatures evolve at implement time. Lower-risk to wait until this slice is verified.
- **Option C:** `/wf plan cloud-function-bookmark-sync pending-delete <feedback>` — return with directed corrections (e.g., switch to hand-rolled AnchoredDraggable, drop the brutalistStrikethrough modifier in favor of an overlay Box, add a Maestro flow for the long-press regression case as a separate file).
- **Option D:** `/wf review cloud-function-bookmark-sync` — slug-wide review of the five landed slices before adding more code. Could run in parallel with implement.
