---
review-command: maintainability
review-round: 2
scope: slug-wide (git diff main...HEAD)
slug: brutalist-redesign
completed: 2026-05-18
verdict: APPROVE_WITH_COMMENTS
prior-round-ref: 07-review-maintainability.md
---

# Maintainability Review (Round 2) — brutalist-redesign

**Scope:** Re-verification of Round-1 maintainability fixes (MAINT-01 popup factory, MAINT-03/SUPPLY-05 catalog centralization, MAINT-04 `BannerState.source`, MAINT-05 `RedditPostData.toBookmark` dedup) plus drive-by maintainability scan of code introduced by the fix sequence — `refreshTokenSingleFlight` (Twitter/Reddit), `CrumbApplication.ImageLoaderFactory`, and growth of `DatabaseModule.kt`.
**Reviewer:** Maintainability Agent (Round 2)
**Date:** 2026-05-18

---

## Summary

The Round-1 maintainability blockers/highs targeted in commits `dd4a169`, `4d9634c`, `e97ee5f`, and `9dfb119` are all correctly remediated at the code level:

- **MAINT-01** — `bookmarkPopupActions(...)` factory + `rememberLongPressState()` exist in `CrumbsLongPressPopup.kt`; all three Routes call the factory. Adding a fifth popup action is now a single edit. (Verified via `e97ee5f`.)
- **MAINT-03 / SUPPLY-05** — Hilt 2.59.2 and Arrow-Optics now resolve through `libs.versions.toml`; the five module `build.gradle`s import `libs.hilt.android` / `libs.hilt.compiler` / `libs.arrow.optics`. (Verified via `dd4a169`.)
- **MAINT-04** — `BannerState.source: BookmarkSource` is now typed as the `core/models` enum. The previous `else -> Unit` silent fallthrough in `HomeRoute` is now exhaustive. (Verified via `e97ee5f`.)
- **MAINT-05** — Single `fun RedditPostData.toBookmark(...)` in `feature/reddit/screens/RedditBookmarksScreen.kt:244`; `AllBookmarksScreen.kt` imports it. (Verified via `4d9634c`.)

Drive-by scan of the fix sequence surfaces six new maintainability findings, none above MED. The two most actionable items are the new shape of the `refreshTokenSingleFlight` duplication between Twitter and Reddit Repositories (R2-MAINT-01) and the incomplete catalog migration that left coil, media3, and Roborazzi plugin versions inline across multiple modules (R2-MAINT-02).

**Severity Breakdown:**
- BLOCKER: 0
- HIGH: 0
- MED: 3 (R2-MAINT-01 refresh duplication; R2-MAINT-02 catalog migration incomplete; R2-MAINT-03 `DatabaseModule.kt` migration sprawl)
- LOW: 2 (R2-MAINT-04 ImageLoader magic constants; R2-MAINT-05 `filterCount` dead state)
- NIT: 1 (R2-MAINT-06 root `kotlin_version` ext unused)

**Merge Recommendation:** APPROVE_WITH_COMMENTS

---

## Round-1 Fix Validation

| ID | Round-1 severity | Round-1 commit | Round-2 status | File:Line | Notes |
|----|------------------|----------------|----------------|-----------|-------|
| MAINT-01 (popup factory) | HIGH | `e97ee5f` | **Verified fixed** | `core/designsystem/.../CrumbsLongPressPopup.kt` (`LongPressState`, `rememberLongPressState`, `bookmarkPopupActions`) | All three Routes call the factory; per-route lambdas keep `softDelete` source-correct. |
| MAINT-03 / SUPPLY-05 (Hilt + Arrow catalog) | MED | `dd4a169` | **Verified fixed (partial — see R2-MAINT-02)** | `gradle/libs.versions.toml:13,63-66`; all 5 build.gradle files | Hilt and Arrow now centralized. `core/data/build.gradle` (newly added) directly uses `libs.hilt.*`. **Caveat:** `hilt-navigation-compose` still drifts (catalog says 1.0.0; `feature/reddit` inlines 1.1.0) — Round-1 MAINT-03 called this out and the fix did not address it. |
| MAINT-04 (`BannerState.source` typed) | MED | `e97ee5f` | **Verified fixed** | `core/data/.../BannerState.kt:5-10` | `source: BookmarkSource` (enum). `HomeRoute.kt` `when` over the source is now exhaustive (`when(activeBanner?.source) { Twitter -> ...; Reddit -> ...; null -> Unit }`). |
| MAINT-05 (`RedditPostData.toBookmark` dedup) | MED | `4d9634c` | **Verified fixed** | `feature/reddit/.../RedditBookmarksScreen.kt:244` (public top-level extension); `AllBookmarksScreen.kt:44` imports it. | Mirrors the Twitter approach. Comment block at `AllBookmarksScreen.kt:314-315` documents the cross-module reference. |

---

## New Findings

### R2-MAINT-01: `refreshTokenSingleFlight` is duplicated across Twitter and Reddit Repositories [MED]

**Location:**
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/data/Repository.kt:52-54, 283-316`
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/data/RedditRepository.kt:41-43, 210-239`

**Category:** Duplication / Change Amplification
**Severity:** MED | **Confidence:** High

**Evidence:** Both repositories carry essentially the same scaffolding — a `private val refreshMutex = Mutex()` plus a `suspend fun refreshTokenSingleFlight(currentRefreshToken: String): Boolean` body that opens with `if (!refreshMutex.tryLock()) { Timber.d("...deferring"); return true }`, runs the provider-specific refresh call, and closes with `catch (e: Exception) { Timber.e(e, "...exception during refresh") } finally { refreshMutex.unlock() }`. The only divergence is the body of the `try { ... }` block (Twitter handles a `TokenResponse?` and calls `authPref.setAccessAndRefreshToken`; Reddit handles a `String?` because `RedditAuthClient.refreshAccessToken` persists internally).

**Impact:**
- Already-drifted Timber tags: Twitter logs `"token refreshed and persisted"`; Reddit logs `"Reddit token refreshed"`. Indicates the two implementations are diverging at the comment/log-message layer already.
- The Reliability Round-2 finding R2-REL-01 ("tryLock false-success") will need to be applied to both files identically.
- A future requirement — exponential backoff, retry budget, metric emission — must be coded twice.

**Change scenario:**
> Q: Add a retry budget so a flapping refresh endpoint stops storming requests after 3 failures.
> A: Two synchronized edits in two repositories, with no compiler help if the implementations drift.

**Smallest Fix:**
Extract a `Mutex` extension or top-level helper in `core/data` that owns the lock/log/finally scaffolding:

```kotlin
// core/data/auth/RefreshSingleFlight.kt
suspend fun Mutex.runSingleFlightRefresh(
    tag: String,
    block: suspend () -> Boolean,
): Boolean {
    if (!tryLock()) {
        Timber.d("$tag: another refresh in flight, deferring")
        return true
    }
    return try {
        block()
    } catch (e: Exception) {
        Timber.e(e, "$tag: exception during refresh")
        false
    } finally {
        unlock()
    }
}
```

Both repository implementations shrink to a 5-line lambda; the per-provider differences live in the lambda body.

**Cost/benefit:** ~15 min, low risk. Same cost story as the Round-1 popup factory: the scaffolding is provably identical, the bodies provably differ; the extraction is mechanical.

---

### R2-MAINT-02: Catalog migration did not absorb coil, media3, Roborazzi plugin, or hilt-nav-compose drift [MED]

**Location:** Multiple `build.gradle` files (see Evidence)
**Category:** Configuration Duplication / Change Amplification
**Severity:** MED | **Confidence:** High

**Evidence:** Per the prompt's question — a grep for inline-pinned `implementation "<group>:<artifact>:<version>"` across all 8 `build.gradle` files surfaces these leftovers after `dd4a169`:

| Coordinate | File:Line | Drift? |
|---|---|---|
| `io.coil-kt:coil-compose` | `core/designsystem/build.gradle:61` (`2.5.0`); `app/build.gradle:122` (`2.2.2`); `feature/twitter/build.gradle:93` (`2.2.2`) | **Yes — 2.5.0 vs 2.2.2** |
| `com.github.Commit451.coil-transformations[-gpu]` | `app:123-124` (`2.0.2`), `feature/twitter:94-95` (`2.0.2`) | No drift, duplicated 1:1 |
| `androidx.media3:media3-*` | `core/designsystem:64-67` (`1.2.0`); `app:133-134` (`1.0.0-beta02`); `feature/twitter:108-109` (`1.0.0-beta02`) | **Yes — 1.2.0 stable vs 1.0.0-beta02 (2022 beta)** |
| `androidx.datastore:datastore-preferences` | `app:119` (`1.0.0`); `core/pref:39` (`1.0.0`) | No drift, duplicated 1:1 |
| `androidx.hilt:hilt-navigation-compose` | `feature/reddit:65` (`1.1.0`); catalog says `1.0.0` | **Yes — 1.0.0 catalog vs 1.1.0 reddit inline** (Round-1 MAINT-03 flagged this and the dd4a169 fix did not address it) |
| `androidx.appcompat:appcompat:1.5.1`, `com.google.android.material:material:1.7.0`, `androidx.core:core-ktx:1.8.0` | `feature/reddit:49-51` | Module-creation boilerplate; `material` (XML) is unused in this Compose-only module (Round-1 MAINT-12 already flagged) |
| Roborazzi plugin | `app/build.gradle:8`, `feature/twitter:6`, `feature/reddit:6`, `core/designsystem:4` — all `version '1.60.0'` inline | Plugin version pinned in 4 places; catalog has `roborazzi = "1.60.0"` for the libraries but not the plugin |

**Impact:**
- Two versions of Coil (2.2.2 / 2.5.0) and media3 (1.0.0-beta02 / 1.2.0) ship to one APK; Gradle resolves the higher version at link time but the duplication is invisible until something breaks.
- The Round-1 MAINT-03 finding explicitly called out `hilt-navigation-compose:1.1.0 (reddit) vs hiltNavigationCompose=1.0.0 (catalog)` as drift. The `dd4a169` commit fixed the main Hilt drift but missed this sibling.
- Upgrading Roborazzi requires touching four module files identically.

**Change scenario:**
> Q: Upgrade Coil to 2.6.0 to pick up CVE-XXX.
> A: Three module files; catalog cannot help because Coil isn't in it. Easy to miss one.

**Smallest Fix:**
1. Add catalog entries:
```toml
[versions]
coil = "2.5.0"
media3 = "1.2.0"
coilTransformations = "2.0.2"
roborazziPlugin = "1.60.0"

[libraries]
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
coil-transformations = { module = "com.github.Commit451.coil-transformations:transformations", version.ref = "coilTransformations" }
coil-transformations-gpu = { module = "com.github.Commit451.coil-transformations:transformations-gpu", version.ref = "coilTransformations" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-exoplayer-dash = { module = "androidx.media3:media3-exoplayer-dash", version.ref = "media3" }
media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }

[plugins]
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazziPlugin" }
```
2. Update `libs.versions.toml:37` so `hiltNavigationCompose` matches the inline `1.1.0`.
3. Apply the Roborazzi plugin via `alias(libs.plugins.roborazzi) apply false` at the root and `alias(libs.plugins.roborazzi)` per consuming module — four lines collapse to one catalog ref.
4. Drop `appcompat:appcompat:1.5.1` and `material:material:1.7.0` from `feature/reddit/build.gradle:50-51` (Round-1 MAINT-12 callout).

---

### R2-MAINT-03: `DatabaseModule.kt` is becoming a migration grab-bag [MED]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/di/DatabaseModule.kt:1-204`
**Category:** Cohesion
**Severity:** MED | **Confidence:** High

**Evidence:** The file is 204 lines and now mixes three concerns:
1. The Hilt `@Module` (`provideAppDatabase` + 3 DAO providers, lines 18-140).
2. Two **private** `Migration` instances inside the module class (`MIGRATION_2_3`, `MIGRATION_3_4`).
3. Four **public top-level** `Migration` vals, declared after the class, in non-sequential order: `MIGRATION_4_5` (line 142), `MIGRATION_7_8` (line 163), `MIGRATION_6_7` (line 178), `MIGRATION_5_6` (line 185).

The mixed visibility (`private val` inside class vs public top-level `val`), mixed location (class member vs top-level), and out-of-order top-level decls are pure historical accretion — each round-1 fix landed a new migration at the bottom of the file rather than next to its siblings. The file's intent — "set up Room" — is now buried under 150 lines of migration SQL.

**Change scenario:**
> Q: Add MIGRATION_8_9 for a tag table renaming. Where does it go?
> A: Either inside the `DatabaseModule` class as a private val (consistent with 2_3 and 3_4) or as a public top-level val (consistent with 4_5/5_6/6_7/7_8). The current file gives the contributor no guidance, and `addMigrations(...)` requires both to be threaded.

**Smallest Fix:**
Extract migrations to their own file. Group by responsibility — DI module knows about the database, migrations know about schema deltas:

```kotlin
// app/.../db/Migrations.kt — one file, ordered
internal val MIGRATION_2_3 = object : Migration(2, 3) { ... }
internal val MIGRATION_3_4 = object : Migration(3, 4) { ... }
internal val MIGRATION_4_5 = object : Migration(4, 5) { ... }
internal val MIGRATION_5_6 = object : Migration(5, 6) { ... }
internal val MIGRATION_6_7 = object : Migration(6, 7) { ... }
internal val MIGRATION_7_8 = object : Migration(7, 8) { ... }

internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
)

// DatabaseModule.kt — shrinks to ~40 lines
.addMigrations(*ALL_MIGRATIONS)
```

**Alternative (smaller refactor):** Keep migrations in `DatabaseModule.kt` but make every migration top-level, in ascending order, with consistent visibility. The `private val` versions inside the class are inaccessible to `MigrationTest` from `androidTest/` — if any of them needs cross-source-set access (likely once a migration test gets added for 2_3 or 3_4), they'll have to be promoted anyway.

**Why it matters:**
- 6 migrations and counting; this file will grow ~30-100 lines per migration depending on complexity.
- A reviewer hunting "what does v5→v6 do?" has to scroll past 4_5, 7_8, 6_7 to find 5_6.
- The mixed visibility creates a subtle invariant — only the older migrations are private — that nobody documented.

---

### R2-MAINT-04: `ImageLoaderFactory` config has magic numbers and a dead call [LOW]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/CrumbApplication.kt:22-39`
**Category:** Naming / Magic Numbers
**Severity:** LOW | **Confidence:** High

**Evidence:**
```kotlin
override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
    .crossfade(true)
    .crossfade(180)                                    // ms — what does this mean?
    .memoryCache {
        MemoryCache.Builder(this).maxSizePercent(0.20).build()  // 20% of what?
    }
    .diskCache {
        DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizePercent(0.02).build()              // 2% of what?
    }
    ...
```

Three numeric literals encode tuning decisions: crossfade duration (ms), memory cache percentage of heap, disk cache percentage of free disk. None are named. Additionally, `.crossfade(true)` followed by `.crossfade(180)` is a no-op of the first call (the duration overload replaces the boolean), so one of the two lines is dead.

**Smallest Fix:**
```kotlin
private companion object {
    const val IMAGE_CROSSFADE_MS = 180
    const val IMAGE_MEMORY_CACHE_PCT = 0.20
    const val IMAGE_DISK_CACHE_PCT = 0.02
}

override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
    .crossfade(IMAGE_CROSSFADE_MS)
    .memoryCache {
        MemoryCache.Builder(this).maxSizePercent(IMAGE_MEMORY_CACHE_PCT).build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizePercent(IMAGE_DISK_CACHE_PCT).build()
    }
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .respectCacheHeaders(false)
    .build()
```

**Optional build-config promotion:** If tuning the cache percentage per build variant is a foreseeable need (e.g. lower disk-cache budget in low-end builds, larger in release), the constants belong in `buildConfigField "double"` so they vary by build type. As written they're tied to the runtime device's heap/disk, which is already adaptive, so plain `const val` is probably sufficient.

**Why it matters:** Tuning is a profile-driven activity. Greppable named constants let the next perf pass adjust them without re-deriving "is `0.20` heap or disk?" from context.

---

### R2-MAINT-05: `HomeUiState.filterCount` is documented dead state [LOW]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt:30-34`, call site in `HomeRoute.kt`
**Category:** Naming / Dead Code
**Severity:** LOW | **Confidence:** High

**Evidence:** Round-1 CS-4 asked the team to wire the field or remove it. Round-1 chose option (3) — keep the field, paste a 4-line comment explaining why:
```kotlin
// Count badge value displayed in the filter bar. Currently unwired — the
// active VM does not surface a per-tab total Flow yet, so HomeRoute hands
// through 0. Kept as a first-class field so the wiring can land in a
// follow-up without re-threading the HomeScreen signature.
val filterCount: Int = 0,
```
The comment is honest about the gap. But:
- No ticket reference. A reader cannot find the follow-up.
- The user sees "000" badge as a permanently incorrect display value.
- The "stable surface" rationale assumes a follow-up exists, but the artifact at `07-review.md:266` records this as `Documented` with no follow-up commit or workflow.

**Smallest Fix:**
Two viable options:
1. **Close the loop** — add a `TODO(ticket-id)` reference in the comment. Even an internal ticket id is enough provenance.
2. **Remove the field** — `HomeUiState` loses `filterCount`, `CrumbsFilterBar` makes the `count` param `Int? = null` (omit cell when null), `HomeRoute` drops the pass-through.

The "avoid re-threading `HomeScreen`" argument is weak — adding back a single property to a data class is a one-line, mechanical change with full IDE migration support.

**Why it matters:**
A field whose only behaviour is to make the UI lie is a maintenance liability disguised as forward-thinking design.

---

### R2-MAINT-06: Root `buildscript { ext { kotlin_version } }` is dead config [NIT]

**Location:** `build.gradle:1-7`
**Category:** Dead Code / Comments
**Severity:** NIT | **Confidence:** High

**Evidence:**
```groovy
buildscript {
    ext {
        kotlin_version = '2.2.10'
        // All other versions (compose, room, hilt, retrofit, lifecycle, etc.)
        // live in gradle/libs.versions.toml as the single source of truth.
    }
}
```
`kotlin_version` is declared but never referenced. The Kotlin Compose plugin and KSP plugin versions are hard-coded inline in the same file's `plugins { }` block (`'2.2.10'`, `'2.2.10-2.0.2'`).

**Smallest Fix:**
Delete the entire `buildscript { ext { ... } }` block. The comment about catalog being the source of truth becomes self-deprecating once the unused ext is gone.

**Why it matters:** A root `build.gradle` should be a single source of truth itself. Carrying an unused version variable contradicts the comment immediately below it.

---

## Findings Summary

| ID | Sev | Conf | Category | File:Line | Issue |
|----|-----|------|----------|-----------|-------|
| R2-MAINT-01 | MED | High | Duplication / Change Amplification | Twitter `Repository.kt:283-316` + Reddit `RedditRepository.kt:210-239` | `refreshTokenSingleFlight` lock/log/finally scaffolding duplicated; extract to shared `Mutex` helper |
| R2-MAINT-02 | MED | High | Config Duplication | 8 build.gradle files | Catalog migration incomplete — coil (2 versions), media3 (2 versions), Roborazzi plugin (×4), hilt-nav-compose drift |
| R2-MAINT-03 | MED | High | Cohesion | `DatabaseModule.kt:1-204` | 6 migrations now in one file with mixed visibility, mixed location, non-sequential order; extract to `Migrations.kt` |
| R2-MAINT-04 | LOW | High | Naming / Magic Numbers | `CrumbApplication.kt:22-39` | ImageLoader: 3 unnamed numeric tuning knobs + no-op `.crossfade(true)` line |
| R2-MAINT-05 | LOW | High | Dead Code | `HomeScreen.kt:30-34` | `filterCount` documented but never wired; UI shows "000" permanently; no ticket reference |
| R2-MAINT-06 | NIT | High | Dead Code | `build.gradle:3` | Root `kotlin_version` ext is never read |

---

## Change Amplification (Round 2)

Re-running the Round-1 scenarios against the patched code:

### Scenario A: Add an "ARCHIVE" popup action across all bookmark lists

- **Round 1:** Three Route composables × 50-line inline `persistentListOf(...)` = 3 edit sites.
- **Round 2:** **1 edit site** — `bookmarkPopupActions(...)` in `CrumbsLongPressPopup.kt` gains a new optional parameter; the three Routes pass an additional lambda. Verified MAINT-01 fix delivers the promised amplification reduction.

### Scenario B: Tighten the single-flight refresh contract (e.g., retry budget, metrics)

- **Round 1:** N/A (refresh path did not exist).
- **Round 2:** **2 edit sites** — Twitter `Repository.refreshTokenSingleFlight` and Reddit `RedditRepository.refreshTokenSingleFlight`. R2-MAINT-01 collapses to 1 edit if the extraction lands.

### Scenario C: Upgrade Hilt 2.59.2 → 2.60

- **Round 1:** 4 build.gradle files.
- **Round 2:** **1 edit site** — `libs.versions.toml:13` `hilt = "..."`. Verified MAINT-03 fix delivers full reduction. (But upgrading Coil or media3 still requires 2-3 edits — see R2-MAINT-02.)

### Scenario D: Add a third bookmark source (e.g., Pocket)

- **Round 1:** 5+ files + 3 duplicated popup lists + silent BannerState fallthrough.
- **Round 2:** 5 files + 0 popup duplications + exhaustive `when` over `BookmarkSource` enum (compiler enforces all branches). The cross-feature `feature/reddit → feature/twitter` Gradle dep is gone (B1 fix), so a new `feature/pocket` would not have to inherit the load-bearing coupling.

---

## Recommendations

### Should Fix (MED)
- **R2-MAINT-01** — Extract `Mutex.runSingleFlightRefresh(tag, block)` helper. ~15 min.
- **R2-MAINT-02** — Finish the catalog migration: add `coil`, `media3`, `coil-transformations`, `roborazzi` (plugin); align `hilt-navigation-compose`; drop appcompat/material XML deps from `feature/reddit`. ~30 min total.
- **R2-MAINT-03** — Split `DatabaseModule.kt` into `Migrations.kt` (or at minimum, reorder + harmonize visibility). ~15 min.

### Consider (LOW)
- **R2-MAINT-04** — Replace 3 magic numbers in `ImageLoader.Builder` with named `const val`s; drop the dead `.crossfade(true)` line. ~5 min.
- **R2-MAINT-05** — Either close the loop with a tracked ticket reference on the `filterCount` comment, or remove the field and its UI surface. ~10 min.

### Drop (NIT)
- **R2-MAINT-06** — Delete unused `kotlin_version` ext. ~1 min.

**Estimated total effort for MED + LOW + NIT:** ~80 min.

---

## Conventions & Consistency (Round 2)

Round-1 conventions are still upheld:
- Screen/Route split — consistent across all 4 screens.
- ViewModel state flows — `_camelCase` private + public `camelCase`.
- Design token access via `LocalCrumbs*` — uniform.
- Catalog references in build.gradle — **partially** uniform; see R2-MAINT-02 for the holes.

**New convention emerging:** Both Twitter and Reddit Repositories now follow a "tryLock + Timber tag + try/catch/finally" refresh pattern. Codifying this as a shared helper (R2-MAINT-01) would turn an emerging pattern into an enforced one.

---

## Positive Observations (Round 2)

- **MAINT-04 done right.** Typed `BannerState.source: BookmarkSource` lets `HomeRoute`'s `when` become compiler-exhaustive.
- **MAINT-05 mirrors Twitter.** Public top-level extension in `feature/reddit`, with inline comment at the `AllBookmarksScreen.kt` reference site.
- **MAINT-01 is genuinely DRY.** `bookmarkPopupActions` is a plain function, no compose-state leak through the helper.
- **Catalog migration is right-shaped.** Hilt and Arrow centralized, new `core/data/build.gradle` uses catalog refs from the start. R2-MAINT-02 is about completeness, not direction.

---

## False Positives & Disagreements Welcome

1. **R2-MAINT-01 (refresh duplication):** If `Repository` and `RedditRepository` should drift over time (Reddit grows retry budget, Twitter doesn't), the duplication is intentional scaffolding.
2. **R2-MAINT-02 (media3 beta):** Older media3 may be a known-good pin. A pinning comment would close the loop.
3. **R2-MAINT-03 (migration sprawl):** Splitting migrations requires promoting them to `internal` for `MigrationTest` cross-source-set access. Mechanical but worth knowing.
4. **R2-MAINT-05 (filterCount):** If `BookmarksViewModel.totalCount: Flow<Int>` is close to landing, "stable surface" is reasonable. No evidence of it in the artifact history.

---

*Review completed: 2026-05-18*
*Session: brutalist-redesign*
