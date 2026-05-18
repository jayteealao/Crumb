---
schema: sdlc/v1
type: implement
slug: brutalist-redesign
slice-slug: maestro
status: complete
stage-number: 5
created-at: "2026-05-18T07:25:46Z"
updated-at: "2026-05-18T07:25:46Z"
metric-files-changed: 14
metric-lines-added: 897
metric-lines-removed: 1
metric-deviations-from-plan: 4
metric-review-fixes-applied: 0
commit-sha: "fb5f3f0"
tags: [maestro, e2e, testing, debug-source-set, lazylogcat]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-maestro.md
  plan: 04-plan-maestro.md
  siblings:
    - 05-implement-toolchain.md
    - 05-implement-tokens.md
    - 05-implement-quick-skip-auth-page.md
    - 05-implement-components.md
    - 05-implement-layouts.md
    - 05-implement-screens.md
    - 05-implement-behaviors.md
  verify: 06-verify-maestro.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign maestro"
---

# Implement: maestro — End-to-end Maestro coverage

## Summary of Changes

Authored 4 Maestro `.yaml` flows (`happy_path`, `long_press`, `filter_overlay`, `sync_error`) plus a probe flow under `maestro/`, a debug-only `DebugDataInjector` (+ `DebugIntentHandler` entry point) under a new `app/src/debug/` source set, reflective debug-intent dispatch in `MainActivity`, an instrumentation test for the seed contract, a Gradle release-cleanliness gate (`verifyReleaseDebugInjectorAbsent`), cross-platform orchestration scripts under `scripts/`, and a new top-level `README.md` (the repo had none). No feature-module code touched; the debug surface is wired exclusively through `app/src/debug/` and a 30-line reflective shim in `MainActivity`.

## Files Changed

### New files (12)

- **`maestro/_probe.yaml`** — 21 lines. Standalone probe verifying `testTagsAsResourceId` + kebab-case testTag round-trip via `tapOn: id: login-skip-auth` and `extendedWaitUntil` on `home-scaffold`. Verify-stage runs this once before the full suite; if it fails, plan Step 2 (rename audit) fires.
- **`maestro/happy_path.yaml`** — 79 lines. Cold-start seed via `launchApp.arguments`, conditional `login-skip-auth` tap, 4-tab traversal (`nav-tab-twitter/reddit/all/map`), long-press → DELETE → snackbar → UNDO on the first `bookmark-card`, filter chip toggle on `filter-bar-chip-article`.
- **`maestro/long_press.yaml`** — 90 lines. Focused popup flow — re-opens the popup for each of the 4 actions (`popup-action-tag/open/share/delete`), exercises snackbar dismiss, and verifies backdrop tap dismissal via `tapOn: point: "50%,5%"` + `assertNotVisible: popup`.
- **`maestro/filter_overlay.yaml`** — 58 lines. Type instant chip toggle, Tags + Collection chip hedged-state assertions (per Blocker 1 — overlay UI gap from behaviors AC-line-96), conditional backdrop dismiss on `overlay-shell` if it opens.
- **`maestro/sync_error.yaml`** — 55 lines. Two-stage launch: first seeds, then re-launches with `debug_action: corrupt_token` to write `INVALID_DEBUG_TOKEN` into the Twitter `ACCESS_CODE` pref. Pull-to-refresh on `twitter-bookmarks-feed` forces a 401; banner asserted within 2000ms (SLA is 1s, 2× allowance); CTA tap fires OAuth intent. Screenshot tagged `sync_error_banner` for verify evidence.
- **`app/src/debug/AndroidManifest.xml`** — 2 lines. Empty `<manifest />` shell so AGP recognizes the debug source set. No `<application>` overrides — release `MainActivity` remains the sole entry point.
- **`app/src/debug/java/.../DebugDataInjector.kt`** — 212 lines. `@Singleton class @Inject constructor(Context, AppDatabase, twitter.Prefs, RedditPrefs)`. `suspend fun run(wipe: Boolean)` clears tables when `wipe=true`, then seeds 1 `TwitterUserEntity` + 4 `TweetEntity` + 4 `RedditPostEntity` + 5 `TagEntity` (3 plain + 2 `collection-*`-prefixed) + 2 `TweetTagCrossRef` rows. `suspend fun corruptTwitterToken()` writes `"INVALID_DEBUG_TOKEN"` to `ACCESS_CODE`. All work wrapped in `withContext(Dispatchers.IO)` since the synchronous Tweet DAO inserts (`insertTweet`, `insertTwitterUser`) are non-suspending and must run off the main thread.
- **`app/src/debug/java/.../DebugIntentHandler.kt`** — 55 lines. Plain `object` (no DI on the entry point itself — invoked reflectively). `@JvmStatic fun handleIntent(activity, intent)` reads `debug_action`, resolves `DebugDataInjector` via `EntryPointAccessors.fromApplication(...)`, dispatches `seed` / `corrupt_token` through `activity.lifecycleScope.launch { runCatching { ... } }`. Unknown actions Timber-warn and return. The Hilt `DebugInjectorEntryPoint` interface lives in the same file.
- **`app/src/androidTest/java/.../DebugDataInjectorTest.kt`** — 57 lines. `@RunWith(AndroidJUnit4::class)` instrumentation test. Builds in-memory `AppDatabase` via `Room.inMemoryDatabaseBuilder(ctx, ...)`, constructs `DebugDataInjector` directly (no Hilt), runs the seed, asserts: 5 tags, 4 latest-bookmark id `"debug-tweet-1"`, 4 reddit post count. Cross-source-set reference (test in `androidTest/`, target in `debug/`) compiles because AGP merges both at `debugAndroidTest` assembly — documented inline.
- **`scripts/run-maestro.ps1`** — 62 lines. PowerShell primary script. Boots `Medium_Phone_API_36` via `emulator -avd ... -no-snapshot-save`, waits for `sys.boot_completed`, runs `gradlew :app:assembleDebug`, installs APK, starts `lazylogcat logs dump --pkg com.github.jayteealao.crumbs` in the background redirecting to `build/maestro-logs/<timestamp>.log`, executes `maestro test` on the 4 flow files, then cleans up (stops `lazylogcat`, `adb emu kill`). Propagates Maestro exit code.
- **`scripts/run-maestro.sh`** — 62 lines. POSIX sibling with `set -euo pipefail` + `trap cleanup EXIT` covering both AVD and log-capture termination. Same boot-install-test-tear-down flow.
- **`README.md`** — 74 lines. Top-level README. Sections: Build / Test / End-to-end (Maestro) / Project layout. Documents `Medium_Phone_API_36` AVD prereq, the `verifyReleaseDebugInjectorAbsent` gate, and `lazylogcat` linkage. No badges, no contributor section — single-user app.

### Modified files (2)

- **`app/build.gradle`** — +40/-1. Added `tasks.register("verifyReleaseDebugInjectorAbsent") { dependsOn("assembleRelease"); doLast { ... } }` at file end. Replaces the plan's `dexdump` invocation with a path-flexible approach: scans every `.dex` entry inside the release APK as ISO-8859-1 bytes for the string `"DebugDataInjector"` and throws `GradleException` on any hit. Avoids depending on `dexdump` being on PATH or knowing the exact `build-tools` version. The release APK filename is matched by glob (`.apk` suffix under `outputs/apk/release/`) so signed and unsigned variants both work.
- **`app/src/main/java/.../MainActivity.kt`** — +29/-0. Added `import android.content.Intent`, an `onNewIntent(intent: Intent)` override that calls `setIntent(intent)` + `dispatchDebugIntent(intent)`, a `dispatchDebugIntent(intent: Intent?)` private helper that uses `Class.forName("com.github.jayteealao.crumbs.debug.DebugIntentHandler")` + `.getMethod("handleIntent", ComponentActivity::class.java, Intent::class.java).invoke(null, this, intent)` inside `try/catch (ClassNotFoundException)` + a broad `Throwable` catch, and a call to `dispatchDebugIntent(intent)` from `onCreate`. Release builds throw `ClassNotFoundException` on the `forName` and silently no-op — no debug references survive into release bytecode beyond a 30-line try/catch block.

## Shared Files (also touched by sibling slices)

- **`MainActivity.kt`** — last touched by the layouts slice (added `enableEdgeToEdge()`). This slice's edit is additive — keeps the existing `enableEdgeToEdge()` call + `CrumbsTheme { CrumbsNavHost(...) }` block byte-stable, only inserts `dispatchDebugIntent(intent)` between `enableEdgeToEdge()` and `setContent { ... }`, adds the `onNewIntent` override, and adds the reflective helper. No conflict with sibling work.
- **`app/build.gradle`** — last touched by behaviors slice (`versionCode 3`, `versionName "2.0"`, added `:core:data` dep). This slice's edit appends a new `tasks.register` block at file end; does not modify any existing block.

## Notes on Design Choices

- **Reflective debug dispatch over a debug-`Application` subclass.** Considered putting a `CrumbApplication` subclass in `app/src/debug/` that handles debug-intent dispatch in `onCreate`. Rejected: would require either renaming `app/src/main/.../CrumbApplication.kt` or splitting the `<application android:name>` attribute by source set — touches release surface unnecessarily. Reflection from `MainActivity` keeps the release bytecode delta to a single try/catch.
- **`launchApp.arguments` over a debug ContentProvider seed trigger.** Plan PO Round 2 Q1 picked this. Two launchApp calls in `sync_error.yaml` (first seed, second `corrupt_token`) over a single launch + adb-broadcast — cleaner Maestro syntax and no debug-only receiver in `AndroidManifest`.
- **Dex-string scan over dexdump for the release-cleanliness gate.** Plan called for `dexdump` from `android.sdkDirectory/build-tools/<version>/`. Replaced with a pure-JVM byte scan of every `.dex` entry inside the APK zip — no PATH dependency, no build-tools-version coupling, same false-positive surface (class names appear in the dex string pool either way).
- **Hedged Tags + Collection assertions in `filter_overlay.yaml`.** Per Blocker 1 (behaviors AC-line-96, OverlayShell tag picker UI gap), the flow asserts chip-toggle state only. Expansion to multi-select + APPLY is a 5-line edit gated on PO Path B resolution pre-handoff.
- **`Medium_Phone_API_36` AVD in scripts.** Slice spec literal said "Pixel 6 API 34", but every prior slice's verify run used `Medium_Phone_API_36`. Scripts and README both pin `Medium_Phone_API_36`; the divergence is documented in plan Risk 8 for handoff aggregation.
- **`extendedWaitUntil` everywhere.** Maestro 2.4's default optional-lookup timeout is 7s; Compose recomposition + Splash auto-nav (1000ms) + LoginScreen → Home transition can push past that on cold start. Every state-transition assertion uses `extendedWaitUntil` with an explicit `timeout`.

## Deviations from Plan

1. **Step 8 — dex scan replaces dexdump.** Plan's `dexdump`-based Gradle task replaced with a pure-JVM `ZipFile` byte scan. Rationale: no PATH or build-tools-version dependency, identical detection coverage (dex string pool contains class names regardless of inspection tool). Documented inline in `app/build.gradle`.
2. **Step 1 probe execution deferred to verify-stage.** Plan says "boot AVD, install APK, run probe; if green, proceed". Implement-stage cannot boot an AVD; the probe file is authored and ready, but actual execution is verify-stage work. If the probe fails at verify, the Step 2 rename audit fires there. Implement-stage proceeded with dashes per the dominant Maestro Compose convention; 60 existing kebab-case testTags across 16 files have been baseline-stable through 6 prior slices' Roborazzi runs (which exercise testTag-bearing trees) without resource-name complaints — strong soft evidence that dashes round-trip cleanly under `testTagsAsResourceId`.
3. **`DebugDataInjector` accepts `@ApplicationContext` directly.** Plan signature was `@Singleton class DebugDataInjector @Inject constructor(db, prefs, redditPrefs)`. Added `@ApplicationContext Context` as a constructor parameter so `corruptTwitterToken()` can call `context.writeString(ACCESS_CODE, ...)` directly via the existing `pref` extension — no need to thread an extra `setAccessAndRefreshToken` mutator through `Prefs`. Test constructs the injector with `InstrumentationRegistry.getInstrumentation().targetContext` — same shape as Hilt provides at runtime.
4. **`sync_error.yaml` uses `swipe: { from: { id }, direction: DOWN }` for pull-to-refresh.** Plan didn't specify the swipe syntax; this is the Maestro 2.4 canonical pull-to-refresh shape. The first `launchApp` seeds; the second re-launch with `debug_action: corrupt_token` triggers token corruption; the subsequent swipe forces a sync that 401s. Banner assertion uses `extendedWaitUntil` with `timeout: 2000` (plan said the SLA is 1s; allowance ×2).

## Anything Deferred

- **Probe execution** — `maestro test maestro/_probe.yaml` against a running AVD. Owned by `/wf verify brutalist-redesign maestro`.
- **All four Maestro flows execution** — green-light gating per AC-Maestro-1. Owned by verify.
- **Instrumentation test run** — `./gradlew :app:connectedDebugAndroidTest --tests "*DebugDataInjectorTest"`. Owned by verify.
- **Release-APK absence check execution** — `./gradlew :app:verifyReleaseDebugInjectorAbsent`. Owned by verify.
- **17 prior-slice runtime-evidence-deferrals collapse.** The probe run + happy_path + long_press + sync_error flows clear: toolchain AC4; tokens AC-K4, AC-K6 (handoff hex/font); components AC-C6; layouts AC-L2, AC-L5; screens AC-S1, AC-S2, AC-S4, AC-S6-nav, AC-S7; behaviors AC-line-{90, 92, 93, 95, 97, 98}.
- **AC-line-96 (Tags overlay UI)** — substantive code gap, not just runtime evidence. Stays open as Blocker 1 for pre-handoff PO decision (Path A: ship with chip-as-toggle; Path B: ½-day refactor slice landing the OverlayShell tag picker).
- **CI integration** — explicitly out per PO Round 1 Q3.

## Known Risks / Caveats

1. **testTag dash compatibility unverified empirically.** The probe file is authored; live execution at verify-stage is the first hard signal. If dashes fail under `testTagsAsResourceId`, a ~1-hour rename audit across 16 testTag-bearing files fires before any flow can pass.
2. **`launchApp.arguments` cold-start propagation.** Maestro 2.4 release notes confirm arguments → `intent.extras`. MainActivity dispatches from both `onCreate` and `onNewIntent`; `DebugDataInjector.run(wipe=true)` is idempotent, so double-fire is harmless.
3. **`DebugDataInjector` cross-source-set reference from `androidTest`.** Compiles because AGP merges `debug` + `androidTest` source sets for `debugAndroidTest`. If a future AGP behavior change breaks this merge, the test compilation fails — caught immediately. Documented in the test file's class doc.
4. **`sync_error.yaml` 1s SLA on banner appearance.** Banner is given a 2000ms `extendedWaitUntil` window; if the 401 → bus → collector → recomposition path exceeds 1s on the verify AVD, the SLA needs PO consultation but the flow still passes. Plan Risk 5 documents the escalation.
5. **`lazylogcat` background process on PowerShell.** Output redirection has known buffering quirks ≤PS 7.2; if logs are truncated, the script's `Start-Job` fallback (documented inline) can be enabled. No code change needed.
6. **`gradlew.bat` vs `./gradlew` on Windows.** PowerShell script tries `.\gradlew.bat` first then falls back to `.\gradlew`; bash script does the reverse. Both forms exist in the repo.

## Freshness Research

Carried forward from `04-plan-maestro.md` (Maestro 2.4 CLI, `testTagsAsResourceId` semantic, `extendedWaitUntil` 7s default override, `launchApp.arguments` → intent.extras, `lazylogcat` CLI shape, native Windows Maestro support). No additional freshness pass during implement — all relevant external surfaces were just-researched at plan time.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign maestro` — boots AVD, runs the 4 flows + instrumentation test + release-cleanliness gate, captures Maestro auto-screenshots + `lazylogcat` log. **Compact recommended** — implementation context (DAO surface, source-set merge details, reflective dispatch shape) is noise for verification. The PreCompact hook preserves workflow state. Verify-stage owns AC-Maestro-1 through 5 + collapse of 17 prior-slice runtime-evidence-deferrals.
- **Option B:** `/wf-quick refactor brutalist-redesign add-tags-overlay` — close Blocker 1 Path B before verify. A ~½-day compressed slice landing the OverlayShell tag picker so `filter_overlay.yaml`'s Tags assertion can expand from chip-toggle to multi-select + APPLY. Recommended only if PO wants AC-line-96 fully resolved in v2.0.
- **Option C:** `/wf review brutalist-redesign` — slug-wide review now possible (every slice landed). Less recommended than Option A — verify should adjudicate first so reviewer sees a known-passing state.
- **Option D:** `/wf implement brutalist-redesign maestro` again with explicit feedback — directed-fix only if a flow or the injector needs immediate revision (e.g., testTag dash probe surfaced a problem out-of-band).
