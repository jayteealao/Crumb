---
schema: sdlc/v1
type: index
slug: brutalist-redesign
title: "Replace Crumbs app design with the Brutalist Mono (Option D) handoff, pixel-for-pixel"
status: active
current-stage: verify
stage-number: 6
created-at: "2026-05-16T21:44:39Z"
updated-at: "2026-05-17T10:23:06Z"
selected-slice: tokens
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
next-command: wf-review
next-invocation: "/wf review brutalist-redesign tokens"
runtime-evidence-deferrals:
  - slice: toolchain
    ac: AC4
    reason: "Maestro testTag round-trip needs a Modifier.testTag(...) in the codebase; testTags are introduced systematically in the maestro slice. Scaffolding (testTagsAsResourceId at CrumbsTheme:42) is in place; Maestro can already address the running app."
    deferred-at: "2026-05-17T01:17:12Z"
    cleared-by: null
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
  - 05-implement.md
  - 05-implement-toolchain.md
  - 05-implement-tokens.md
  - 05-implement-quick-skip-auth-page.md
  - 06-verify.md
  - 06-verify-toolchain.md
  - 06-verify-tokens.md
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
    status: defined
    complexity: l
    depends-on: [tokens]
  - slug: layouts
    status: defined
    complexity: s
    depends-on: [components]
  - slug: screens
    status: defined
    complexity: l
    depends-on: [layouts]
  - slug: behaviors
    status: defined
    complexity: m
    depends-on: [screens]
  - slug: maestro
    status: defined
    complexity: s
    depends-on: [behaviors]
progress:
  intake: complete
  shape: complete
  slice: complete
  plan: in-progress   # 2/7 slices planned (toolchain, tokens); 5 remaining plans deferred (rolling plans)
  implement: in-progress   # 2/7 slices implemented (toolchain, tokens); see 05-implement-{toolchain,tokens}.md
  verify: in-progress      # 2/7 slices verified-partial (toolchain, tokens); 4 runtime-evidence-deferrals
  review: not-started
  handoff: not-started
  ship: not-started
  retro: not-started
---
