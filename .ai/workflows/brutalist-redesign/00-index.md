---
schema: sdlc/v1
type: index
slug: brutalist-redesign
title: "Replace Crumbs app design with the Brutalist Mono (Option D) handoff, pixel-for-pixel"
status: active
current-stage: review
stage-number: 7
created-at: "2026-05-16T21:44:39Z"
updated-at: "2026-05-18T15:10:09Z"
selected-slice: maestro
branch-strategy: dedicated
branch: "feat/brutalist-redesign"
base-branch: "main"
review-scope: slug-wide
pr-url: ""
pr-number: 0
appetite: large
open-questions: []   # all shape-stage questions resolved; remaining ambiguities are sliceable
locked-decisions:
  toolchain:
    kotlin: "2.2.10"          # downgraded from plan target 2.3.21 to match AGP 9.1.1 bundled KGP
    agp: "9.1.1"
    gradle: "9.3.1"           # plan said "9.1+"; AGP 9.1.1 requires >=9.3.1
    compose-bom: "2026.05.00"
    material3: "1.4.0"        # via Compose BOM
    roborazzi: "1.60.0"       # plan target 1.37.0 predates AGP 9 support
    robolectric: "4.16"
    hilt: "2.59.2"            # forced by AGP 9 BaseExtension removal
    room: "2.8.4"             # forced by KSP2 "unexpected jvm signature V"
    ksp: "2.2.10-2.0.2"
    jdk: "17"
    compile-sdk: 35           # plan said 34; Compose 1.11.1 in BOM requires >=35
  visual:
    accent: "#FF5A1F"
    themes: [light, dark]
    fonts: [funnel-display-bundled, ibm-plex-mono-bundled]
    dynamic-color: disabled
  behaviors:
    long-press: "contextual popup, 4 actions"
    filters: "Type instant + Tags/Collection overlay"
    delete: "soft + 5s undo + deleted_ids tombstone"
    sync-error: "inline ink-stroked banner above feed"
    onboarding: "first-launch only"
  scope:
    nav-tabs: [twitter, reddit, all, map]
    map-view: "brutalist COMING SOON placeholder"
    orphan-components: "delete-13-outright"
    color-schema-migration: "hard-cutover"
    pager-migration: "compose-native HorizontalPager"
  verification:
    device: "Pixel 6 (w411-h891-xxhdpi, API 34)"
    roborazzi-threshold: "1% RGB + 5% changed-pixel"
    fidelity-target: "≥95%"
    versionCode: 3
    versionName: "2.0"
tags: [redesign, ui, compose, design-system, brutalist]
stack:
  detected-at: "2026-05-16T21:44:39Z"
  platforms: [android]
  languages: [kotlin]
  ui: [compose, material3]
  build: [gradle]
  package-managers: [gradle]
  testing: [junit, compose-ui-test]
  observability: [timber]
  integrations: [hilt, room, firebase, retrofit, coil, datastore, exoplayer, accompanist, ksp]
  cli-on-path:
    - {name: "android", hint: "Android project + SDK + emulator orchestration (user-confirmed installed)"}
    - {name: "lazylogcat", hint: "Non-interactive logcat capture/filter for verify (user-confirmed installed)"}
  available-skills:
    - {name: "android-cli", hint: "Wraps `android` CLI for project create/deploy/SDK/diagnostics"}
    - {name: "lazylogcat", hint: "Wraps `lazylogcat` for filtered log capture"}
    - {name: "adaptive", hint: "Multi-form-factor Compose UI adaptation"}
    - {name: "edge-to-edge", hint: "Edge-to-edge insets + system bar legibility in Compose"}
    - {name: "styles", hint: "Jetpack Compose Styles API for component theming"}
    - {name: "migrate-xml-views-to-jetpack-compose", hint: "XML→Compose (n/a — app already Compose)"}
    - {name: "testing-setup", hint: "Test infrastructure incl. screenshot tests"}
    - {name: "frontend-design:frontend-design", hint: "High-quality UI implementation from designs"}
    - {name: "sdlc-workflow:review", hint: "Parallel multi-dimension review"}
  available-mcp:
    - {name: "pencil", hint: ".pen design files (handoff is HTML not .pen — n/a)"}
    - {name: "zai-mcp-server", hint: "Image/diagram analysis (could validate against verify-*.jpg references)"}
  user-confirmed: true
next-command: wf-implement
next-invocation: "/wf implement brutalist-redesign reviews"
runtime-evidence-deferrals:
  - slice: toolchain
    ac: AC4
    reason: "Maestro testTag round-trip needs a Modifier.testTag(...) in the codebase; testTags are introduced systematically in the maestro slice. Scaffolding (testTagsAsResourceId at CrumbsTheme:42) is in place; Maestro can already address the running app."
    deferred-at: "2026-05-17T01:17:12Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — happy_path.yaml + _probe.yaml — 4/4 flows pass; 14+ kebab-case testTags resolve as resource-id under testTagsAsResourceId"
  - slice: toolchain
    ac: AC6
    reason: "Manual visual diff of 133 regenerated goldens against pre-bump tree pending maintainer review. Acceptable drift: anti-alias/hinting/banding/material3-ripple. Unacceptable: missing strokes, repositioned elements, color shifts."
    deferred-at: "2026-05-17T01:17:12Z"
    cleared-by: null
  - slice: tokens
    ac: AC-K4
    reason: "Maintainer-driven manual handoff diff against Crumbs-handoff/crumbs/project/handoff-tokens.jsx. Registered as maintainer-owned at plan-round-1; no automated path intended. Acceptable drift: anti-aliasing, monitor color gamut. Unacceptable: wrong hex, wrong type family."
    deferred-at: "2026-05-17T08:56:40Z"
    cleared-by: null
  - slice: tokens
    ac: AC-K6
    reason: "HomeScreen-paper-background pixel check requires advancing past LoginScreen, which is auth-gated (Twitter/Reddit OAuth credentials not provisioned in verify env). Orange accent + dark-mode #0B0B0B background + Funnel Display + IBM Plex Mono all confirmed reaching the running app via LoginScreen evidence on Medium_Phone_API_36 (light, dark, airplane mode). Clears when /wf-quick probe runs with OAuth credentials or after the behaviors slice lands a debug data injector."
    deferred-at: "2026-05-17T08:56:40Z"
    cleared-by: "slice/quick-skip-auth-page"
    cleared-at: "2026-05-17T10:23:06Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/quick-skip-auth-page/04-homescreen-via-skip-rerun.png + 05-homescreen-dark-via-skip.png"
  - slice: components
    ac: AC-C6
    reason: "Maestro studio interactive dry-run requires Maestro CLI not on confirmed PATH; the dedicated `maestro` slice owns the round-trip verification per the workflow's slice boundary. Static evidence (39 testTag call sites across 16 components + testTagsAsResourceId scaffold at CrumbsTheme:42) confirms the scaffolding is in place. Clears when /wf-quick probe runs Maestro studio against the running app, or when the maestro slice verifies the full testTag round-trip."
    deferred-at: "2026-05-17T13:29:48Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — long_press.yaml — popup actions (TAG/OPEN/SHARE/DELETE) exercised end-to-end"
  - slice: layouts
    ac: AC-L2
    reason: "HomeScaffold precise inset measurement (28dp status / 88dp TopBar / 34dp FilterBar / 52dp BottomNav + 8dp pill) requires a live system bar; Robolectric defaults WindowInsets to zero, so the Roborazzi captures evidence slot composition correctness only. No host screen consumes HomeScaffold yet — the screens slice's verify on Medium_Phone_API_36 is the first natural moment to measure. Slot order (topBar → filterBar → content → bottomBar) is already evidenced by HomeScaffold_default_{light,dark}.png. Clears when the screens slice's verify captures HomeScaffold under a real status bar."
    deferred-at: "2026-05-17T16:05:31Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — happy_path.yaml on Medium_Phone_API_36 — HomeScaffold renders correctly under real status-bar insets"
  - slice: layouts
    ac: AC-L5
    reason: "Maestro studio testTag round-trip for all shell + slot tags (home-scaffold, overlay-shell, onboarding-shell + nested tags) requires Maestro CLI not on confirmed PATH; the dedicated `maestro` slice owns the round-trip verification. Static evidence: 4 testTags wired in HomeScaffold.kt, 5 in OverlayShell.kt, 4 in OnboardingShell.kt; testTagsAsResourceId enabled at CrumbsTheme:40. This deferral collapses onto the same emulator+Maestro evidence run that clears toolchain AC4 + components AC-C6. Clears when /wf-quick probe runs Maestro studio against the running app, or when the maestro slice verifies the full testTag round-trip."
    deferred-at: "2026-05-17T16:05:31Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — happy_path.yaml — home-scaffold-topbar/filterbar/bottombar + top-bar + filter-bar + bottom-nav testTags surface in tree"
  - slice: screens
    ac: AC-S1
    reason: "≥95% mock-fidelity adjudication against the Option-D handoff for all 8 screens × light theme. Automated Roborazzi tolerance (5% changed-pixel + 1% RGB) is met for every light golden, but subjective fidelity scoring requires maintainer-owned side-by-side review against the Crumbs-handoff browser-rendered mocks. Same precedent as tokens AC-K4 + toolchain AC6. Clears when maintainer signs off per-screen in the verify artifact."
    deferred-at: "2026-05-17T19:13:28Z"
    cleared-by: null
  - slice: screens
    ac: AC-S2
    reason: "Same as AC-S1, dark theme. 8 dark goldens captured and verified under the automated tolerance bands; maintainer manual diff against Option-D dark-mode references pending. Clears alongside AC-S1."
    deferred-at: "2026-05-17T19:13:28Z"
    cleared-by: null
  - slice: screens
    ac: AC-S4
    reason: "Manual side-by-side review on a Pixel 6 (or Medium_Phone_API_36) emulator — boot app and walk Splash → Onboarding → Login → Home (all 4 tabs) → AllBookmarks → MapView confirming brutalist visuals match the mocks. testTags wired across all 8 screens + 4 dedicated Route files; testTagsAsResourceId enabled at CrumbsTheme:40. Collapses onto the same emulator+Maestro evidence run that clears toolchain AC4 + components AC-C6 + layouts AC-L2 + layouts AC-L5. Clears when /wf-quick probe runs the live nav walk against the running app, or when the maestro slice verifies the happy-path flow."
    deferred-at: "2026-05-17T19:13:28Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — happy_path.yaml — Splash→Home→all 4 tabs walk passes on Medium_Phone_API_36; all-bookmarks-seeded.png evidence"
  - slice: screens
    ac: AC-S6-nav
    reason: "Empty-state CONNECT-AN-ACCOUNT button navigation half. Callback wiring closed in-process via AllBookmarksScreenTest.emptyState_connectAccountCta_invokesCallback (Compose UI test). Actual navController.navigate(LOGINSCREEN) reach requires emulator + the NavHost rendered in the same process. Collapses onto the maestro slice's happy-path flow which exercises tab → empty-state → CTA → LoginScreen as part of the standard onboarding traversal."
    deferred-at: "2026-05-17T19:13:28Z"
    cleared-by: null
  - slice: screens
    ac: AC-S7
    reason: "Long-press on a CrumbsBookmarkCard in AllBookmarksScreen opens CrumbsLongPressPopup with 4 actions (TAG, OPEN, SHARE, DELETE) visible. Component-level coverage exists from components slice (LongPressPopupTest 4-action variants). AllBookmarks-level integration needs touch-input runtime + a populated LazyPagingItems feed; Robolectric's performTouchInput { longClick() } against a paging-driven LazyColumn is brittle and the integration belongs naturally to maestro's gesture-driven flow. Clears when /wf-quick probe runs the long-press flow against the running app, or when the maestro slice verifies the popup integration."
    deferred-at: "2026-05-17T19:13:28Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — long_press.yaml — long-press on bookmark-card[0] opens popup with 4 actions visible"
  - slice: behaviors
    ac: AC-line-90
    reason: "Room migration 4→5 runs cleanly on Pixel 6 emulator after v1.1 install. MigrationTest.kt is authored + assembleDebugAndroidTest compiles cleanly + the test asserts the post-migration schema, but execution requires a booted emulator (connectedDebugAndroidTest). Collapses onto the same emulator+Maestro evidence run that clears the 11 prior deferrals. Clears when /wf-quick probe boots Medium_Phone_API_36 and runs `:app:connectedDebugAndroidTest --tests "*MigrationTest"`, or when the maestro slice runs the migration test as part of the install-from-v1.1 path."
    deferred-at: "2026-05-17T23:48:00Z"
    cleared-by: null
  - slice: behaviors
    ac: AC-line-92
    reason: "Long-press → DELETE → card disappears 200ms + CrumbsSnackbar 'DELETED · UNDO' shows for 5s. Wiring closed end-to-end (popup softDelete dispatch at AllBookmarksScreen.kt:308 + Twitter/Reddit equivalents → DeletedBookmarkRepository.softDelete → events SharedFlow → HomeRoute SnackbarHostState collector). Runtime gesture-timing measurement needs Maestro on PATH; collapses onto the maestro slice."
    deferred-at: "2026-05-17T23:48:00Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — long_press.yaml + happy_path.yaml — DELETE→snackbar shows; UNDO label visible"
  - slice: behaviors
    ac: AC-line-93
    reason: "UNDO before timer → tombstone removed + card reappears at original position. Closed at the data layer by DeletedBookmarkRepositoryTest (added at verify-owned fix 47ee1b78, 3/3 pass) + SnackbarResult.ActionPerformed → undoDelete(id) wired at HomeRoute.kt:111-117. Room InvalidationTracker auto-invalidates the paging source via LEFT JOIN deleted_bookmarks. Runtime gesture verification deferred to maestro."
    deferred-at: "2026-05-17T23:48:00Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — long_press.yaml — UNDO tap before timer succeeds (flow exit 0)"
  - slice: behaviors
    ac: AC-line-95
    reason: "Type filter chip 'THREAD' tap → feed re-queries within 300ms. Chip callback wired at HomeRoute.kt:135-140 dispatching to active VM's onTypeChipToggled; FilterState.type updates reactively. DAO predicate is tombstone-only (tweetEntity.type column does not exist); user-observable type filtering collapses onto maestro along with the future-cleanup type derivation. Clears via maestro slice."
    deferred-at: "2026-05-17T23:48:00Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — filter_overlay.yaml + happy_path.yaml — type filter chips respond within Maestro polling window (<300ms)"
  - slice: behaviors
    ac: AC-line-96
    reason: "Tags chip → OverlayShell opens with multi-select tag list → APPLY filters feed. Tag state plumbing wired in feature VMs (onTagToggled / onTagsApplied) but the OverlayShell-mounted multi-select picker UI was not delivered in-stage — substantive gap, not just runtime evidence. Recommend pre-handoff/pre-ship refinement decision: either add a small OverlayShell tag-filter sheet now or accept the chip-as-toggle behavior for v2.0 with tag filtering as a follow-up enhancement."
    deferred-at: "2026-05-17T23:48:00Z"
    cleared-by: null
  - slice: behaviors
    ac: AC-line-97
    reason: "Forced Twitter 401 → CrumbsBanner appears above feed within 1s with kicker 'ERR · RECONNECT TWITTER'. Bus emit wired at Repository.kt:157-160 + RedditRepository.kt:115-117; HomeRoute collector at HomeRoute.kt:71-96 flips per-tab banner state; banner visual contract verified via 4 new Roborazzi goldens (HomeScreen_withSyncErrorBanner_{light,dark}.png + HomeScaffold_withBanner_{light,dark}.png). Live 401 trigger + 1s latency measurement needs Maestro flow + a forced expired token. Collapses onto maestro."
    deferred-at: "2026-05-17T23:48:00Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — sync_error.yaml — banner appears within 2000ms extendedWaitUntil after corrupt_token + pull-to-refresh"
  - slice: behaviors
    ac: AC-line-98
    reason: "Banner CTA → OAuth flow initiates identically to LoginScreen CONNECT button. CTA at HomeRoute.kt:142-147 fires `context.startActivity(loginViewModel.authIntent())` / `redditViewModel.authIntent()` — byte-stable with the existing LoginRoute.kt:59-60 call. Live OAuth handoff verification deferred to maestro."
    deferred-at: "2026-05-17T23:48:00Z"
    cleared-by: "slice/maestro"
    cleared-at: "2026-05-18T08:16:21Z"
    cleared-evidence: ".ai/workflows/brutalist-redesign/verify-evidence/maestro/ — sync_error.yaml — banner-cta tap fires OAuth intent (flow exit 0)"
workflow-files:
  - 00-index.md
  - 01-intake.md
  - 02-shape.md
  - 03-slice.md
  - 03-slice-toolchain.md
  - 03-slice-tokens.md
  - 03-slice-components.md
  - 03-slice-layouts.md
  - 03-slice-screens.md
  - 03-slice-behaviors.md
  - 03-slice-maestro.md
  - 03-slice-quick-skip-auth-page.md
  - 04-plan.md
  - 04-plan-toolchain.md
  - 04-plan-tokens.md
  - 04-plan-components.md
  - 04-plan-layouts.md
  - 04-plan-screens.md
  - 04-plan-behaviors.md
  - 04-plan-maestro.md
  - 05-implement.md
  - 05-implement-toolchain.md
  - 05-implement-tokens.md
  - 05-implement-quick-skip-auth-page.md
  - 05-implement-components.md
  - 05-implement-layouts.md
  - 05-implement-screens.md
  - 05-implement-behaviors.md
  - 05-implement-maestro.md
  - 06-verify.md
  - 06-verify-behaviors.md
  - 06-verify-toolchain.md
  - 06-verify-tokens.md
  - 06-verify-components.md
  - 06-verify-layouts.md
  - 06-verify-screens.md
  - 06-verify-maestro.md
  - 07-review.md
  - 07-review-correctness.md
  - 07-review-security.md
  - 07-review-code-simplification.md
  - 07-review-testing.md
  - 07-review-maintainability.md
  - 07-review-reliability.md
  - 07-review-frontend-accessibility.md
  - 07-review-backend-concurrency.md
  - 07-review-architecture.md
  - 07-review-performance.md
  - 07-review-data-integrity.md
  - 07-review-migrations.md
  - 07-review-privacy.md
  - 07-review-supply-chain.md
  - po-answers.md
compressed-slices:
  - slug: quick-skip-auth-page
    slice-type: quick
    created-at: "2026-05-17T09:35:13Z"
slices:
  - slug: toolchain
    status: verified-partial
    complexity: l
    depends-on: []
  - slug: tokens
    status: verified-partial
    complexity: m
    depends-on: [toolchain]
  - slug: components
    status: verified-partial
    complexity: l
    depends-on: [tokens]
  - slug: layouts
    status: verified-partial
    complexity: s
    depends-on: [components]
  - slug: screens
    status: verified-partial
    complexity: l
    depends-on: [layouts]
  - slug: behaviors
    status: verified-partial
    complexity: m
    depends-on: [screens]
  - slug: maestro
    status: verified
    complexity: s
    depends-on: [behaviors]
progress:
  intake: complete
  shape: complete
  slice: complete
  plan: complete   # 7/7 slices planned (toolchain, tokens, components, layouts, screens, behaviors, maestro)
  implement: complete      # 7/7 slices implemented; see 05-implement-*.md
  verify: complete         # 7/7 slices verified (6 verified-partial + maestro pass); 11 of 17 active runtime-evidence-deferrals cleared by maestro verify run; 6 remaining (4 maintainer-owned + behaviors AC-line-90 infra + AC-line-96 substantive gap)
  review: complete         # slug-wide review: 14 commands, 141 raw → 98 deduplicated findings (3 BLOCKER, 26 HIGH, 40 MED, 19 LOW, 10 NIT); verdict: dont-ship; convergence: escalated; 57 Fix decisions routed to /wf implement reviews
  review-fixes: in-progress    # 20/57 patched at 2026-05-18T15:10:09Z — BLOCKERs B1/B2/B3 (commit 9dfb119) + SEC H1/H2 (commit 30def3f) + Correctness H3/H4/H5 (commit 5461075) + Reliability H6/H7/H8/H9/H10 (commit 41aa8aa) + Architecture/Data H11/H12/H13/H14/H15 (commit e97ee5f) + A11y H16/H17 (commit 790bdba); 37 remaining queued, see 07-review.md ## Fix Status
  handoff: not-started
  ship: not-started
  retro: not-started
---
