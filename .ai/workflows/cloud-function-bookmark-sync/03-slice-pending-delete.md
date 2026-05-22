---
schema: sdlc/v1
type: slice
slug: cloud-function-bookmark-sync
slice-slug: pending-delete
status: defined
stage-number: 3
created-at: "2026-05-19T21:23:52Z"
updated-at: "2026-05-19T21:23:52Z"
complexity: m
depends-on: [android-reader]
tags: [android, room, migration, compose, gesture, maestro, brutalist]
refs:
  index: 00-index.md
  slice-index: 03-slice.md
  siblings:
    - 03-slice-auth-foundation.md
    - 03-slice-functions-oauth.md
    - 03-slice-daily-poll.md
    - 03-slice-android-reader.md
    - 03-slice-cutover-migration.md
  plan: 04-plan-pending-delete.md
  implement: 05-implement-pending-delete.md
status-implement: complete
---

# Slice: pending-delete

## Goal

Land the X-side-removal user experience: a Room migration introducing the `pendingDelete` column, the projection through `TweetDao`, the inline brutalist strikethrough rendering in `TwitterBookmarksScreen`, the swipe-right-confirm / swipe-left-cancel gestures wired to Firestore + Room writes, and a Maestro flow covering the round-trip.

## Why This Slice Exists

This is the only UI surface that introduces a Room schema change in this workflow. Isolating it means the migration (and its `MigrationTest`) get a dedicated verify cycle rather than being bundled with the broader reader work. It also keeps the gesture-heavy code path — long-press collision, swipe thresholds, interaction with the existing brutalist popup — verifiable in a single Maestro flow without contaminating the read-only verification of `android-reader`.

## Scope

**In:**
- `app/.../db/AppDatabase.kt` — bump version (current is 9 per recent migration work; this slice takes it to **v10**). Add a Room `Migration` adding `pendingDelete: Boolean NOT NULL DEFAULT 0` to the tweets table (whichever entity backs Twitter tweets in `feature/twitter`).
- `feature/twitter/.../data/TweetEntity.kt` (or equivalent) — add `pendingDelete: Boolean = false` field.
- `feature/twitter/.../data/TweetDao.kt` — projection includes `pendingDelete`; query gets an optional `includePendingDelete` filter (default `true`, exposed for tests).
- `feature/twitter/.../data/FirestoreRepository.kt` (touched) — the one-shot reader from `android-reader` is extended to map `pending_delete: true` → `pendingDelete = true` in the Room write.
- `feature/twitter/.../screens/TwitterBookmarksScreen.kt` — inline strikethrough rendering for `pendingDelete == true` (text-decoration + ink-stroke overlay per brutalist contract); accessibility content description "pending removal".
- Swipe gesture handlers on `CrumbsBookmarkCard` (or feature-local card) for pending-delete state:
  - Swipe right → Firestore write `{deleted: true, deletedAt: now()}` + Room update; existing `deleted_bookmarks` tombstone write path used.
  - Swipe left → Firestore write `pending_delete: false` + Room update `pendingDelete = false`.
  - Both writes are best-effort offline (Firestore SDK queues); Room writes are immediate.
- Maestro flow `maestro/pending_delete_swipe.yaml` (new) covering: seed a `pending_delete: true` doc, open the list, assert strikethrough rendering, swipe-right one item, swipe-left another, assert end states.
- Roborazzi snapshots: `TwitterBookmarksScreen` with one `pendingDelete = true` item in light + dark.
- Instrumented `MigrationTest` (`androidTest`) — schema assertion from v9 → v10 + a row's data survives migration.
- Robolectric unit tests on the swipe handler logic with mocked Firestore + Room.

**Out (handled by other slices):**
- The server-side write of `pending_delete: true` flags — that's `daily-poll`.
- The reader path that surfaces `sync_status` and the `linked == false` banner — that's `android-reader`.
- The cutover-time deletion of legacy device-side X code — `cutover-migration`.

## Acceptance Criteria

- **Given** a tweet `T` previously bookmarked in X, present in Firestore as `pendingDelete = true`, **when** the user opens Crumb after the next poll, **then** `T` renders with strikethrough styling in the bookmarks list.
- **Given** a `pendingDelete = true` row, **when** the user swipes right, **then** Firestore receives `{deleted: true, deletedAt: now()}` and the row leaves the visible list (`deleted_bookmarks` tombstone present).
- **Given** a `pendingDelete = true` row, **when** the user swipes left, **then** Firestore receives `pending_delete: false`, Room updates `pendingDelete = false`, and the row returns to normal styling.
- **Given** an install on Crumb v1.x with existing data, **when** the upgrade to this release runs the Room migration v9 → v10, **then** the migration succeeds and existing rows survive with `pendingDelete = false`. (MigrationTest assertion.)
- Roborazzi snapshots match (strikethrough rendering in both themes); Maestro `pending_delete_swipe.yaml` passes; MigrationTest passes on a real emulator at verify.
- Brutalist conformance: strikethrough + swipe affordances use design-system primitives (no Material `Surface` ripple leak on swipe). (Closes the `pending-delete` portion of **AC11**; with this slice merged, AC11 + AC7 are fully covered.)

## Dependencies on Other Slices

- `android-reader`: the Firestore reader path, `FirestoreRepository`, and `TwitterBookmarksScreen` rendering scaffolding must already exist; this slice extends them rather than introducing the reader itself.

## Risks

- **Room migration corruption** on a populated user device. Mitigation: MigrationTest covers v9 → v10 with seeded data; pre-merge a manual install-from-v1.x → upgrade walkthrough on the user's primary emulator.
- **Swipe gesture conflict with long-press popup** (existing brutalist long-press from the redesign workflow). Mitigation: long-press takes priority on touch-down; swipe activates only after horizontal-movement threshold (>32dp) and no recent long-press; Maestro flow exercises both paths.
- **Offline swipe writes** queue indefinitely in Firestore SDK. Mitigation: documented in plan; Room update is immediate and the visible state is correct regardless of when Firestore ACKs.
- **Test-tag collisions** with the existing card test tags from the brutalist redesign. Mitigation: reuse the redesign's tag conventions (`bookmark-card[<id>]`, child tags like `bookmark-card-strikethrough`); plan stage lists every new tag.
- **Brutalist contract drift** — strikethrough must be ink-stroked, not a stock `TextDecoration.LineThrough` Material default. Mitigation: lift the strike treatment from `02c-craft.md` of the redesign workflow if present, or apply the existing `CrumbsTheme` stroke conventions.
