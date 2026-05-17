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

## verify — toolchain — 2026-05-17T01:17:12Z

### Interactive AC triage
- **AC5 (emulator smoke):** I drive partial automation, user confirms visuals. Visual confirmation evidence: `verify-evidence/toolchain/02-home.png` (Crumbs Login/Connect screen rendered with v1.1 cyan accent + cut-corner dark navy buttons).
- **AC4 (Maestro testTag round-trip):** User confirmed Maestro CLI is installed; ran `maestro hierarchy` dump showing Maestro can address the running app. Full testTag round-trip deferred to maestro slice (no testTag values exist in current codebase to exercise the scaffolding).
- **AC6 (manual visual diff of regenerated goldens vs pre-bump):** User will run the diff and report back. Recorded as `runtime-evidence-deferrals[1]` in 00-index.md.

### Verify-owned fix
- **ROOM-NULL-1:** `TweetDao.getLatestBookmark()` widened from `TweetEntity` to `TweetEntity?` (Room 2.8.4 enforces non-null query return contracts where 2.4.3 silently returned null; the app crashed on first launch on a fresh emulator). Both callers in Repository.kt already null-checked the field, so the widening is safe. Committed at `6148b61`.

### Plan deviations noted in verify
- Runtime smoke ran on `Pixel_9_Pro` (Android 16 / API 36) instead of plan-canonical `Pixel_6_API_34` because the canonical AVD isn't installed on this machine. Runtime smoke purpose unaffected by AVD substitution; goldens still used the Pixel 6 spec via Robolectric (no emulator).

---

## stage: plan — slice: tokens — 2026-05-17T02:03:08Z

### Round 1 — token surface + cutover ambiguities

- **Q: Accent color holds — locked orange (#FF5A1F) vs. handoff JSX default lime (#D6FF00)?**
  **A:** Stick with locked orange (#FF5A1F). onAccent stays #0A0A0A (ink). Lime is documented as an alt in the handoff but the shape decision holds; no re-opening of the locked decision.

- **Q: CrumbsStroke.kt (new) scope — full file, widths only, or defer to components?**
  **A:** Yes, full CrumbsStroke.kt this slice. Includes hairline=1dp, regular=1.5dp, emphasis=2dp, offsetX=6dp, offsetY=6dp. LocalCrumbsStroke wired into CrumbsTheme. Downstream components can consume immediately.

- **Q: AC-K5 tokens-preview Roborazzi golden — minimal/full/skip?**
  **A:** Skip the preview composable; re-purpose AC-K5 as a maintainer-driven manual diff against `Crumbs-handoff/crumbs/project/handoff-tokens.jsx`. Will register as a runtime-evidence-deferral if not cleared before ship.

- **Q: FontLoadingStrategy for bundled res/font fonts — Async/Blocking/OptionalLocal?**
  **A:** Switch every Font(...) to default Blocking (omit the loadingStrategy parameter). Matches Google's bundled-resource guidance and the offline-rendering NFR. Current Async usage was an oversight.

### Round 2 — font weights, typography mapping, dead-orphan timing

- **Q: Funnel Display weights to bundle — match handoff (400/500/700), keep 600 for safety, or follow spec (400/500/700/800)?**
  **A:** 400/500/700 only — match handoff exactly. Delete funnel_display_semibold.ttf. Don't add extrabold (no handoff style uses it). APK savings: ~80KB.

- **Q: IBM Plex Mono weights — 400/500/700 vs. 400/700 only?**
  **A:** 400/500/700 — match handoff. ~120KB total. Skipping medium would force synthetic font weight thickening which looks wrong.

- **Q: Typography 11→7 rename approach — mechanical rename table / both scales / aliases?**
  **A:** Mechanical rename via lookup table. Apply Edit replace_all per pair across surviving consumers. Components will look intentionally wrong (sans body → mono body) in the intermediate state; components slice rewrites every component anyway, so cost is zero.

- **Q: app/src/main/java/com/github/jayteealao/crumbs/ui/theme/*.kt dead orphans — delete in tokens, components, or later?**
  **A:** Delete in this slice (early step). Sub-agent confirmed zero imports, zero risk. Cleanest — no stale Material code visible in subsequent slice reviews.

### Round 3 — cutover mechanics + scope pull-forward

- **Q: 13 orphan components — rename-then-let-components-delete, delete-now, or suppress?**
  **A:** Delete the 13 orphans NOW in tokens slice (and their test files). Pulled forward from the components slice. **Scope shift: AC-C1 in the components slice becomes verification-only (deletions already done); components slice now focuses purely on rebuilding 8 active brutalist composables + 4 new components.**

- **Q: accentAlpha inline value at the 2 CrumbsIconButton sites — 0.1f / 0.0f / TODO?**
  **A:** Keep 0.1f for both. `accent.copy(alpha = 0.1f)` for container and disabled-container. Preserves current visual at those v1.1-layout sites in Roborazzi goldens; components slice can make the proper brutalist choice during rebuild.

- **Q: Goldens regeneration — verify-first (capture diff) or record directly?**
  **A:** Record directly. No verify-first diff capture. Matches what the toolchain slice did. Lose the per-image change audit trail; gain ~10 min.

- **Q: Maestro testTag — add `testTag("app_root")` to CrumbsTheme now, or defer?**
  **A:** Yes — add a single `app_root` testTag. One-line change in CrumbsTheme alongside the existing semantics scaffolding. Gives Maestro a stable root target before per-element tags arrive in components/maestro slices. Partially clears the AC4 runtime-evidence-deferral.

### Cross-slice impact captured

- **components slice scope reduced.** 13 orphan deletions + 13 test deletions now done in tokens. Components-slice plan (when drafted) must reflect this. Master plan index updated to flag the shift.
- **AC-K5 deferral risk.** Manual handoff-diff will register as a runtime-evidence-deferral on 00-index.md at verify if maintainer doesn't close it before ship. Same handling pattern as AC4 / AC6 from the toolchain slice.

---

## stage: plan — slice: components — 2026-05-17T11:11:14Z

### Round 1 — architecture + scope

- **Q: How should rebuilt components treat the existing Material3 wrappers (Button, Scaffold, TopAppBar, AlertDialog, DropdownMenu, NavigationBar, CircularProgressIndicator)?**
  **A:** Mixed — case-by-case. Plan picks per-component: strip Button/IconButton/ProgressIndicator/TopBar/BottomNav/TagEditorDialog (rebuild from Box+Modifier primitives); keep Scaffold passthrough (purely structural, no chrome to leak). The mixed-stance posture is more pragmatic than a blanket "strip everything" rule.

- **Q: QuickActionMenu disposition — retire, keep both, or defer?**
  **A:** Retire — delete `QuickActionMenu.kt` + its golden PNGs + its @Test methods in `ActionComponentsTest.kt`. CrumbsLongPressPopup is the single long-press primitive. Slice spec line 90 already flagged this as "likely outcome"; PO confirms.

- **Q: Long-press popup layout — handoff Screen 5 grid vs slice spec vertical list?**
  **A:** Handoff wins — 2×2 grid with TAG/SHARE/ARCHIVE/DELETE (TAG = accent primary, DELETE = #a40000 text). Overrides slice spec line 59 verbatim. **Cross-slice impact:** ARCHIVE is a new behavior (hide-from-feed) that the handoff introduces; flagged for the behaviors slice to wire — components slice ships visual shell only.

- **Q: Commit cadence?**
  **A:** Grouped by family — ~6 commits (Phase A setup + 5 family commits + 1 goldens regen). Families: chrome primitives (Button/IconButton/ProgressIndicator), layout chrome (Scaffold/TopBar/BottomNav), cards & states (BookmarkCard/EmptyState/LoadingCard/GradientImage/UserProfileDisplay), dialog/menu (TagEditorDialog + QuickActionMenu retire), new components (FilterBar/Snackbar/Banner/LongPressPopup), goldens.

### Round 2 — mechanics

- **Q: LoadingCard scan-line determinism mechanism?**
  **A:** Hoist time as parameter — add `scanLinePositionFraction: Float? = null` to LoadingCard. Composable uses `rememberInfiniteTransition` by default; tests pass a constant (e.g. 0.5f) to override. Simpler than `mainClock.advanceTimeBy()`; future-proof for screen-level tests.

- **Q: List parameter type for FilterBar/Popup/Dialog?**
  **A:** kotlinx.collections.immutable.ImmutableList. Adopt the dependency this slice. ImmutableList<T> is @Immutable; Compose treats it as stable. Twitter Compose lint rules verify this. Adds ~50KB dep.

- **Q: CrumbsScaffold rebuild posture (with mixed Material3 stance)?**
  **A:** Keep Material3 Scaffold passthrough — purely structural, no chrome to leak. Override containerColor / contentColor to CrumbsTheme; add testTag("scaffold-root"). Saves ~80 lines of Box/Column rewrite.

- **Q: Snackbar + Banner visual contract (no handoff mocks)?**
  **A:** Brutalist token defaults. Snackbar: black ink bg, 1.5dp accent border, mono uppercase text + action, bottom-anchored. Banner: surface bg, 1.5dp ink top+bottom border, kicker text + accent CTA, sticky. Document shapes in plan; no atomic mock review needed.

### Round 3 — testing + edges

- **Q: AC #6 testTag verify method when Maestro CLI isn't in this slice's stack?**
  **A:** Maestro studio dry-run — use as read-only inspector. Already installed per toolchain verify. One-shot end-of-slice run: `maestro studio` → navigate via Skip Auth (Debug) → inspect HomeScreen hierarchy → confirm testTags queryable → screenshot the hierarchy panel as evidence. No flow files yet.

- **Q: Roborazzi `compareOptions` tolerance location?**
  **A:** Slice-local in core/designsystem/build.gradle. Add `roborazzi { compareOptions = ChangeThreshold(0.05f, PixelMatcher(0.01f)) }`. Scoped change matching the per-module Roborazzi plugin application. Future modules add per-module if needed.

- **Q: Brutalist offset shadow — Modifier.dropShadow (Compose 1.11+ native) vs sibling Box trick?**
  **A:** Modifier.dropShadow — Compose 1.11 native (Aug-25 release). Use `Modifier.dropShadow(DpOffset(6.dp, 6.dp), color=ink, blurRadius=0.dp, shape=RectangleShape)` on BookmarkCard pressed state, LongPressPopup container, TagEditorDialog. One line, no extra layout nodes. Phase-A scratch verifies API availability.

- **Q: Golden coverage strategy?**
  **A:** Per-component meaningful-state matrix × 2 themes (~24 new goldens). FilterBar gets {empty, single-selected, multi-selected, with-count} × {light, dark} = 8. Snackbar {default, with-action} × 2 = 4. Banner {sync-error, success} × 2 = 4. LongPressPopup {default, danger-pressed} × 2 = 4. LoadingCard {has-image, no-image} × 2 = 4 (with hoisted scanLinePositionFraction = 0.5f).

### Cross-slice impact captured

- **ARCHIVE action introduced this slice (visual only).** Per handoff Screen 5, the long-press popup has 4 actions TAG/SHARE/ARCHIVE/DELETE; ARCHIVE (hide-from-feed) is new. CrumbsLongPressPopup ships the visual button this slice; **behavioral wiring (tombstone-like soft-archive, retrievability) defers to behaviors slice.** Behaviors slice plan, when drafted, must include this scope.
- **BookmarkCard onLongPress API widens** from `(Bookmark) -> Unit` to `(Bookmark, Offset) -> Unit`. Internal API; call sites updated in same commit. No feature-module ripple (zero feature/* imports of CrumbsBookmarkCard).
- **`kotlinx.collections.immutable` adoption** is workflow-wide once introduced. Downstream slices should default to ImmutableList for new component parameters.
- **`Modifier.dropShadow` becomes the brutalist offset-shadow primitive** for the rest of the workflow. Layouts/screens slices should reuse the pattern.

---

## 2026-05-17T14:55:21Z — stage: plan, slice: layouts (Round 1 + 2, 8 answers)

### Round 1 — Scope and architecture

- **Q1 (EdgeToEdge):** Where should the `enableEdgeToEdge()` call land?
  **A:** MainActivity in this slice. Co-locate with HomeScaffold's edge-to-edge assumption. One-line `app/` touch is acceptable.

- **Q2 (OverlayShell technique):** in-tree composition or Popup wrapper?
  **A:** In-tree `Box` + `AnimatedVisibility(slideInVertically + fadeIn)` + `BackHandler`. Better IME + a11y story; rejects Material3 ModalBottomSheet.

- **Q3 (OnboardingShell pager slot shape):** Three API shapes considered.
  **A:** `pages: ImmutableList<@Composable () -> Unit>` + `pagerState: PagerState = rememberPagerState(pageCount = { pages.size })`. Shell internally renders Compose-native `HorizontalPager`. Caller passes pre-built page composables.

- **Q4 (filterBar slot shape):** required-with-default vs nullable?
  **A:** Nullable — `filterBar: (@Composable () -> Unit)? = null`. Cleaner call sites for screens without filters.

### Round 2 — Implementation and testing

- **Q5 (Backdrop dismiss mechanism):** `Modifier.clickable(indication = null)` or `pointerInput { detectTapGestures }`?
  **A:** `Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss() }` with `Modifier.semantics { contentDescription = "Dismiss overlay" }`. Better TalkBack semantics.

- **Q6 (Roborazzi WindowInsets strategy):** stub via test rule, accept 0, or hoist as test param?
  **A:** Hoist as test parameter; accept `WindowInsets(0)` default in goldens. Deterministic. AC-2's "28dp gap" measurement transfers to a runtime-evidence-deferral at verify, cleared by maestro slice or `/wf-quick probe`.

- **Q7 (AC-3 backdrop dismiss test):** ship in layouts slice or defer to maestro?
  **A:** Ship in layouts slice. `OverlayShellTest.kt` gains a non-Roborazzi Compose UI test that performs `onNodeWithTag("overlay-shell-backdrop").performClick()` and asserts the `onDismiss` lambda fired. Closes AC-3 within the slice.

- **Q8 (OnboardingShell footer layout):** single internally-composed Row or fully caller-supplied slot?
  **A:** Single internally-composed Row(SpaceBetween) with shell-owned `OnboardingPageIndicator` (3 RectangleShape pills, accent on `currentPage`) + optional `CrumbsButton(footerCtaText, onFooterCtaClick)`. Shell owns the indicator pattern; lowest call-site burden.

### Cross-slice impact captured

- **MainActivity edit is a one-line app-module touch from a slice nominally scoped to `core/designsystem/`.** Deliberate co-location so HomeScaffold's edge-to-edge assumption is satisfiable. Implement record + verify report must both surface this.
- **Interim visual artifact** between layouts merge and screens slice migrations: with `enableEdgeToEdge()` active but no screens yet consuming insets, screens render TopBar partially under the status bar. Acknowledged interim state; verify must not flag as regression.
- **AC-2 (inset-applied measurement) and AC-5 (Maestro testTag round-trip)** will register as runtime-evidence-deferrals at verify-stage, per workflow precedent.
- **Compose-native `HorizontalPager` adoption begins this slice** (OnboardingShell only). Accompanist Pager removal from `OnboardingScreen` itself is screens-slice work.

## plan — screens — 2026-05-17T16:15:10Z

### Discovery (Round 1) — 4 questions

- **Q1 (screen factoring for Hilt-free testing):** Route/Screen split, keep current with Hilt test infra, mixed, or VM-param at call site?
  **A:** Route/Screen split for all 8 screens. Each screen becomes a stateless `XxxScreen(uiState, onEvent)` composable + a thin `XxxRoute(viewModel = hiltViewModel())` wrapper. NavHost wires the Route; tests call the stateless Screen with fake state. Zero Hilt test infra introduced.

- **Q2 (screen-test location):** app/+features, app-only, or new core/screens-test?
  **A:** Tests live in `app/src/test/` + `feature/twitter/src/test/` + `feature/reddit/src/test/`. Apply Roborazzi plugin + dep bundle to all 3 modules (one-line plugin id + dep bundle copy from `core/designsystem/build.gradle` template).

- **Q3 (feature-screen rewrite depth):** light reskin, compose into HomeScaffold slot, or hard rewrite?
  **A:** Hard rewrite to match brutalist mock 1:1 for both `TwitterBookmarksScreen` and `RedditBookmarksScreen`. Replace internal `LazyColumn` body + scroll behavior to match Option D. ViewModels untouched (cross-module `BookmarksViewModel` reuse from Reddit preserved).

- **Q4 (LoginScreen layout):** full-bleed, OnboardingShell-wrapped, or HomeScaffold?
  **A:** Full-bleed brutalist. Keep current full-bleed structure (Box + GradientImage→brutalist background); rebuild visuals: kicker + wordmark + UserProfileDisplay per provider + two CONNECT-* `CrumbsButton`s. No shell wrapper. Bottom-nav must not appear on auth-gated screens.

### Discovery (Round 2) — 4 questions

- **Q5 (resplit decision):** single slice, screens-feed + screens-shells, or screens-features split?
  **A:** Keep as single slice. Route/Screen split + 16 goldens is mechanical, linear work; component set + layout shells are already done; this slice is composition only. Single atomic commit per implement-stage contract.

- **Q6 (Roborazzi tolerance for screen goldens):** 5%/1% (match components), 7%/1.5% relaxed, or 1%/0.5% strict?
  **A:** Match component goldens — `roborazzi.compare.changeThreshold=0.05` (5% changed-pixel; already in `gradle.properties`) + `SimpleImageComparator(maxDistance = 0.01f)` per test class (1% RGB). Consistent with components + layouts slices.

- **Q7 (AC-S1 mock-fidelity method):** maintainer manual diff, render-mocks-to-PNG, or generated side-by-sides?
  **A:** Maintainer-driven manual diff at verify time. AC-S1 ("≥95% match to Option D mock") registers as `interactive-verification: deferred` runtime-evidence-deferral, same precedent as tokens AC-K4 and toolchain AC6. Roborazzi captures the rendered screen; maintainer signs off against the handoff JSX/JPG.

- **Q8 (HomeScaffold filterBar slot for this slice):** CrumbsFilterBar empty, null, or stub Box?
  **A:** Wire `CrumbsFilterBar` with empty/inert chips (the 3 type chips visible but no selection state). Matches Option D mock visually; behaviors slice adds chip-state and filter logic without touching HomeScreen's layout.

### Cross-slice impact captured

- **Roborazzi plugin enablement spreads to 3 new modules** (`app`, `feature/twitter`, `feature/reddit`). Mechanical copy of `core/designsystem`'s plugin id + dep bundle. No catalog version changes.
- **Route/Screen split touches NavHost wiring** at [Crumbs.kt](app/src/main/java/com/github/jayteealao/crumbs/Crumbs.kt). Routes call `XxxRoute(...)`; previously they called `XxxScreen(...)` directly. Net diff is name + one extra Composable wrapper per screen.
- **AC-S1 fidelity diff is maintainer-driven** — registered as a runtime-evidence-deferral at verify. AC-S2 (Maestro happy-path) belongs to the maestro slice on the established pattern. AC-S3 (Accompanist Pager removal grep) is a one-line automated check within this slice.
- **MaterialTheme cleanup obligation:** `AllBookmarksScreen.kt` lines ~103, ~180 use `MaterialTheme.typography.titleMedium`; feature modules have a commented `Color(0xFF…)` literal in `TwitterCard.kt:419`. Plan must remove or actively-convert all `MaterialTheme.*` references in any screen file the slice touches.
- **Twitter→Reddit cross-module VM coupling persists.** `RedditBookmarksScreen` will continue to inject Twitter's `BookmarksViewModel` for tag state. No change in this slice; the contract is a behaviors-slice consideration if it becomes load-bearing.
- **PullToRefreshBox in TwitterBookmarksScreen** is functional behavior; it stays. Brutalist re-skin replaces card composables + tokens, not the pull-to-refresh affordance itself. Behaviors slice owns the refresh state machine.
