# Product Owner answers — brutalist-redesign

A cumulative log of product-owner answers across stages. Newest at the bottom. Every entry: stage, timestamp, question, answer.

## intake — 2026-05-16T21:44:39Z

### Raw request
> "read the files within Crumbs-handoff especially 'crumb design handoff.html' and 'crumb option D.html' the plan is to replace the current app design with the design within Crumbs-Handoff matching it pixel for pixel"

### Batch A (structured) — 2026-05-16T21:44:39Z
- **Branch strategy:** Dedicated → `feat/brutalist-redesign`, base `main`.
- **Appetite:** Large (multi-day, will need slicing).
- **Review scope:** Slug-wide (one `07-review.md` against cumulative branch diff; handoff gates on that single review).

### Batch A.2 (design-specific) — 2026-05-16T21:44:39Z
- **Canonical accent:** Orange `#FF5A1F` (matches Option D screen defaults; supersedes the handoff doc cover's neon).
- **Theme scope:** Both light + dark — `LightColors` and `DarkColors` per `CrumbsColors.kt` handoff.
- **Screens in scope:** Home/Feed, AllBookmarks, MapView, Onboarding + Splash + Login — **all six existing screens**.
- **Verification:** Screenshot tests **and** Maestro flows **and** visual review (all three layers).

### Batch B — stack confirmation (partial) — 2026-05-16T21:44:39Z
- **Confirmed available on PATH:** `android` CLI and `lazylogcat` CLI, both with their Claude skills installed. → Verify stage will route SDK/emulator/install/run through `android-cli` and runtime log capture through `lazylogcat`.
- Remaining Batch B questions still pending: source-of-truth scope, non-goals, success criteria, timeline/constraints, behavioral vs visual treatment.

### Batch B (final) — 2026-05-16T21:44:39Z
- **Source-of-truth:** Only `Crumbs Design Handoff.html` + `Crumbs Option D.html` are canonical. `option-a..f.jsx` and `tweaks-panel.jsx` are reference / discarded.
- **Non-goals (agreed):** no DB schema changes; no API/integration changes (Firestore, Reddit, Twitter, etc. stay as-is); no navigation-graph changes beyond what new layouts require; no new features (search behavior, filtering logic, bookmark CRUD remain functionally identical).
- **Success criteria:** all six screens match the HTML mocks at **≥95% visual fidelity** on both light + dark themes at 412×920; screenshot tests green; Maestro happy-path flows clean; no JUnit regressions; emulator install + lazylogcat verification on at least one device profile.
- **Timeline / versioning:** no fixed deadline. Version bump "as appropriate" — ship stage to pick (working assumption: `versionCode 3 / versionName 2.0` to mark the redesign milestone).
- **Behavioral additions:** all behaviors implied by the handoff are **in-scope and fully wired** — long-press menu, filter chip bar, overlay shells, any new bottom-nav destinations. No stubs.

### Freshness research notes — 2026-05-16T21:44:39Z
- Screenshot library: **Roborazzi** preferred over Paparazzi for this workflow — it integrates with Robolectric + Hilt and supports interaction-driven snapshots (we need this since behaviors are wired). Final pick deferred to `/wf plan`.
- Maestro setup: requires `Modifier.semantics { testTagsAsResourceId = true }` on the root Compose tree and `Modifier.testTag(...)` on each asserted node. No build.gradle test deps. Add to layout shells during implement.

## shape — 2026-05-16T21:44:39Z

### Round 1 — what the redesign does
- **Long-press menu actions:** Open/Read, Share, Delete, Edit tags / Add to collection (all four).
- **Filter chips filter:** Type, Tags, and Collection (NOT source — source is already represented by the bottom-nav tabs).
- **Onboarding trigger:** First app launch only (DataStore flag).
- **Save input model:** Integration auto-pull only — no manual URL paste. (No share-intent entry point added; Twitter/Reddit sync remains the only ingestion path.)

### Round 2 — how the redesign behaves
- **Delete UX:** Soft delete + brutalist Snackbar with "DELETED · UNDO" (~5s timeout).
- **Filter application:** Instant for single-select (Type); overlay shell for multi-select (Tags, Collection).
- **Toolchain upgrade:** Inline first slice of THIS workflow — Kotlin 2.0.21 → 2.3.21, AGP 8.0.2 → 9.1.1, Compose → 1.11.1, Roborazzi 1.7.0 → 1.37.0. Visual work is blocked until builds + existing 154 Roborazzi tests pass on the new chain.
- **MapView treatment:** Keep as placeholder; apply brutalist styling to a "COMING SOON" empty-state. No maps SDK added.

### Round 3 — what the redesign looks like
- **Long-press surface:** Contextual popup anchored to the card (NOT the OverlayShell bottom-sheet). Compact, finger-positioned, 4 actions.
- **Bottom nav (final):** Twitter, Reddit, All, Map — same 4 tabs as today, redressed.
- **Empty-state copy:** "No crumbs yet" displaySmall + "CONNECT AN ACCOUNT" CTA → routes to LoginScreen. (Supersedes the handoff's "Drop your first crumb" copy since there is no in-app save.)
- **Loading state:** Sharp-edged skeleton blocks with a single subtle horizontal scan-line as the only motion.

### Round 4 — what can go wrong
- **Soft-delete re-sync race:** Bookmark stays deleted. **Targeted DB schema addition** — a `deleted_ids` tombstone table (intake's "no schema changes" non-goal explicitly relaxed for this one table).
- **Font fallback:** Bundle BOTH Funnel Display and IBM Plex Mono in `res/font/` (OFL allows). No Downloadable Fonts path. Offline-safe by construction.
- **Sync error UI:** Inline ink-stroked banner pinned above the feed of the affected tab — "ERR · RECONNECT TWITTER" kicker + button. Persistent until resolved.
- **Roborazzi tolerance:** Per-pixel RGB threshold **1%** + **5%** changed-pixel allowance (Roborazzi defaults). Catches structural drift; tolerates anti-alias/font-hinting variance across emulator runs.

### Round 5 — scope boundaries
- **Orphan-component cleanup:** Delete the 13 unused components outright — CrumbsCard, CrumbsDialog, CrumbsDivider, CrumbsFilterChip, CrumbsSortMenu, CrumbsTabBar, CrumbsTagChip, CrumbsTextField, EngagementMetrics, MediaCarousel, SearchSuggestions, ThreadIndicator, VideoPlayer. Their Roborazzi tests retire with them. (Note: CrumbsFilterChip and CrumbsTabBar may be *rebuilt* under new names if the brutalist filter bar / tab pattern requires; but the existing implementations are scrapped.)
- **CrumbsColors migration:** Hard cutover. Replace the data class fields inline; update every caller in the same slice. No deprecation window.
- **Accompanist Pager:** Migrate OnboardingScreen to `androidx.compose.foundation.pager.HorizontalPager`. MediaCarousel is being deleted (orphan), so its dependency goes with it.
- **Canonical device for goldens:** Pixel 6 — `w411-h891-xxhdpi`, API 34. Single device profile, single golden set. No tablet variant in v1.

## slice — 2026-05-16T22:37:59Z

### Slicing strategy
- **Granularity:** thin — the 6 natural delivery clusters from shape stay separate (toolchain, tokens, components, layouts, screens, behaviors).
- **Best-first slice:** toolchain (risk-first). Toolchain upgrade lands ahead of any visual work; existing 154 Roborazzi goldens regenerated on the new chain *with the old visuals intact* prove the chain is healthy before redesign work begins.
- **`deleted_bookmarks` DB schema:** colocated in the behaviors slice — schema, Room migration, DAO, tombstone-aware sync filter, and the soft-delete UX all ship as one self-contained change.
- **Maestro flows:** dedicated final slice — all 4 yaml flows (happy_path, long_press, filter_overlay, sync_error), the debug-only fake-data injector, and the `lazylogcat`/`android-cli` integration glue ship after every UI surface is final. Total slice count: **7**.

## plan — toolchain — 2026-05-16T22:37:59Z

### Round 1 — dependency / convergence approach
- **Compose dependency management:** adopt **Compose BOM `2026.05.00`** as the single source of version truth. Every `androidx.compose.*` dependency drops its direct version pin; the catalog references `platform(libs.androidx.compose.bom)`.
- **Material3:** governed by the Compose BOM. Drops both the `material3:1.2.0` direct pin in `app` and the separate `material3:2024.02.00` BOM in `core/designsystem`. Material3 1.4.0 ships via the Compose BOM.
- **Roborazzi golden regeneration:** **one big commit at the end of the slice**, after every version bump is in and the build is green. Commit title: `chore: regenerate roborazzi goldens for new toolchain`. Single reviewable diff for all 133 PNGs.
- **Coil:** adopt **Coil 3.x** (latest stable 3.4.0+). Major-version bump; uses the `coil3` namespace (coexistable with `coil` if needed, but we cut over). Touches `GradientImage.kt` + `CrumbsBookmarkCard.kt` import paths; surface area is small since the components slice will rewrite both shortly.

### Round 2 — sequencing + risk
- **KSP × Kotlin 2.3.21 risk mitigation:** **spike on a throwaway commit first**. Before any mainline toolchain work, push a single commit bumping Kotlin + KSP and verifying `./gradlew :app:assembleDebug` passes with Hilt+KSP active. If it fails, immediately escalate; do not proceed.
- **`core/pref`:** bump `compileSdk 33 → 34` and `jvmTarget 1.8 → 17` in this slice. Aligns every module on the same SDK + JVM target.
- **Pre-upgrade audits (all four selected):**
  - Grep `@JvmInline` across the codebase for value-class constructors that may go private in Kotlin 2.3.
  - Grep `Class.forName("com.github.jayteealao...")` for reflection that may break under AGP 9 R8 repackaging default.
  - Verify the AGP 9.1.1 release notes re: whether `id 'org.jetbrains.kotlin.android'` must be removed.
  - Verify `org.jmailen.kotlinter v3.12.0` works on Kotlin 2.3.21; bump to latest 3.x/4.x if not.
- **Smoke test scope:** full **emulator boot via `android` CLI** → `./gradlew :app:installDebug` → manual nav through Splash → Onboarding → Login → Home (all 4 tabs) → back, with `lazylogcat -t crumbs` capturing logs. Visual review confirms v1.1 design renders unchanged.

### Round 3 — test infra + CI
- **CI workflow (`.github/workflows/pr_check.yml`):** full update — JDK **17**, Android SDK **34**, add `lintDebug`, `kotlinterCheck`, and `verifyRoborazziDebug` as PR gates. Closes the AC-T2 / AC-T3 verification gaps the audit identified (today CI runs `clean assembleDebug` only).
- **Robolectric `@Config(sdk = ...)`:** bump **all 17 test classes** from `[33]` to `[34]` so Robolectric runtime SDK aligns with `compileSdk 34`. Single search-and-replace.
- **Golden directory:** **keep `core/designsystem/src/test/screenshots/`** (current non-default path). Avoid the renaming churn that would obscure the 133-PNG regeneration diff.
- **`org.jetbrains.kotlin.android` plugin removal:** **verify against AGP 9.1.1 official release notes first** before removing. If still required, leave it. If removed by AGP 9, drop it from root + all 6 module build.gradle files in the same commit.



## implement — toolchain — 2026-05-17T00:38:34Z

### Execution pace + env handling (pre-flight)
- **Pace:** Drive end-to-end, pause only on failure.
- **Env handling:** Try the build anyway, deal with errors as they come.

### Plan-deviation decisions raised mid-implementation
- **AGP-Gradle-Kotlin coupling vs bisectable per-step commits:** Combine the coupled bumps into 2 commits, keep the rest sequential. (Ended up needing six commits total to hit green; commits 1 and 2 each bundle a coordination knot, commits 3–6 are one concern each.)
- **Kotlin 2.3.21 (planned) is incompatible with AGP 9.1.1's bundled KGP 2.2.10:** Accept AGP 9.1.1 + Kotlin 2.2.10 (downgrade locked Kotlin). The audits confirmed no Kotlin-2.3-specific language features in use, so the downgrade is lossless.
