---
schema: sdlc/v1
type: implement
slug: brutalist-redesign
slice-slug: quick-skip-auth-page
status: complete
stage-number: 5
created-at: "2026-05-17T10:23:06Z"
updated-at: "2026-05-17T10:23:06Z"
metric-files-changed: 6
metric-lines-added: 67
metric-lines-removed: 11
metric-deviations-from-plan: 1
metric-review-fixes-applied: 0
commit-sha: "5f0557d"
tags: [debug-affordance, auth-bypass, paging-api-migration, ac-k6-unblocker]
refs:
  index: 00-index.md
  implement-index: 05-implement.md
  slice-def: 03-slice-quick-skip-auth-page.md
  plan: 03-slice-quick-skip-auth-page.md
  siblings: [05-implement-toolchain.md, 05-implement-tokens.md]
  verify: 06-verify-quick-skip-auth-page.md
next-command: wf-verify
next-invocation: "/wf verify brutalist-redesign quick-skip-auth-page"
---

# Implement: quick-skip-auth-page

## Summary of Changes

A debug-build-only `Skip Auth (Debug)` button now appears on `LoginScreen` as a third option below `Connect with X` and `Connect with Reddit`. Tapping it bypasses Twitter/Reddit OAuth entirely and navigates directly to `HomeScreen`, with `LoginScreen` popped from the back stack. The button is gated by `BuildConfig.DEBUG`, so it does not appear in release builds and R8 will strip the dead branch when minify is on.

Reaching `HomeScreen` for the first time without OAuth exposed a pre-existing dependency rot: `androidx.paging:paging-compose:1.0.0-alpha17` (from 2022) referenced classes in `paging-runtime` that were never on the classpath, and after the `toolchain` slice landed AGP 9 + Room 2.8.4 the transitive `paging-common` resolved to 3.3.x — ABI-incompatible with alpha17. Fixing this required bumping `paging-compose` to 3.3.6 (which transitively pulls in the correct `paging-runtime` and `paging-common`) and migrating the three `LazyListScope.items(lazyPagingItems)` call sites to the count-based API (`items(count = …, key = …)` with `pagedItems.itemKey { … }` and indexed `pagedItems[index]` access). With that done, `HomeScreen` renders cleanly in both light (`#EFEEE9`) and dark (`#0B0B0B`) themes and shows the empty-state `Connect to Twitter` affordance.

## Files Changed

- `app/build.gradle` — added `buildConfig true` to `buildFeatures` so `BuildConfig.DEBUG` is generated for the app module.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/LoginScreen.kt` — added `import com.github.jayteealao.crumbs.BuildConfig`; inside the `else ->` (initial state) branch of the `when` expression, appended a `BuildConfig.DEBUG`-gated third `CrumbsButton` labelled "Skip Auth (Debug)" that calls `navController.navigate(Screens.HOMESCREEN.screenRoute(false)) { popUpTo(LoginScreen) inclusive }`.
- `gradle/libs.versions.toml` — bumped `pagingCompose` from `1.0.0-alpha17` to `3.3.6` to align with the Room 2.8.4 / Compose BOM 2026.05.00 transitive `paging-common` graph.
- `feature/twitter/src/main/java/com/github/jayteealao/twitter/screens/TwitterBookmarksScreen.kt` — replaced `import androidx.paging.compose.items` with `import androidx.paging.compose.itemKey`; migrated the `items(pagedBookmarks, key = { it.tweet.id }) { tweetData -> … }` call to `items(count = pagedBookmarks.itemCount, key = pagedBookmarks.itemKey { it.tweet.id }) { index -> val tweetData = pagedBookmarks[index]; … }`.
- `feature/reddit/src/main/java/com/github/jayteealao/reddit/screens/RedditBookmarksScreen.kt` — identical migration shape for `pagedPosts` with key `it.post.id`.
- `app/src/main/java/com/github/jayteealao/crumbs/screens/AllBookmarksScreen.kt` — identical migration shape applied twice, for the `twitterPosts` block (key `"twitter_${it.tweet.id}"`) and the `redditPosts` block (key `"reddit_${it.post.id}"`).

## Shared Files (also touched by sibling slices)

- `gradle/libs.versions.toml` — also touched by `toolchain` (Kotlin/AGP/Roborazzi/Room versions) and `tokens` (no version-catalog edits, but heavy use of the entries). This slice's edit is constrained to a single line (`pagingCompose` version). The components slice will likely also touch this file for Coil 3.

## Notes on Design Choices

- **Why bump `paging-compose` instead of pinning `paging-common`?** Room 2.8.4 already forces `paging-common` to 3.3.x via its own transitive dependencies. Trying to downgrade `paging-common` would have created a fresh conflict with Room and propagated to every DAO that defines `PagingSource` returns. The bump was the only available direction.
- **Why migrate to the count-based `items()` rather than keep the deprecated overload?** The deprecated `items(LazyPagingItems<T>)` extension was *removed* in `paging-compose:3.2.0`. Versions that still carry it (the late 3.1.x line) are not published to maven.google.com — `3.1.1` 404s. Migration was the cheapest path with a current artifact.
- **Why `popUpTo(LoginScreen) inclusive`?** Without `inclusive = true`, the back button on `HomeScreen` would return to the (now-bypassed) `LoginScreen`, contradicting the debug intent. Inclusive pop matches the `SplashScreen` → `HomeScreen` transition pattern in `SplashScreen.kt:32-34`.
- **Why no mock OAuth state injection?** The auth flow's `isAccessTokenAvailable` flow is left untouched. `HomeScreen` correctly observes "not authenticated" and renders the brutalist empty-state ("Connect to Twitter" / "Login To Twitter" button). That empty state is the AC-K6 evidence target and renders perfectly without further plumbing.

## Visual Contract Honored

No `02c-craft.md` exists for this slice. Contract assertions made instead against the AC-Q1/Q2/Q3 acceptance criteria from `03-slice-quick-skip-auth-page.md`:

- **AC-Q1** — Honored. The "Skip Auth (Debug)" button is present in debug builds (verified via screenshots `01-loginscreen-debug-with-skip-button.png` and `03-loginscreen-rerun.png`) and is wrapped in a `BuildConfig.DEBUG` source-level guard, so the bytecode emits it only into the debug variant. Release-side absence is structural (build-flavor-driven), not runtime-detected.
- **AC-Q2** — Honored. After tapping the button, `dumpsys activity activities` reports `topResumedActivity=…crumbs/.MainActivity` (i.e. we stayed inside Crumbs and reached `HomeScreen`), not `…nexuslauncher` (which appeared during the first attempt when `HomeScreen` crashed on paging). The `popUpTo(LoginScreen) inclusive` ensures back button does not retreat to login.
- **AC-Q3** — Honored. `HomeScreen` renders without crash in both light (`04-homescreen-via-skip-rerun.png`) and dark (`05-homescreen-dark-via-skip.png`) themes after the paging migration. No `AndroidRuntime` / `FATAL EXCEPTION` entries in logcat after the second attempt.

## Deviations from Plan

The original slice plan was **2 files / 5 steps / no new deps / no arch change**, well inside the wf-quick envelope. Execution landed at **6 files / 7 steps** because the AC-Q3 emulator check exposed a pre-existing dependency mismatch that the slice could not pass through without resolving.

**Deviation #1 — paging-compose 1.0.0-alpha17 ABI broken by `toolchain`-induced transitive resolution.**

The first AC-Q3 attempt produced `java.lang.ClassNotFoundException: androidx.paging.LoggerKt` at the first `HomeScreen` frame. Investigation showed:

- Room 2.8.4 (locked at toolchain) requires `paging-common:3.3.x` transitively.
- `paging-compose:1.0.0-alpha17` was compiled against `paging-common:3.0.0-alpha14` and uses removed/renamed symbols (including its `LazyListScope.items(LazyPagingItems)` extension, which is sourced from a class file that the newer `paging-common` no longer provides ABI for).
- The crash had been latent since the `toolchain` slice landed; no slice had reached `HomeScreen` to trigger it, because the OAuth gate was unreachable in verify environments. The discovery is a direct consequence of AC-K6 / AC-Q3 being the first user-observable check that crosses into `HomeScreen` rendering.

User decision recorded at decision point: bump `paging-compose` to 3.3.6 and migrate the three `items(lazyPagingItems)` call sites to the count-based API. This added four files to the diff (`libs.versions.toml`, TwitterBookmarksScreen, RedditBookmarksScreen, AllBookmarksScreen) and roughly +20/-9 lines on top of the original Skip Auth surface. The original 2-file plan thus becomes a 6-file plan; the wf-quick `>3 files` tripwire is breached but is documented and user-approved.

## Anything Deferred

- **Replacing the bypass with a fake-auth injector** is not done. The Skip Auth button uses navigation bypass, not state injection — `isAccessTokenAvailable` remains `false` after the tap and `HomeScreen` therefore renders its empty state. This is sufficient for AC-K6 (paper background pixel-check) and was explicitly out-of-scope per the slice's "Out of scope" section. A later slice (`behaviors` per the existing deferral text, or a deliberate follow-up) can add a debug data injector that materializes fixture bookmarks if richer HomeScreen evidence is needed.
- **Modernising the paging-compose call sites further** (e.g. using `paging-common`'s newer `LazyPagingItems<T>.itemContentType(…)` for stable item types, or pulling shared paging-aware bookmark list rendering into a reusable component) is deferred to whatever later slice owns `feature/twitter/components/` and `screens/AllBookmarksScreen.kt`. This implementation does the minimum to restore compilation.
- **Audit of other pre-existing `LazyListScope.items(paging)` call sites or analogous alpha17-era patterns** is not done in this slice. A short grep at the time of writing found exactly the three migrated sites; no further drift suspected.

## Known Risks / Caveats

- **Dark mode HomeScreen evidence (`05-homescreen-dark-via-skip.png`) shows a duplicate top-row of `Twitter / Reddit / All / Map` labels at the top of the content area in addition to the legitimate bottom navigation.** This is a layout glitch in `HomeScreen` (not in this slice's code) that surfaces only because we can now actually reach `HomeScreen`. The `screens` or `layouts` slice should investigate; this is not a blocker for the current slice's ACs but should be on the list of "newly-visible bugs the auth-skip unblocked."
- **The `paging-compose` bump may have other downstream surprises** the lint pass didn't catch (e.g. behavioural drift in `LoadState` semantics between 1.0.0-alpha17 and 3.3.6). Lint and `assembleDebug` were both clean; runtime testing only reached the empty-state path. Once OAuth is restored in verify (or once the debug injector lands), the `feature/twitter` and `feature/reddit` paging flows will exercise the new code and any regression there will surface.
- **The wf-quick tripwire** (>3 files) was breached. This implementation record is the audit trail; the user-approved scope extension is recorded above. Future similar dependency-rot discoveries should consider whether `/wf intake` is the better entry point.

## Freshness Research

- **Source:** [paging-compose release notes / changelog (developer.android.com)](https://developer.android.com/jetpack/androidx/releases/paging) — paging-compose 1.0.0-alpha18 added `LazyListScope.items(count, key, contentType, itemContent)` overload taking `LazyPagingItems.itemKey { ... }` and `LazyPagingItems.itemContentType { ... }` helpers. The legacy `items(LazyPagingItems<T>)` overload was deprecated at 3.0.0 and removed at 3.2.0. This established the only viable migration target as the count-based API.
- **Source:** Direct grep — confirmed only three call sites use the legacy overload (`TwitterBookmarksScreen.kt`, `RedditBookmarksScreen.kt`, `AllBookmarksScreen.kt`), all of which were migrated in this slice.
- **Source:** maven.google.com 404 on `androidx.paging:paging-compose:3.1.1` — confirmed during fallback attempt; the 3.1.x line was not published as a standalone `paging-compose` artifact (it shipped bundled with paging core releases without a standalone compose POM). 3.3.6 was the lowest published version that both restores `paging-runtime` transitively and keeps a current API.

## Recommended Next Stage

- **Option A (default):** `/wf verify brutalist-redesign quick-skip-auth-page` — exercise the AC-Q1/Q2/Q3 evidence formally and have the verify stage clear the parent tokens slice's `runtime-evidence-deferrals[3]` (AC-K6 HomeScreen paper background) with `cleared-by: slice/quick-skip-auth-page`. **Compact recommended.**
- **Option B:** `/wf review brutalist-redesign` — proceed directly to the slug-wide review since the per-slice ACs are demonstrably met from the implement evidence and the review-scope is slug-wide. Verify can be folded into review if the maintainer judges the implement evidence sufficient.
- **Option C:** `/wf plan brutalist-redesign components` — start the next slice in parallel; the auth-skip slice does not block components planning.
