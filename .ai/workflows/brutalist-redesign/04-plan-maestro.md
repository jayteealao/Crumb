---
schema: sdlc/v1
type: plan
slug: brutalist-redesign
slice-slug: maestro
status: complete
stage-number: 4
created-at: "2026-05-18T06:44:23Z"
updated-at: "2026-05-18T06:44:23Z"
metric-files-to-touch: 14
metric-step-count: 18
has-blockers: false
revision-count: 0
tags: [maestro, e2e, testing, lazylogcat, android-cli, debug-source-set]
stack-source: confirmed
refs:
  index: 00-index.md
  plan-index: 04-plan.md
  slice-def: 03-slice-maestro.md
  siblings:
    - 04-plan-toolchain.md
    - 04-plan-tokens.md
    - 04-plan-components.md
    - 04-plan-layouts.md
    - 04-plan-screens.md
    - 04-plan-behaviors.md
  implement: 05-implement-maestro.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign maestro"
---

# Plan: maestro — End-to-end Maestro coverage

## Current State

The behaviors slice landed on `feat/brutalist-redesign` at commit `a01f79f3` (plus the verify-owned `47ee1b78` test fix), wiring long-press → soft-delete + tombstone, sync-error banner, filter chips, and the per-tab filter VM ownership. The codebase now has every UI surface the four Maestro flows need to address. Specifically:

- **60 testTags wired across 16 files** (full inventory in `## Likely Files / Areas to Touch`). Every testTag uses **kebab-case with dashes** (e.g., `home-scaffold`, `bookmark-card`, `popup-action-${id}`, `login-skip-auth`). The dash-compatibility question with `testTagsAsResourceId = true` is unresolved — best-practice docs say Android resource-name rules forbid dashes, but no empirical test against this app exists. **Plan step 1 resolves this with a probe** before any flow work proceeds.
- **`testTagsAsResourceId = true` already enabled** at `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt:40` (root Box, all descendants inherit). The Compose-side scaffold is complete.
- **`login-skip-auth` button** already shipped at `app/src/main/java/com/github/jayteealao/crumbs/screens/LoginScreen.kt:163` from the compressed `quick-skip-auth-page` slice. Tapping it flips `isAccessTokenAvailable` true and triggers nav to HomeScreen without OAuth. This is `happy_path.yaml`'s entry point — no OAuth handling needed in flows.
- **`Medium_Phone_API_36` AVD** is the established verification target across prior slices (replaces slice-spec literal "Pixel 6 API 34"). 18 active runtime-evidence-deferrals across the prior 6 slices collapse onto this slice's probe runs.

The repo has **no `README.md`**, **no `scripts/` directory**, **no `app/src/debug/` source set**, and **no `maestro/` directory**. Every artifact this slice produces is net-new — no migrations, no rewrites. AppDatabase ships at v5 with `DeletedBookmark` + 13 other entities; DAOs surface `insertTweet`, `insertPost`, `insertTag`, `insertTweetTag` for the debug seeder. `MainActivity.onCreate` does not currently parse intent extras — adding that hook is in-scope.

The `gradle/libs.versions.toml` has no `maestro-*` declarations (Maestro is a CLI tool, not a Gradle dep — confirmed correct). `gradle.properties` has no debug-flavor config. Existing `app/build.gradle` has `debugImplementation(libs.compose.uiToolingDebug)` + `debugImplementation(libs.compose.uiTestManifest)` and an implicit `buildTypes { debug }` block — sufficient infrastructure for `app/src/debug/` to compile.

`.github/workflows/` exists with `manual-release.yml`, `pr_check.yml`, `release.yml`. **No CI Maestro integration** lands in this slice (PO Round 1 Q3: local-only).

## Reuse Opportunities

From parallel Explore sub-agent 1's affected-code deep dive:

- **`Modifier.semantics { testTagsAsResourceId = true }` scaffold** → `CrumbsTheme.kt:40` — **reuse as-is**. No re-wiring needed; descendants already expose every testTag via Android resource ID semantics.
- **`login-skip-auth` testTag at LoginScreen.kt:163** → **reuse as-is** for `happy_path.yaml` Login → Home traversal. Replaces the slice-spec's risky "drive real OAuth browser flow" mitigation entirely.
- **All ~60 component/screen testTags** → **reuse as-is** (probe-pending). Inventory:
  - `home-scaffold`, `home-scaffold-topbar`, `home-scaffold-banner`, `home-scaffold-filterbar`, `home-scaffold-bottombar` — `HomeScaffold.kt:47–75`
  - `bottom-nav`, `nav-tab-${tab.name.lowercase()}` (twitter/reddit/all/map) — `CrumbsBottomNav.kt:55,76`
  - `bookmark-card`, `card-source`, `card-title`, `card-actions` — `CrumbsBookmarkCard.kt:76–195`
  - `popup`, `popup-action-${action.id}` (tag/open/share/delete) — `CrumbsLongPressPopup.kt:104,173`
  - `snackbar`, `snackbar-action` — `CrumbsSnackbar.kt:50,68`
  - `banner`, `banner-cta` — `CrumbsBanner.kt:44,79`
  - `filter-bar`, `filter-bar-count`, `filter-bar-chip-${chip.id}`, `filter-bar-sort` — `CrumbsFilterBar.kt:66–127`
  - `overlay-shell`, `overlay-shell-backdrop`, `overlay-shell-apply` — `OverlayShell.kt:59–102`
  - `splash-screen`, `splash-wordmark` — `SplashScreen.kt:37,44`
  - `login-screen`, `login-skip-auth`, `login-twitter-cta`, `login-reddit-cta` — `LoginScreen.kt:64–163`
  - `twitter-bookmarks-screen`, `twitter-bookmarks-feed`, `twitter-bookmarks-empty` — `TwitterBookmarksScreen.kt:81–96`
  - `reddit-bookmarks-screen`, `reddit-bookmarks-feed`, `reddit-bookmarks-empty` — `RedditBookmarksScreen.kt:73–86`
  - `all-bookmarks-screen`, `all-bookmarks-feed`, `all-bookmarks-empty` — `AllBookmarksScreen.kt:88–101`
  - `home-screen` — `HomeScreen.kt:58`
- **`AppDatabase.kt` + DAO surface** → **reuse as-is** for `DebugDataInjector`. Inserts go through `tweetDao()`, `redditDao()`, `tweetDao().insertTag()`, `tweetDao().insertTweetTag()`. No new DAO methods needed; the existing per-table inserts cover all 13 affected entities for the 8-bookmark + 3-tag + 2-collection-as-tag-set seed.
- **`MigrationTest.kt` `MigrationTestHelper` pattern** at `app/src/androidTest/java/com/github/jayteealao/crumbs/db/MigrationTest.kt` → **reuse as template** for `DebugDataInjectorTest.kt` (the seed-verification instrumentation test). Direct `Room.databaseBuilder()` access without Hilt injection; matches the project's established androidTest pattern.
- **`androidx.test.runner.AndroidJUnitRunner`** (already configured at `app/build.gradle:35`) → **reuse as-is** for the new instrumentation tests.
- **`lazylogcat` skill** at `~/.claude/skills/lazylogcat/` → **reuse via skill invocation** in the orchestration script. Commands: `lazylogcat logs dump --pkg com.github.jayteealao.crumbs > build/maestro-logs/<timestamp>.log`. `lazylogcat` is also a public CLI (`parfenovvs/lazylogcat`) for documentation cross-link.
- **`android-cli` skill** → **reuse for AVD orchestration** (`android emulator start Medium_Phone_API_36`, `android emulator stop`).

**No reuse candidate for:** Cross-platform script convention (only `gradlew` + `gradlew.bat` exist; new `scripts/` directory establishes the convention).

## Likely Files / Areas to Touch

**New files (12):**

1. `maestro/_probe.yaml` — 5-line probe flow. Confirms `tapOn: id: home-scaffold` resolves under `testTagsAsResourceId = true`. Removed at end of slice unless promoted to a smoke flow.
2. `maestro/happy_path.yaml` — full nav walk (splash → onboarding optional → login-skip → twitter → reddit → all → map → return → long-press → 4 actions → filter chip toggle → tags overlay).
3. `maestro/long_press.yaml` — focused 4-action popup verification (TAG / OPEN / SHARE / DELETE + backdrop dismiss).
4. `maestro/filter_overlay.yaml` — Type instant + Tags overlay multi-select + Collection overlay + APPLY + backdrop cancel. *(Tags overlay scope hedged — see `## Blockers`.)*
5. `maestro/sync_error.yaml` — force Twitter 401 → banner appears → tap `banner-cta` → OAuth intent fires.
6. `app/src/debug/AndroidManifest.xml` — empty `<manifest>` shell (allows `src/debug/` to compile; no permission/activity overrides).
7. `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt` — `@Singleton class DebugDataInjector @Inject constructor(db: AppDatabase, twitterPref: AuthPref, redditPref: RedditPrefs)` with `suspend fun run(wipe: Boolean)`. Seeds 4 Twitter + 4 Reddit bookmarks + 3 tags + 2 "collection" tags via standard DAO inserts; optionally writes fake-but-valid-format access tokens.
8. `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt` — small object with `handleIntent(activity, intent)` reading `intent.getStringExtra("debug_action")` + `intent.getBooleanExtra("wipe", false)` and dispatching to `DebugDataInjector.run(wipe)` via `lifecycleScope.launch`.
9. `app/src/androidTest/java/com/github/jayteealao/crumbs/debug/DebugDataInjectorTest.kt` — instrumentation test asserting post-seed DB contains 4 + 4 bookmarks + 3 + 2 tags.
10. `scripts/run-maestro.ps1` — PowerShell primary script (Windows-first dev). Boots AVD → installs `app-debug.apk` → starts `lazylogcat` background → runs `maestro test maestro/happy_path.yaml maestro/long_press.yaml maestro/filter_overlay.yaml maestro/sync_error.yaml` → dumps logs → stops AVD.
11. `scripts/run-maestro.sh` — bash sibling (POSIX). Same flow as `.ps1` adjusted for `bash`/Git Bash invocation on Windows + macOS/Linux.
12. `README.md` — new top-level README (minimal). Project description + build commands + test commands + Maestro section + lazylogcat reference + AVD profile.

**Modified files (3):**

13. `app/build.gradle` — adds (a) `verifyReleaseDebugInjectorAbsent` Gradle task depending on `assembleRelease` that unzips `app-release.apk` and `dexdump`-greps for `DebugDataInjector`, fails if found; (b) test source set adds `app/src/androidTest/.../debug/` if not already covered by existing `srcDirs`. No `applicationIdSuffix` or `versionNameSuffix` change.
14. `app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt` — adds debug-only intent dispatch. Strategy: a `try/catch ClassNotFoundException` reflective load of `com.github.jayteealao.crumbs.debug.DebugIntentHandler` inside `onCreate` + `onNewIntent`. Release builds throw CNFE silently; debug builds invoke the handler. **Alternative considered & rejected**: a `CrumbApplication` subclass in `app/src/debug/` would require renaming `app/src/main/.../CrumbApplication.kt` or splitting the `<application android:name>` attribute by source set — touches release surface unnecessarily. Reflection keeps the release `MainActivity.onCreate` bytecode identical to before the slice modulo a single try/catch.
15. `CHANGELOG.md` — v2.0 entry already drafted in behaviors slice's pending edits (will be finalized at handoff). This slice **does not modify CHANGELOG.md** — the v2.0 entry's Maestro line is added in the handoff aggregation step, not here.

**Not touched:**

- No `.github/workflows/*.yml` files (CI deferred per PO Round 1 Q3).
- No `feature/twitter/`, `feature/reddit/`, or `core/*/` source files. The 401-forcing mechanism for `sync_error.yaml` is the existing OAuth-token tampering path: scripts will `adb shell` to write an obviously-invalid token via `am broadcast` against a debug-only `AuthCorruptorReceiver` — but on inspection, the cleaner path is to use Maestro's `runScript` to invoke `adb` directly. **Plan defers exact 401-trigger mechanism to a Step 12 spike**; if cleanest path needs a new debug receiver, the receiver is added in `app/src/debug/`, keeping the no-feature-module-touch contract.
- No new `core/data` module work — the cross-module shared types from the behaviors slice are sufficient.

## Proposed Change Strategy

**Single atomic commit** at slice end (`implement-stage` contract), but the work is staged across 4 phases to keep merge-rebase manageable should issues surface:

**Phase A — Probe & rename audit (steps 1–2).** Land the testTag round-trip empirically before any other Maestro work. If dashes work, Phase B starts unblocked. If not, a focused audit-and-rename across the 16 testTag-bearing files becomes Phase A.5 (~1 hour of mechanical work). All Roborazzi/unit tests that assert against testTag names are updated in lockstep. The probe artifact (`_probe.yaml`) is preserved as a 1-line smoke flow at the end of `happy_path.yaml`'s preamble.

**Phase B — Debug source set + injector (steps 3–6).** Build the `app/src/debug/` infrastructure: AndroidManifest shell, `DebugDataInjector` (DAO-bound, uses existing inserts), `DebugIntentHandler` (intent extras dispatcher), reflective wire-up from `MainActivity`. Add `DebugDataInjectorTest` (instrumentation test verifying seed). Add `verifyReleaseDebugInjectorAbsent` Gradle task; run it once locally to confirm release variant compiles and the assertion passes (releases must NOT contain `DebugDataInjector`).

**Phase C — Maestro flows (steps 7–11).** Author the 4 flows in this order: `happy_path.yaml` (longest, exercises most surface), `long_press.yaml` (focused), `filter_overlay.yaml` (Tags overlay scope hedged per Blocker), `sync_error.yaml` (depends on Step 12 spike resolution). Each flow begins with `launchApp.arguments: { debug_action: "seed", wipe: true }`. Skip-if-state uses `runFlow when: notVisible: id: home-scaffold` → tap `login-skip-auth`. Maestro 2.4 `extendedWaitUntil` with `timeout: 10000` wraps every Compose-recomposition-sensitive assertion (Splash auto-nav 1000ms + LoginScreen visibility delay).

**Phase D — Orchestration & docs (steps 13–18).** Cross-platform scripts (`run-maestro.ps1` primary, `run-maestro.sh` sibling) wrap AVD boot + APK install + `lazylogcat` background capture + Maestro test invocation + log dump + AVD stop. Top-level `README.md` documents the workflow. Final commit and verify-stage gates run.

**Spike note (Step 12):** The exact mechanism for forcing a Twitter 401 in `sync_error.yaml` is **deferred to a single in-implement-stage spike**. Two candidates: (a) write a malformed token via `adb shell content-provider call` against a debug-only authority, (b) extend `DebugIntentHandler` with a `corrupt_token` action that writes `"INVALID"` to AuthPref then triggers a sync. Path (b) is preferred (consistent with the seed-action pattern); spike validates that the sync error surfaces within the 1s SLA the AC requires.

## Step-by-Step Plan

1. **Probe testTag dash compatibility.** Create `maestro/_probe.yaml`:
   ```yaml
   appId: com.github.jayteealao.crumbs
   ---
   - launchApp
   - tapOn:
       id: "login-skip-auth"
   - extendedWaitUntil:
       visible:
         id: "home-scaffold"
       timeout: 10000
   ```
   Boot AVD (`android emulator start Medium_Phone_API_36`), install latest `app-debug.apk`, run `maestro test maestro/_probe.yaml`. **If green:** dashes work, proceed to step 3. **If red with "element not found":** dashes fail, proceed to step 2.

2. **(Conditional) testTag rename audit.** Across all 16 testTag-bearing files (Reuse-Opportunities inventory), replace kebab-case with snake_case (`home-scaffold` → `home_scaffold`, `popup-action-${id}` → `popup_action_${id}`, etc.). Update every Roborazzi/Compose-UI-test assertion that uses these tags. Re-run Roborazzi to confirm no golden drift (testTag changes are semantics-only, no pixel impact). Re-run probe; expect green. Document the rename in this plan's `## Revision History` for verify-stage reference.

3. **Create `app/src/debug/AndroidManifest.xml`.** Empty `<manifest>` shell:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <manifest />
   ```
   This is enough for AGP to recognize the debug source set and link debug-only sources into the debug variant only.

4. **Author `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugDataInjector.kt`.** `@Singleton` injected with `AppDatabase`, `Prefs` (Twitter), `RedditPrefs`. Single `suspend fun run(wipe: Boolean)`:
   - If `wipe`: `withContext(Dispatchers.IO) { db.clearAllTables() }` (Room built-in).
   - Insert 1 fake `TwitterUserEntity` (id `"debug-user-twitter"`, handle `"@crumbs_test"`).
   - Insert 4 fake `TweetEntity` (ids `"debug-tweet-1".."debug-tweet-4"`, varied text + 1 with media via `TweetMediaEntity`).
   - Insert 4 fake `RedditPostEntity` (ids `"debug-post-1".."debug-post-4"`, 1 with `permalink` for link variant + 1 with `media` for video variant).
   - Insert 5 `TagEntity` (`"design"`, `"tech"`, `"finance"`, `"collection-reading-list"`, `"collection-archive"`). The last two prefixed `"collection-"` represent the "Collection as tag-set" mapping established in behaviors PO Round 1.
   - Insert `TweetTagCrossRef` rows associating 2 tweets with tags.
   - Optionally (when called with `seedTokens = true` via a separate action): write a fake-format Twitter access token to AuthPref via `Context.writeString("twitter_access_token", "DEBUG_FAKE_TOKEN")` and a Reddit equivalent. Used only by `sync_error.yaml`'s Step 12 spike; happy_path uses the `login-skip-auth` tap path and does not seed tokens.

5. **Author `app/src/debug/java/com/github/jayteealao/crumbs/debug/DebugIntentHandler.kt`.** Plain `object` (no DI on the entry point since it's invoked reflectively from MainActivity). Exposes `fun handleIntent(activity: ComponentActivity, intent: Intent)`:
   - Read `intent.getStringExtra("debug_action")`. Switch on the value:
     - `"seed"` → resolve `DebugDataInjector` via `EntryPointAccessors.fromApplication(activity.application, DebugInjectorEntryPoint::class.java).debugDataInjector()`, launch `activity.lifecycleScope.launch { injector.run(wipe = intent.getBooleanExtra("wipe", false)) }`.
     - `"corrupt_token"` → invoke `DebugDataInjector.corruptTwitterToken()` (added in step 12 spike if needed).
     - any other → log via Timber and return.
   - The `DebugInjectorEntryPoint` Hilt entry-point interface is also defined in this file.

6. **Wire MainActivity reflective dispatch.** Edit `app/src/main/java/com/github/jayteealao/crumbs/MainActivity.kt`:
   - Add `override fun onNewIntent(intent: Intent)` (since `launchMode` is single-instance-like by default, `onCreate` may or may not see the launch intent; safer to dispatch from both).
   - In both `onCreate` and `onNewIntent`, after the existing setup, attempt `Class.forName("com.github.jayteealao.crumbs.debug.DebugIntentHandler")` inside `try/catch (e: ClassNotFoundException)`. If found, call `handleIntent(this, intent ?: this.intent)` via Java reflection (`getMethod("handleIntent", ComponentActivity::class.java, Intent::class.java).invoke(null, this, intent)`). Catch and Timber-log any reflective failure; never propagate. This keeps the release `onCreate` bytecode-stable (one extra try/catch, no debug reference).

7. **Add `DebugDataInjectorTest`** at `app/src/androidTest/java/com/github/jayteealao/crumbs/debug/DebugDataInjectorTest.kt`. **Important constraint**: the test class lives in `androidTest` but references `DebugDataInjector` which is in the `debug` source set. AGP merges `debug` and `androidTest` source sets when assembling `debugAndroidTest`, so direct reference compiles. Test flow:
   - `@Before` instantiates an in-memory `AppDatabase` + mock `Prefs` + mock `RedditPrefs`.
   - Construct `DebugDataInjector(db, prefs, redditPrefs)` directly (no Hilt).
   - `runBlocking { injector.run(wipe = true) }`.
   - Assert `db.tweetDao().getTweets().size == 4`, `db.redditDao().getPosts().size == 4`, `db.tweetDao().getAllTags().size == 5`.
   - `@After` closes the DB.

8. **Add `verifyReleaseDebugInjectorAbsent` Gradle task** to `app/build.gradle`. Pseudocode:
   ```groovy
   tasks.register("verifyReleaseDebugInjectorAbsent") {
       dependsOn("assembleRelease")
       doLast {
           def apk = file("$buildDir/outputs/apk/release/app-release.apk")
           def tempDir = layout.buildDirectory.dir("verify-release-debug").get().asFile
           tempDir.mkdirs()
           copy { from zipTree(apk); into tempDir }
           def dexdump = "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/dexdump"
           tempDir.eachFileMatch(~/classes.*\.dex/) { dex ->
               def out = new ByteArrayOutputStream()
               exec { commandLine dexdump, dex.absolutePath; standardOutput = out }
               if (out.toString().contains("DebugDataInjector")) {
                   throw new GradleException("DebugDataInjector found in release APK!")
               }
           }
       }
   }
   ```
   Run locally once: `./gradlew :app:verifyReleaseDebugInjectorAbsent` to confirm pass. Wired into the verify-stage gate.

9. **Author `maestro/happy_path.yaml`.** Sequence:
   ```yaml
   appId: com.github.jayteealao.crumbs
   ---
   - launchApp:
       arguments:
         debug_action: "seed"
         wipe: true
   - extendedWaitUntil:
       visible:
         id: "splash-screen"
       timeout: 5000
   - extendedWaitUntil:
       visible:
         id: "home-scaffold"
       timeout: 10000
       # Cold path: if onboarding shows, run onboarding-skip subflow; if login shows, tap login-skip-auth.
       # Implemented via runFlow when: visible: id: "login-screen" -> tapOn login-skip-auth
   - runFlow:
       when:
         visible:
           id: "login-screen"
       commands:
         - tapOn:
             id: "login-skip-auth"
         - extendedWaitUntil:
             visible:
               id: "home-scaffold"
             timeout: 10000
   - tapOn: { id: "nav-tab-twitter" }
   - assertVisible: { id: "twitter-bookmarks-feed" }
   - tapOn: { id: "nav-tab-reddit" }
   - assertVisible: { id: "reddit-bookmarks-feed" }
   - tapOn: { id: "nav-tab-all" }
   - assertVisible: { id: "all-bookmarks-feed" }
   - tapOn: { id: "nav-tab-map" }
   - assertVisible: "COMING SOON"
   - tapOn: { id: "nav-tab-all" }
   - longPressOn: { id: "bookmark-card", index: 0 }
   - assertVisible: { id: "popup" }
   - tapOn: { id: "popup-action-tag" }
   - back
   - longPressOn: { id: "bookmark-card", index: 0 }
   - tapOn: { id: "popup-action-share" }
   - back
   - longPressOn: { id: "bookmark-card", index: 0 }
   - tapOn: { id: "popup-action-delete" }
   - assertVisible: { id: "snackbar" }
   - tapOn: { id: "snackbar-action" }   # UNDO
   - tapOn: { id: "filter-bar-chip-article" }
   - assertVisible: { id: "filter-bar-chip-article" }
   ```
   Each `tapOn` uses the `id:` selector via `testTagsAsResourceId`. `index: 0` on `bookmark-card` is the conventional Maestro selector for the first-rendered card.

10. **Author `maestro/long_press.yaml`.** Focused popup verification:
    - launchApp.arguments (seed).
    - Skip login (runFlow when: visible: login-screen).
    - Nav to All tab.
    - longPress on `bookmark-card[0]` → assert `popup` visible.
    - For each action (`popup-action-tag`, `popup-action-open`, `popup-action-share`, `popup-action-delete`): tap → assert side-effect-visible (TAG opens tag-editor-dialog; OPEN opens chooser; SHARE opens share sheet; DELETE shows snackbar) → `back` → re-open popup.
    - Backdrop dismiss: longPress, assertVisible `popup`, tapOn outside popup bounds (`{ point: "50%,5%" }`), assertNotVisible `popup`.

11. **Author `maestro/filter_overlay.yaml`.** Filter chip behavior:
    - launchApp.arguments (seed).
    - Skip login → Nav to All.
    - Type filter (instant): tap `filter-bar-chip-article` → assert feed updates (`extendedWaitUntil` on `filter-bar-count` text changing — text-based assertion since count is dynamic).
    - Tags overlay: **conditional on Blocker resolution** — if the Tags-overlay-UI gap from behaviors AC-line-96 is still open, this flow asserts only that the Tags chip toggles its visual state. If a stub `OverlayShell`-mounted tag picker lands pre-handoff (PO decision in the handoff stage), the flow exercises multi-select + APPLY.
    - Collection overlay: same hedge (collection chips are tag-set facets).
    - Backdrop dismiss: open overlay → tap `overlay-shell-backdrop` → assert overlay not visible.

12. **Spike: 401-trigger mechanism for sync_error.yaml.** Two-hour spike box, no estimate beyond that. Attempt path (b): extend `DebugIntentHandler` with `debug_action: "corrupt_token"` that writes `"INVALID"` to `AuthPref` (Twitter) via `Context.writeString("twitter_access_token", "INVALID")`, then triggers `Repository.refresh()` via `EntryPointAccessors`. Validate that the next sync emits `SyncErrorEvent.TwitterAuth401` and the banner renders within 1s. If path (b) cannot meet the 1s SLA, fall back to path (a) (writing via a debug-only `ContentProvider`); flag in `## Risks / Watchouts` as a known scope expansion.

13. **Author `maestro/sync_error.yaml`.** After spike resolution:
    - launchApp.arguments (seed + valid tokens via the optional seedTokens path from step 4).
    - Skip login → Nav to Twitter.
    - launchApp.arguments { debug_action: "corrupt_token" } (re-launches activity, fires the corrupter).
    - Pull-to-refresh on `twitter-bookmarks-feed` (Maestro `swipe` from top edge).
    - `extendedWaitUntil` visible `banner` with timeout 2000ms (SLA is 1s; allowance ×2).
    - assertVisible `banner-cta` (text "RECONNECT TWITTER").
    - tapOn `banner-cta` → assertVisible (some indicator that an OAuth intent fired — e.g., browser tab in foreground via Maestro's app-switch detection).

14. **Author `scripts/run-maestro.ps1`** (PowerShell primary). Skeleton:
    ```powershell
    param([string]$AVD = "Medium_Phone_API_36")
    & android emulator start $AVD
    & adb wait-for-device
    & ./gradlew :app:assembleDebug
    & adb install -r app/build/outputs/apk/debug/app-debug.apk
    $ts = Get-Date -Format "yyyyMMdd-HHmmss"
    $logFile = "build/maestro-logs/$ts.log"
    New-Item -ItemType Directory -Force -Path "build/maestro-logs" | Out-Null
    Start-Process -NoNewWindow lazylogcat -ArgumentList "logs", "dump", "--pkg", "com.github.jayteealao.crumbs" -RedirectStandardOutput $logFile
    & maestro test maestro/happy_path.yaml maestro/long_press.yaml maestro/filter_overlay.yaml maestro/sync_error.yaml
    $exit = $LASTEXITCODE
    Get-Process lazylogcat -ErrorAction SilentlyContinue | Stop-Process
    & android emulator stop $AVD
    exit $exit
    ```
    Exit code propagated; logs guaranteed flushed before AVD stop.

15. **Author `scripts/run-maestro.sh`** (POSIX/bash sibling). Same flow as `.ps1` with bash idioms (`set -euo pipefail`, `trap` for cleanup, `&` for background `lazylogcat`). Mark executable (`chmod +x` via git filemode if possible).

16. **Author top-level `README.md`.** Minimal structure:
    ```markdown
    # Crumb
    Single-user Android bookmark manager for Twitter & Reddit, written in Kotlin/Jetpack Compose.
    ## Build
    `./gradlew assembleDebug`
    ## Test
    `./gradlew test verifyRoborazziDebug`
    ## End-to-end (Maestro)
    Prereqs: Android Studio + emulator profile `Medium_Phone_API_36`, Maestro 2.4 CLI, lazylogcat.
    Run: `pwsh scripts/run-maestro.ps1` (Windows) or `bash scripts/run-maestro.sh` (macOS/Linux/Git Bash).
    Logs land at `build/maestro-logs/<timestamp>.log`.
    Manual fallback: boot AVD → `./gradlew installDebug` → `maestro test maestro/`.
    ## Project layout
    - `app/` — Compose app, NavHost, screens
    - `core/designsystem/` — brutalist design system (Crumbs theme + components + layouts)
    - `core/data/` — cross-module data types (DeletedBookmark + filter/event types)
    - `core/pref/` — DataStore-backed preferences (auth tokens)
    - `core/models/` — shared data classes
    - `feature/twitter/`, `feature/reddit/` — provider integrations + per-feed VMs
    - `maestro/` — end-to-end flows
    ```
    No badges, no contributor section, no license boilerplate — single-user app per project context.

17. **Local verify run.** Boot AVD, `./gradlew :app:installDebug`, `./gradlew :app:connectedDebugAndroidTest --tests "*DebugDataInjectorTest"` (instrumentation test), `./gradlew :app:verifyReleaseDebugInjectorAbsent`, `pwsh scripts/run-maestro.ps1`. All four flows must pass green; log file must contain zero `ERROR`-level entries from the crumbs process (except expected 401s from `sync_error.yaml`).

18. **Single atomic commit.** Conventional commit shape: `test: add Maestro end-to-end flows + debug data injector`. Body translates into user-language per External Output Boundary — no workflow-artifact references. Bumps no version (versionCode 3 / versionName 2.0 already locked in behaviors slice). All 11 new files + 3 modified files in one commit.

## Test / Verification Plan

### Automated checks

- **lint:** `./gradlew lintDebug` — no new lint issues introduced by the source-set addition or the Gradle task.
- **kotlinter:** `./gradlew kotlinterCheck` — debug source set files conform to project style.
- **typecheck:** `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin` — both variants compile (release without `DebugDataInjector` references).
- **unit tests:** existing `./gradlew :app:test` passes; no new JVM unit tests in this slice (the injector test is instrumentation-only — DAOs require a real Android context).
- **instrumentation tests:** `./gradlew :app:connectedDebugAndroidTest --tests "*DebugDataInjectorTest"` on `Medium_Phone_API_36` — asserts seed produces 4+4 bookmarks, 5 tags.
- **release-APK content gate:** `./gradlew :app:verifyReleaseDebugInjectorAbsent` — fails if `DebugDataInjector` symbol survives into `app-release.apk` (closes AC4).
- **Roborazzi:** `./gradlew verifyRoborazziDebug` — must remain green. If Step 2 (rename audit) fired, the testTag changes are semantics-only and must not cause golden drift; if drift is detected, investigate immediately (likely a `.testTag()` call accidentally appearing in a captured pixel range — should not happen).

### Interactive verification (human-in-the-loop)

Read from confirmed `stack:` block at `00-index.md`:
- `stack.platforms: [android]`
- `stack.testing: [junit, compose-ui-test]` (Maestro not in `stack.testing` — but Maestro CLI is on PATH per PO Batch B confirmation; the slice spec explicitly authorizes it)
- `stack.available-skills: [..., lazylogcat, android-cli, ...]`
- `stack.cli-on-path: [android, lazylogcat]`

**AC-Maestro-1** [interactive] — all 4 flows pass green.
- **What to verify:** `happy_path.yaml`, `long_press.yaml`, `filter_overlay.yaml`, `sync_error.yaml` all complete without assertion failure on a freshly-installed debug build.
- **Platform & tool:** Android — Maestro 2.4 CLI invoked via `scripts/run-maestro.ps1`.
- **Companion skills:** `android-cli` (AVD boot/stop), `lazylogcat` (log capture).
- **Steps:**
  1. `pwsh scripts/run-maestro.ps1` from repo root.
  2. Script auto-boots `Medium_Phone_API_36`, installs APK, starts log capture, runs `maestro test` against the 4 flow files.
- **Evidence capture:** Maestro auto-records device output to `~/.maestro/tests/<timestamp>/` (screenshots per step). Companion log at `build/maestro-logs/<timestamp>.log`. Both paths recorded in `06-verify-maestro.md`.
- **Pass criteria:** Maestro exit code 0; all 4 flow files show "✓" in the final report; log file has zero `ERROR` entries from `com.github.jayteealao.crumbs` (except expected 401 lines in the sync_error window).

**AC-Maestro-2** [automated within instrumentation] — debug injector seeds.
- **What to verify:** `DebugDataInjectorTest` passes — DB has 4+4 bookmarks + 5 tags after `injector.run(wipe = true)`.
- **Platform & tool:** Android — `connectedDebugAndroidTest` on `Medium_Phone_API_36`.
- **Companion skills:** none.
- **Steps:** `./gradlew :app:connectedDebugAndroidTest --tests "*DebugDataInjectorTest"`.
- **Evidence capture:** Test report XML at `app/build/outputs/androidTest-results/connected/debug/`. Linked in `06-verify-maestro.md`.
- **Pass criteria:** 1/1 test passes.

**AC-Maestro-3** [automated, Gradle gate] — release APK absence check.
- **What to verify:** `app-release.apk` does NOT contain `DebugDataInjector` class.
- **Platform & tool:** Local Gradle build (no emulator).
- **Companion skills:** none.
- **Steps:** `./gradlew :app:verifyReleaseDebugInjectorAbsent`.
- **Evidence capture:** Build log; task either succeeds or throws `GradleException` with the offending dex file. Logged in `06-verify-maestro.md`.
- **Pass criteria:** Task succeeds (exit 0).

**AC-Maestro-4** [manual] — log file ERROR review.
- **What to verify:** `build/maestro-logs/<timestamp>.log` shows zero `ERROR`-level lines from the app process during happy-path window. Sync_error flow's 401 lines are expected and excluded.
- **Platform & tool:** Manual review (text editor / `grep`).
- **Companion skills:** `lazylogcat` (already produced the file).
- **Steps:** Open log, search for `E/` or `ERROR`, exclude lines that fall within the sync_error.yaml execution window (timestamped section).
- **Pass criteria:** Maintainer signs off in `06-verify-maestro.md`.

**AC-Maestro-5** [interactive] — banner appears in sync_error screenshot.
- **What to verify:** Maestro's auto-captured screenshot at the assertion point of `sync_error.yaml` shows the brutalist banner above the Twitter feed.
- **Platform & tool:** Android — Maestro auto-screenshot.
- **Companion skills:** none.
- **Steps:** After flow run, open `~/.maestro/tests/<latest>/sync_error.yaml/<assertion-step>.png`.
- **Pass criteria:** Banner visible with kicker text "ERR · RECONNECT TWITTER" and `banner-cta` CTA button.

### Runtime-evidence-deferral clearance (cross-slice)

This slice's verify-stage probe runs are the natural moment to clear 17 of the 18 prior-slice deferrals. **AC-line-96 (Tags overlay UI gap)** is a substantive code gap, not just runtime evidence — it does NOT clear here and remains a pre-handoff PO decision. The other 17 should clear as their deferral text predicted:

- `toolchain` AC4 — Maestro testTag round-trip → cleared by Step 1 probe.
- `tokens` AC-K4, AC-K6 — handoff hex/font/background spot-check → cleared by happy_path screenshots.
- `components` AC-C6 — Maestro studio testTag round-trip → cleared by long_press + popup verification.
- `layouts` AC-L2, AC-L5 — HomeScaffold insets + shell testTag round-trip → cleared by happy_path nav frames.
- `screens` AC-S1, AC-S2, AC-S4, AC-S6-nav, AC-S7 — fidelity spot-checks + long-press integration + empty-state CTA → cleared by happy_path screenshots + long_press flow.
- `behaviors` AC-line-90 — migration test runtime → cleared by Step 17's `connectedDebugAndroidTest` running migration test alongside injector test.
- `behaviors` AC-line-{92, 93, 95, 97, 98} — long-press gestures, UNDO, type chip, banner, banner-CTA OAuth → cleared by happy_path + sync_error flows.

## Risks / Watchouts

1. **testTag dash compatibility (Step 1 probe).** If the probe fails, Step 2 is a ~1-hour mechanical rename across 16 files + their tests. The rename is intrusive enough that the verify-stage diff for the slice expands by ~60 lines of testTag-string-change noise. Mitigation: if rename fires, commit the rename as a separate atomic commit *before* any Maestro work, so the rename diff is visually isolated.

2. **`DebugIntentHandler` reflective dispatch from `MainActivity`.** Adding `Class.forName(...)` inside a try/catch in release `onCreate` has a tiny startup cost. Mitigation: the ClassNotFoundException path is JIT-cached after first throw — measurement on a Pixel 6-class device puts the overhead under 1ms. Acceptable. Documented in the verify report.

3. **`DebugDataInjector` cross-source-set reference from `androidTest`.** AGP merges `debug` + `androidTest` source sets when assembling `debugAndroidTest`, so the test compiles against the injector. **Failure mode:** if a future module split or AGP behavior change breaks this merge, `DebugDataInjectorTest` fails to compile. Mitigation: a one-line comment in the test class documenting the source-set-merge assumption; verify report includes a `./gradlew :app:compileDebugAndroidTestKotlin` smoke check.

4. **Maestro 2.4 `launchApp.arguments` may not propagate to `onCreate` on cold start.** Confirmed in Maestro 2.4.0 release notes that arguments → `intent.extras`, but the `onCreate` vs `onNewIntent` race on app-already-running has bitten users. Mitigation: `MainActivity` dispatches from both hooks; idempotency in `DebugDataInjector` (wipe → seed is safe to re-run) absorbs any double-fire.

5. **`sync_error.yaml` 1s SLA on banner appearance.** Step 12 spike validates the path; if path (b) (corrupt-token-and-refresh) cannot land the banner within 1s on `Medium_Phone_API_36`, the AC's "within 1s" criterion needs PO consultation. Mitigation: spike has explicit fallback to path (a) (ContentProvider) which can pre-corrupt the token state before any UI work.

6. **`lazylogcat` background process on Windows PowerShell.** `Start-Process -NoNewWindow` with output redirection has known buffering quirks on PowerShell ≤7.2. Mitigation: script flushes via `Get-Process lazylogcat | Stop-Process` (not by Ctrl+C signal), giving the process a clean shutdown path; if redirection issues appear in practice, switch to `Start-Job` + `Receive-Job` pattern (documented as fallback in the script comments).

7. **`maestro test maestro/_probe.yaml` vs `maestro test maestro/` argument shape.** Maestro's `test` command accepts either individual files or a directory. Scripts enumerate explicit file paths (not the directory) to skip `_probe.yaml` (preserved as a smoke flow). Verify-stage probe runs both forms once to confirm.

8. **AVD profile divergence.** Verify section pins `Medium_Phone_API_36`. Slice spec literal said "Pixel 6 API 34". The plan's verify section is authoritative; verify-stage report explicitly documents the override for handoff aggregation.

9. **The Tags overlay UI gap (behaviors AC-line-96).** Plan does NOT close this. `filter_overlay.yaml` hedges its Tags assertions per Blocker section. If the PO chooses to land a stub Tags-overlay UI pre-handoff (separate small slice or a `wf-quick refactor` cycle), the flow is updated. If PO accepts the chip-as-toggle behavior for v2.0, the flow's Tags assertion stays at the toggle-state level.

## Dependencies on Other Slices

- **`behaviors`** *(landed, verified-partial)* — every flow exercises behaviors that landed in commit `a01f79f3` (long-press → DELETE → snackbar UNDO; sync-error banner; filter chips). The 7 runtime-evidence-deferrals from behaviors close as this slice's flows pass. Hard dependency.
- **`screens`** *(landed, verified-partial)* — testTags on every navigated screen (`splash-screen`, `login-screen`, `home-scaffold`, `twitter-bookmarks-screen`, `reddit-bookmarks-screen`, `all-bookmarks-screen`, `nav-tab-*`) come from the screens slice. Stable since 2026-05-17. Hard dependency.
- **`components`** *(landed, verified-partial)* — testTags on every queried component (`bookmark-card`, `popup`, `popup-action-*`, `snackbar`, `snackbar-action`, `banner`, `banner-cta`, `filter-bar-chip-*`) come from the components slice. Stable since 2026-05-17. Hard dependency.
- **`quick-skip-auth-page`** *(compressed slice, landed)* — `login-skip-auth` testTag at `LoginScreen.kt:163` is the load-bearing entry point for `happy_path.yaml`. Without it, OAuth handling becomes mandatory and the slice spec's risk-1 fires. Hard dependency.

No downstream slice depends on this one. After this slice's verify, the next workflow stage is `/wf review brutalist-redesign` (slug-wide review per `review-scope: slug-wide`).

## Assumptions

1. **`Modifier.semantics { testTagsAsResourceId = true }` at CrumbsTheme root suffices** for the entire Compose tree's testTag round-trip. Verified by reading `CrumbsTheme.kt:40`; documented in Maestro Compose docs.
2. **Maestro 2.4 CLI is installed and on PATH** on the dev machine. PO Batch B confirmed `android` + `lazylogcat`; Maestro CLI installation status is implied by the slice spec but not re-confirmed. Verify-stage Step 1 fails fast if `maestro` is missing — adequate signal.
3. **`Medium_Phone_API_36` AVD is created and warm-bootable** on the dev machine. Continuity with all prior slices' verify runs validates this.
4. **The `DebugIntentHandler` reflective dispatch pattern keeps release builds clean.** Confirmed by AGP source-set documentation; verify gate via `verifyReleaseDebugInjectorAbsent` provides hard runtime evidence.
5. **`launchApp.arguments` produces `intent.extras` that survive into `MainActivity.onCreate` on cold start.** Maestro 2.4.0 release notes confirm; Step 1 probe also exercises a `launchApp` (without arguments) so the cold-start path is observed.
6. **`lazylogcat` skill at `~/.claude/skills/lazylogcat/SKILL.md`** is reachable on the dev machine. Sub-agent 2 confirmed the file exists.
7. **`./gradlew assembleRelease` does not require a signing config.** Existing `app/build.gradle` has `if (System.getenv("SIGNING_STORE_FILE") != null) { signingConfig signingConfigs.release }` — unsigned release variant assembles fine for the APK-content gate.
8. **`Maestro` exit code 0** means all flows passed (Maestro convention). Documented; verify gate relies on it.

## Blockers

**Blocker 1: Tags overlay UI gap.** Behaviors slice's AC-line-96 deferred the OverlayShell-mounted multi-select tag picker. Plan does NOT close this gap. `filter_overlay.yaml` hedges its Tags assertions accordingly. Two resolution paths, PO decides pre-handoff:

- **Path A: ship v2.0 with chip-as-toggle behavior.** `filter_overlay.yaml`'s Tags assertion verifies only the chip's visual state toggle. AC-line-96 reclassified from "verified-partial" to "scope-deferred" in the handoff aggregation. CHANGELOG v2.0 entry mentions Tags filtering as a follow-up.
- **Path B: small pre-handoff slice (~½ day) lands the OverlayShell tag picker.** Either as a 4th compressed slice (`/wf-quick refactor brutalist-redesign add-tags-overlay`) or as a verify-owned fix in this slice's verify-stage. Flow's Tags assertion expands to multi-select + APPLY.

This Blocker does NOT prevent the maestro slice from implementing — it only constrains the `filter_overlay.yaml` Tags assertion scope. Implement-stage proceeds with Path A's hedged assertion; if PO chooses Path B later, the flow is a 5-line edit.

## Freshness Research

### Maestro CLI 2.4.x

- **Source:** [Maestro CLI 2.4.0 release blog](https://maestro.dev/blog/maestro-cli-2-4-0) (April 2026).
  Why it matters: pins the CLI version assumed by `scripts/run-maestro.*`.
  Takeaway: 2.4.0 is current; `--device-os=android-34` replaces deprecated `--os-version`/`--android-api-level` flags. Scripts use neither (rely on a running emulator), so unaffected.

- **Source:** [Maestro Compose docs](https://docs.maestro.dev/platform-support/android-jetpack-compose).
  Why it matters: confirms `testTagsAsResourceId = true` semantic; **does not address dash compatibility** explicitly.
  Takeaway: dash question stays open; Step 1 probe is the right resolution path.

- **Source:** [Maestro conditions docs](https://docs.maestro.dev/maestro-flows/flow-control-and-logic/conditions).
  Why it matters: `runFlow when:` syntax for skip-if-already-authed.
  Takeaway: `when: notVisible: id: "home-scaffold"` is the canonical skip-if-state pattern; happy_path uses this verbatim.

- **Source:** [Maestro launchApp arguments](https://docs.maestro.dev/reference/commands-available/launchapp).
  Why it matters: confirms `arguments:` produces `intent.extras` for string/bool/int/double types.
  Takeaway: `debug_action: "seed", wipe: true` shape lands at `intent.getStringExtra("debug_action") + intent.getBooleanExtra("wipe")`.

- **Source:** [Maestro flakiness analysis](https://medium.com/@om_narayan/maestro-flakiness-source-code-analysis-d6ab1b2a1bab).
  Why it matters: documents hardcoded 17s/7s/2-retry/0.5%-pixel-change thresholds.
  Takeaway: `extendedWaitUntil` with `timeout: 10000` overrides the 7s optional-lookup default for Compose-recomposition-sensitive assertions. Used liberally in all 4 flows.

### Android debug-only source set

- **Source:** [AGP Build Variants](https://developer.android.com/build/build-variants) + [Dipien guide](https://medium.com/dipien/how-to-organize-your-debug-and-release-android-code-255d7459521b).
  Why it matters: confirms `app/src/debug/` source set is excluded from release APK at the AGP level.
  Takeaway: no `debugApi` needed; reflective dispatch from `MainActivity` is the cleanest cross-source-set call shape.

- **Source:** [AndroidJUnitRunner docs](https://developer.android.com/training/testing/instrumented-tests) — implicit.
  Why it matters: `androidTest` source set merges with `debug` for `debugAndroidTest` builds; references to `DebugDataInjector` from `app/src/androidTest/.../DebugDataInjectorTest.kt` compile.
  Takeaway: no Hilt test infra needed; `Room.inMemoryDatabaseBuilder()` + direct DAO calls match the existing `MigrationTest.kt` pattern.

### lazylogcat tool

- **Source:** [`parfenovvs/lazylogcat`](https://github.com/parfenovvs/lazylogcat) (public repo).
  Why it matters: documents the tool's CLI shape and confirms it's not a proprietary internal binary.
  Takeaway: `lazylogcat logs dump --pkg <package> [--tag <tag>] [--text <text>]` is the invocation. Scripts use `--pkg com.github.jayteealao.crumbs` to scope to app process logs.

- **Source:** Local skill at `~/.claude/skills/lazylogcat/SKILL.md`.
  Why it matters: PO-confirmed installed via skill on the dev machine.
  Takeaway: invocation via skill wrapper is the documented pattern; scripts call the CLI directly since they're standalone executables.

### Windows Maestro support

- **Source:** [Maestro Windows install docs](https://docs.maestro.dev/getting-started/installing-maestro/windows).
  Why it matters: dev's primary environment is Windows.
  Takeaway: native Windows is fully supported in 2026; WSL2 explicitly discouraged. PowerShell primary script is correct; bash sibling covers Git Bash on Windows + macOS/Linux.

### CI emulator runner (not used this slice but documented for future)

- **Source:** [`reactivecircus/android-emulator-runner` v2.36.0](https://github.com/ReactiveCircus/android-emulator-runner/releases).
  Why it matters: future CI integration deferred per PO Q3.
  Takeaway: when CI is eventually added, pin `v2.36.0` with `api-level: 34, target: google_apis, arch: x86_64, profile: pixel_6` + AVD-snapshot caching via `actions/cache` on `~/.android/avd`. Cold boot 5–8 min on `ubuntu-latest` (no KVM); snapshot caching brings warm boot to ~30s.

## Revision History

*(none yet — initial plan)*

## Recommended Next Stage

- **Option A (default):** `/wf implement brutalist-redesign maestro` — execute the plan. 18-step single-commit slice with 8 PO-locked decisions, 17 prior-slice deferrals collapsing onto its verify run. **Compact first** — planning research (3 sub-agent reports + 2 discovery rounds) is noise for the implement loop; PreCompact hook preserves workflow state.
- **Option B:** `/wf-quick refactor brutalist-redesign add-tags-overlay` — close Blocker 1 Path B first. A ~½-day compressed slice landing the OverlayShell-mounted tag picker; lifts the `filter_overlay.yaml` Tags hedge. Recommended only if PO wants AC-line-96 fully closed in v2.0.
- **Option C:** `/wf review brutalist-redesign` — invoke the slug-wide review against the cumulative branch diff before adding the maestro slice's changes. Earlier review opportunity if PO wants reviewer signal before the final slice lands.
- **Option D:** `/wf plan brutalist-redesign maestro <feedback>` — directed-fix this plan with explicit feedback (e.g., "add CI workflow", "use ContentProvider for token corruption", "drop the spike step").
