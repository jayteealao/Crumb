---
schema: sdlc/v1
type: slice
slug: brutalist-redesign
slice-slug: behaviors
status: defined
stage-number: 3
created-at: "2026-05-16T22:37:59Z"
updated-at: "2026-05-16T22:37:59Z"
complexity: m
depends-on: [screens]
tags: [behaviors, room, migration, soft-delete, filter, snackbar, banner]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-toolchain.md
    - 03-slice-tokens.md
    - 03-slice-components.md
    - 03-slice-layouts.md
    - 03-slice-screens.md
    - 03-slice-maestro.md
  plan: 04-plan-behaviors.md
  implement: 05-implement-behaviors.md
---

# Slice: Behavior wiring + soft-delete schema

## Goal

Wire the four implied behaviors to real handlers and ship the only DB schema change in the workflow (`deleted_bookmarks` tombstone table). Long-press menu actions perform real work; filter chips re-query the paging source; the snackbar appears on delete with a working undo; the sync-error banner appears on auth failure. After this slice, every interactive affordance in the brutalist design is functional. Version bumps to 2.0 / versionCode 3.

## Why This Slice Exists

Behavior wiring is the only part of the redesign that crosses screen + ViewModel + persistence + sync layers. Doing it after screens are in their final visual form means each behavior can be wired and verified without re-rendering the whole UI. Co-locating the DB schema change with the soft-delete UX gives a reviewer the entire "why" of the relaxed intake non-goal in one self-contained change.

## Scope

**In: DB schema + migration.**
- New Room entity `DeletedBookmark`:
  ```kotlin
  @Entity(tableName = "deleted_bookmarks")
  data class DeletedBookmark(
    @PrimaryKey val bookmarkId: String,
    val deletedAt: Long,
  )
  ```
- New DAO `DeletedBookmarkDao` with `insert`, `delete(id)`, `existsBlocking(id): Boolean`, `getAllIds(): Flow<List<String>>`.
- `AppDatabase` version bump from current (4) to **5**; new exported schema at `app/schemas/com.github.jayteealao.crumbs.db.AppDatabase/5.json`.
- Migration 4→5 (additive `CREATE TABLE`). Migration test under `app/src/androidTest/` exercising a fixture v4 DB.

**In: tombstone-aware sync filter.**
- Modify the existing sync code paths in `feature/twitter` and `feature/reddit` to consult `DeletedBookmarkDao.existsBlocking(id)` (or a cached set) before inserting a new bookmark. This is the **only acceptable touch** of the feature-module sync code in this workflow; the diff must be exactly the filter call, nothing else.

**In: long-press menu actions.**
- Open: route to existing reader / external browser intent (whatever current behavior is in `QuickActionMenu`'s Open handler — preserve).
- Share: standard Android `Intent.ACTION_SEND` share sheet.
- Edit tags / Add to collection: open the rebuilt `TagEditorDialog` for tag editing; collection picking opens `OverlayShell` with a multi-select chip group.
- Delete: write tombstone via `DeletedBookmarkDao.insert(DeletedBookmark(id, now))`; remove from in-memory paging source; show `CrumbsSnackbar`.

**In: soft-delete + undo.**
- `BookmarkRepository` (or wherever Twitter/Reddit ViewModels source paging) emits a snackbar event via a `SharedFlow<SnackbarEvent>`.
- Snackbar event carries the bookmark id and a 5s deadline.
- On UNDO tap within window: `DeletedBookmarkDao.delete(id)` + refresh paging.
- On window expiry: no action — tombstone stays.

**In: filter chip wiring.**
- `CrumbsFilterBar` selection state hoisted to the hosting ViewModel.
- Type filter (single-select): updates a `MutableStateFlow<TypeFilter?>`; paging source query parameter updates; LazyColumn re-keys.
- Tags / Collection multi-select: tapping the chip opens `OverlayShell` with the full options list; APPLY commits the multi-selection to the ViewModel.
- Tag/collection lists sourced from existing Room tables (no schema additions needed).

**In: sync-error banner.**
- New `ErrorBannerState` in the Twitter and Reddit ViewModels: `data class ErrorBannerState(val visible: Boolean, val kicker: String, val onRetry: () -> Unit)`.
- OAuth 401/403 + sync 4xx/5xx errors flip `visible = true` with kicker `"ERR · RECONNECT TWITTER"` / `"ERR · RECONNECT REDDIT"`.
- Banner clears when a subsequent sync succeeds.
- `CrumbsBanner` rendered as the second item in `HomeScaffold`'s top column (between TopBar and FilterBar) when state is visible.

**In: version bump.**
- `app/build.gradle`: `versionCode 3`, `versionName "2.0"`.

**Out:**
- Maestro flows that prove these behaviors (handled by `maestro` slice).
- Any non-redesign-related sync/auth logic change.
- DB schema changes other than the new `deleted_bookmarks` table.
- New top-level features (collections is exposed as a filter using existing data; no collection-management UI is in scope).

## Acceptance Criteria

- **Given** an installed v1.1 DB (version 4) on a Pixel 6 emulator, **when** the new app installs via `android` CLI, **then** Room migration 4→5 runs cleanly and the `deleted_bookmarks` table exists. *(automated — instrumentation test)*
- **Given** the migration test fixture at `app/src/androidTest/.../MigrationTest.kt`, **when** run, **then** v4→v5 migration is exercised and asserts the new table schema. *(automated)*
- **Given** the user long-presses a `CrumbsBookmarkCard`, **when** they tap Delete, **then** the card disappears within 200ms and `CrumbsSnackbar` shows "DELETED · UNDO" for 5s. *(interactive)*
- **Given** the snackbar shown, **when** the user taps UNDO before the timer expires, **then** the tombstone is removed and the card reappears at its original list position. *(interactive)*
- **Given** the snackbar timer expires without UNDO, **when** the next sync runs, **then** the tombstoned bookmark id is filtered out and does not reappear in the feed. *(automated — repository unit test with fake API)*
- **Given** the user taps the Type filter chip "THREAD", **when** the chip selection changes, **then** the feed re-queries within 300ms and shows only thread-type bookmarks. *(interactive)*
- **Given** the user taps the Tags chip, **when** `OverlayShell` opens, **then** all known tags are listed; multi-selecting two tags + tapping APPLY filters the feed to bookmarks tagged with either. *(interactive)*
- **Given** a forced Twitter 401 (intercepted via test API or expired token), **when** the next sync runs, **then** the `CrumbsBanner` appears above the Twitter tab feed within 1s with kicker "ERR · RECONNECT TWITTER". *(interactive)*
- **Given** the banner shown, **when** the user taps the banner CTA, **then** the OAuth flow initiates exactly as the LoginScreen CONNECT TWITTER button does. *(interactive)*
- **Given** the version bump, **when** `aapt dump badging app-debug.apk`, **then** `versionCode='3' versionName='2.0'`. *(automated)*

## Dependencies on Other Slices

- **`screens`**: every screen the behaviors hook into must already render the new component shells.
- **`components`**: `CrumbsSnackbar`, `CrumbsBanner`, `CrumbsLongPressPopup`, `CrumbsFilterBar`, `TagEditorDialog` must exist as final brutalist forms.
- **`layouts`**: `OverlayShell` is the carrier for multi-select filter UI.

## Risks

- **Sync code intake non-goal**: shape relaxed "no API/integration changes" only for the tombstone filter call site. Risk of scope creep into the OAuth or paging-source logic. Mitigation: the diff inside `feature/twitter` and `feature/reddit` must be exactly the `DeletedBookmarkDao` consultation and the new `ErrorBannerState` — anything else flagged in review-stage.
- **Room migration on real user installs**: the maintainer has a v1.1 DB locally (`AppDatabase.db` is in the repo root, untracked). Risk of migration failing on the dev's actual data. Mitigation: instrumentation test uses an exported v4 schema fixture, not the dev's local DB; the dev's local DB can be reset before testing.
- **Paging source invalidation on tombstone**: `Pager.flow` needs `invalidate()` after a tombstone is added/removed. Forgetting it leaves the deleted card visible until scroll. Mitigation: the repository emits an invalidation signal whenever the tombstone DAO mutates.
- **Race: user undoes after sync has already filtered out** — sync ran during the undo window, treated the id as tombstoned, didn't re-fetch. Mitigation: undo *also* triggers a sync refresh for the affected source (cheap because the bookmark already exists in the local table; just needs to re-mark as not-deleted).
- **Banner stacking with filter bar**: shape's layout decision puts banner ABOVE filter bar. Re-verify against `HomeScaffold`'s slot order — may need to add a fourth slot `banner` between `topBar` and `filterBar`. Mitigation: revisit `HomeScaffold` API during plan-stage; this is a minor slot addition, not a re-architecture.
- **Tags chip with zero tags applied**: empty state of `OverlayShell` for tags. Mitigation: design a brutalist empty state inside the shell or fall back to disabled chip when no tags exist.
