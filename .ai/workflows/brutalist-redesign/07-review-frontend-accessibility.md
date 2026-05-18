---
review: frontend-accessibility
date: 2026-05-18
scope: slug-wide (git diff main...HEAD)
wcag-target: WCAG 2.1 Level AA (Android TalkBack / accessibility services)
platform: Android Jetpack Compose
---

# Frontend Accessibility Review — Brutalist-Redesign

**Verdict:** Ship with caveats
**Reviewed:** slug-wide / `git diff main...HEAD`
**Files:** 117 changed Kotlin files, 380 total files changed

---

## 0) Scope & Methodology

**What was reviewed:**
- Scope: full branch diff (`main...HEAD`) scoped to UI/Compose files
- Framework: Jetpack Compose (Android)
- Component library: custom `core/designsystem` (Material3 wrappers stripped, brutalist-custom controls)
- Navigation: Hilt-scoped route composables (`HomeRoute`, `TwitterBookmarksRoute`, `RedditBookmarksRoute`)
- Key new components: `CrumbsIconButton`, `CrumbsBottomNav`, `CrumbsLongPressPopup`, `CrumbsFilterBar`, `CrumbsBanner`, `CrumbsSnackbar`, `CrumbsBookmarkCard`, `CrumbsTopBar`, `HomeScaffold`, `OverlayShell`, `EmptyState`, `CrumbsButton`

**Review methodology:**
1. Static analysis: semantic roles, ARIA/accessibility equivalents in Compose (`semantics {}` blocks, `contentDescription`, `role`)
2. TalkBack simulation: what accessibility service announces per composable
3. Keyboard/switch-access traversal: tab order, focus indicators, touchable target sizing
4. WCAG 2.1 Level AA compliance mapping
5. All-caps / uppercase text pattern analysis for screen reader impact

---

## 1) Executive Summary

**Accessibility Status:** MOSTLY_COMPLIANT with five issues requiring fixes before a general release.

The brutalist redesign made several **intentional and correct** accessibility decisions:
- `CrumbsBookmarkCard` uses `semantics { onClick; onLongClick }` with human-readable labels — correct approach for `detectTapGestures` integration.
- `CrumbsSnackbar` uses `liveRegion = LiveRegionMode.Polite` — TalkBack will announce delete confirmations without interrupting.
- `OverlayShell` uses `contentDescription = "Dismiss overlay"` on the backdrop.
- `CrumbsButton` wraps Material3 `Button`, retaining native keyboard/focus semantics.

However, five issues range from HIGH to MED severity and must be addressed. The most critical: **all interactive `Box` / `Text` elements used as buttons in `CrumbsIconButton`, `CrumbsBottomNav`, `CrumbsFilterBar`, and `CrumbsBanner` lack a `role = Role.Button` / `Role.Tab` semantic declaration**, meaning TalkBack announces them without interactive role context. Additionally, several all-caps label patterns use string-level `.uppercase()` rather than text composition, causing TalkBack to spell letters individually.

**Overall Assessment:**
- Keyboard / Switch Access: Good (Material3 `Button` used for primary actions; `detectTapGestures` correctly supplements with `semantics{}` fallback)
- TalkBack / Screen Reader Support: Incomplete (role annotations missing on custom `clickable Box` controls)
- Focus Management: Good (no route-change focus loss detected; `HomeScaffold` uses Material3 `Scaffold` which handles focus order)
- State Announcements: Good (`liveRegion` on snackbar; banner uses `AnimatedVisibility` but lacks `role = Role.Alert`)
- Touch Target Sizes: Partially broken (`CrumbsIconButton.Small` = 36dp; minimum required is 48dp)

---

## 2) Findings Table

| ID | Severity | WCAG (Android equiv) | Component | Violation |
|----|----------|----------------------|-----------|-----------|
| A11Y-01 | HIGH | 4.1.2 Name, Role, Value (A) | `CrumbsBottomNav` | `clickable Box` tabs have no `role = Role.Tab`; TalkBack announces label only, no role |
| A11Y-02 | HIGH | 4.1.2 Name, Role, Value (A) | `CrumbsFilterBar` — chips and sort trigger | `clickable Box` controls have no `role = Role.Button` or `stateDescription`; selection state not announced |
| A11Y-03 | HIGH | 4.1.2 Name, Role, Value (A) | `CrumbsBanner` — CTA Text | CTA is a `clickable Text`, not a button; no `role`, no `contentDescription`; TalkBack reads raw label, no "button" indication |
| A11Y-04 | HIGH | 1.4.3 Contrast (Minimum) (AA) | `CrumbsIconButton.Small` + `CrumbsFilterBar` | Touch target 36dp (`Small`) and 34dp filter bar height are below the 48dp minimum for WCAG 2.5.5 / Android minimum |
| A11Y-05 | MED | 4.1.3 Status Messages (AA) | `CrumbsBanner` | Banner appears via `AnimatedVisibility` but has no `role = Role.Alert` or live-region; TalkBack does not announce banner arrival |
| A11Y-06 | MED | 1.1.1 Non-text Content (A) | `CrumbsIconButton` | `contentDescription` param exists but is `null` by default and callers may omit it; icon remains unlabeled |
| A11Y-07 | MED | 4.1.2 / All-caps TTS | Multiple components | String-level `.uppercase()` on labels read by TalkBack causes letter-by-letter spelling on some TTS engines (e.g. "T-A-G" not "Tag") |
| A11Y-08 | LOW | 2.4.7 Focus Visible (AA) | `CrumbsIconButton`, `CrumbsFilterBar` chips | Custom `clickable` with `indication = null` removes ripple; no alternative focus indicator provided for Switch Access / keyboard |
| A11Y-09 | NIT | 4.1.2 | `CrumbsTopBar` wordmark | `clickable { /* no-op */ }` on wordmark makes a non-interactive element focusable; should be removed or marked `clearAndSetSemantics {}` |

**Findings Summary:**
- BLOCKER: 0
- HIGH: 4
- MED: 3
- LOW: 1
- NIT: 1

---

## 3) Findings (Detailed)

### A11Y-01: Bottom Nav Tabs — Missing Role Annotation [HIGH]

**Location:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNav.kt:67-84`

**WCAG Violation:** 4.1.2 Name, Role, Value (A) — UI components must have programmatically determinable role.

**Vulnerable Code:**
```kotlin
Box(
    modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .background(if (isSelected) colors.ink else Color.Transparent)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
        ) { onTabSelected(tab) }
        .testTag("nav-tab-${tab.name.lowercase()}"),
    contentAlignment = Alignment.Center,
) {
    Text(
        text = tab.label.uppercase(),  // "TWITTER", "REDDIT", etc.
        style = typography.captionMono,
        color = if (isSelected) colors.accent else colors.ink,
    )
}
```

**TalkBack Experience:**
```
Current:  "TWITTER" (no role, no selected state)
Expected: "Twitter, tab, selected, 1 of 4"
```

**Impact:** Switch Access and TalkBack users cannot determine they are on a navigation control or which tab is selected. The navigation bar is structurally invisible to assistive technology.

**Fix:**
```kotlin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

Box(
    modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .background(if (isSelected) colors.ink else Color.Transparent)
        .semantics(mergeDescendants = true) {
            role = Role.Tab
            selected = isSelected
            // Use mixed-case for TalkBack (override uppercase display text)
            contentDescription = tab.label  // "Twitter", not "TWITTER"
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
        ) { onTabSelected(tab) }
        .testTag("nav-tab-${tab.name.lowercase()}"),
```

---

### A11Y-02: Filter Bar Chips and Sort Trigger — Missing Role and State [HIGH]

**Location:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsFilterBar.kt:94-119` (chips), `121-136` (sort trigger)

**WCAG Violation:** 4.1.2 Name, Role, Value (A)

**Vulnerable Code (chips):**
```kotlin
Box(
    modifier = Modifier
        .border(...)
        .background(if (selected) colors.accent else Color.Transparent)
        .clickable { onChipToggled(chip.id) }
        .padding(...)
        .testTag("filter-bar-chip-${chip.id}"),
) {
    Text(
        text = chip.label.uppercase(),  // "ALL", "ARTICLES", "VIDEOS"
        ...
    )
}
```

**TalkBack Experience:**
```
Current:  "ALL" (no role, no selected/checked state)
Expected: "All, toggle button, not selected"
After tap: No state change announced
```

**Impact:** Users cannot determine filter chips are interactive toggles, cannot hear current filter state, and receive no feedback when a filter is activated.

**Fix:**
```kotlin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

Box(
    modifier = Modifier
        .border(...)
        .background(if (selected) colors.accent else Color.Transparent)
        .semantics(mergeDescendants = true) {
            role = Role.Checkbox   // or Role.Button with stateDescription
            toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            contentDescription = chip.label   // mixed-case, not chip.label.uppercase()
        }
        .clickable { onChipToggled(chip.id) }
```

**Sort trigger fix:**
```kotlin
Box(
    modifier = Modifier
        .fillMaxHeight()
        .semantics { role = Role.Button; contentDescription = "Sort: $sortLabel" }
        .clickable { onSortClick() }
```

---

### A11Y-03: Banner CTA — Clickable Text Without Button Role [HIGH]

**Location:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBanner.kt:71-80`

**WCAG Violation:** 4.1.2 Name, Role, Value (A)

**Vulnerable Code:**
```kotlin
Text(
    text = ctaLabel.uppercase(),   // "RECONNECT"
    style = typography.captionMono,
    color = colors.accent,
    modifier = Modifier
        .clickable { onCta() }
        .padding(horizontal = 4.dp)
        .testTag("banner-cta"),
)
```

**TalkBack Experience:**
```
Current:  "RECONNECT" (no role — TalkBack may skip it as non-interactive)
Expected: "Reconnect, button"
```

**Impact:** TalkBack may not surface the CTA as focusable, preventing reconnect action for assistive technology users after a sync error.

**Fix:**
```kotlin
Text(
    text = ctaLabel.uppercase(),
    style = typography.captionMono,
    color = colors.accent,
    modifier = Modifier
        .semantics { role = Role.Button; contentDescription = ctaLabel }  // mixed-case
        .clickable { onCta() }
        .padding(horizontal = 4.dp)
        .testTag("banner-cta"),
)
```

---

### A11Y-04: Touch Target Size Below 48dp Minimum [HIGH]

**Location:**
- `CrumbsIconButton.kt:56-59` — `Small` variant = 36dp
- `CrumbsFilterBar.kt:63` — entire bar height = 34dp

**WCAG Violation:** WCAG 2.5.5 Target Size (AAA, but Android enforces 48dp minimum via Material Design and accessibility guidelines)

**Vulnerable Code:**
```kotlin
// CrumbsIconButton
val square = when (size) {
    IconButtonSize.Small -> 36.dp   // ❌ below 48dp minimum
    IconButtonSize.Medium -> 40.dp  // ❌ below 48dp minimum
    IconButtonSize.Large -> 48.dp   // ✅
}

// CrumbsFilterBar
Row(
    modifier = modifier
        .fillMaxWidth()
        .height(34.dp)   // ❌ entire interactive bar is 34dp tall
```

**Impact:** `Small` (36dp) and `Medium` (40dp) icon buttons, and all filter bar interactives, are below the 48dp minimum. Users with motor impairments or reduced dexterity will miss targets. The filter bar at 34dp is the most egregious — all chips and the sort trigger fall below minimum.

**Fix options:**
1. Increase `Small` to 40dp and `Medium` to 48dp to match Material Design specs.
2. Alternatively, pad touch target using `Modifier.minimumInteractiveComponentSize()` (Material3 helper) while keeping visual size smaller for the brutalist aesthetic:
```kotlin
import androidx.compose.material3.minimumInteractiveComponentSize

var inner: Modifier = modifier
    .minimumInteractiveComponentSize()  // guarantees 48dp touch target
    .size(square)                       // visual size unchanged
```
3. For `CrumbsFilterBar`, increase height to 40dp minimum (48dp preferred) or wrap each chip's `clickable` with `minimumInteractiveComponentSize`.

---

### A11Y-05: Banner — No Live Region / Alert Role on Appearance [MED]

**Location:** `app/src/main/java/com/github/jayteealao/crumbs/screens/HomeScreen.kt:48-57` (banner slot)

**WCAG Violation:** 4.1.3 Status Messages (AA)

**Context:** When a sync error occurs (`SyncErrorEvent.TwitterAuth401`), the banner slides into view via `AnimatedVisibility`. There is no live region or alert role, so TalkBack users navigating the feed will not hear that a reconnection is needed.

**Vulnerable Code:**
```kotlin
banner = uiState.bannerState?.let { state ->
    {
        AnimatedVisibility(visible = true) {
            CrumbsBanner(
                kickerLine = state.kicker,   // "ERR · RECONNECT TWITTER"
                detail = state.detail,
                ctaLabel = state.ctaLabel,
                onCta = onBannerCta,
            )
        }
    }
},
```

The `CrumbsBanner` itself has no `semantics { liveRegion }` or `role = Role.Alert`.

**TalkBack Experience:**
```
Banner appears → TalkBack: [silence]
User continues scrolling, unaware of auth error
```

**Fix — add live region to CrumbsBanner:**
```kotlin
Column(
    modifier = modifier
        .fillMaxWidth()
        .background(colors.surface)
        .semantics { liveRegion = LiveRegionMode.Polite }  // or Assertive for errors
        .testTag("banner"),
)
```

Or use `role = Role.Alert` (maps to `accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_ASSERTIVE`):
```kotlin
import androidx.compose.ui.semantics.LiveRegionMode

// For error banners (BannerState with source != null), use Assertive:
.semantics { liveRegion = LiveRegionMode.Assertive }
```

---

### A11Y-06: CrumbsIconButton — contentDescription Nullable, No Enforcement [MED]

**Location:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsIconButton.kt:47, 80-87`

**WCAG Violation:** 1.1.1 Non-text Content (A)

**Vulnerable Code:**
```kotlin
fun CrumbsIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,   // nullable with no enforcement
    ...
)

Box(modifier = inner, contentAlignment = Alignment.Center) {
    // icon() composable is caller-provided; no contentDescription injected here
}
```

The `contentDescription` parameter is accepted but **never applied to any semantic node**. The icon slot is a raw `@Composable () -> Unit`, meaning the caller must independently set `contentDescription` on their `Icon()` call. This is fragile — callers that do `Icon(imageVector = Icons.Default.Delete, contentDescription = null)` will silently produce an unlabeled button.

Additionally, the `Box` itself carries no `semantics { contentDescription }`, so even if the icon has no label, there is no fallback.

**Evidence from usage — `AllBookmarksScreen.kt` imports `Icons.Default.Delete`, and popup actions use icons but their labels come through `PopupAction.label`; the separate Search icon in `CrumbsTopBar` does pass `contentDescription = "Search"` correctly.**

**Fix:**
```kotlin
Box(modifier = inner, contentAlignment = Alignment.Center) {
    // Enforce contentDescription on the container so TalkBack has a fallback
    // even if the icon slot forgets to set it
    val effectiveModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else Modifier
    Box(modifier = effectiveModifier, contentAlignment = Alignment.Center) {
        icon()
    }
}
```

Or require `contentDescription: String` (non-nullable) and pass it explicitly.

---

### A11Y-07: String-level `.uppercase()` Causes TalkBack Letter-spelling [MED]

**Location:** Multiple files — pervasive pattern throughout all new components

**WCAG Violation:** No direct WCAG criterion, but violates WCAG's principle of understandability; impacts screen reader usability.

**Pattern:**
```kotlin
// CrumbsBottomNav.kt:80
Text(text = tab.label.uppercase())  // "TWITTER" → T-W-I-T-T-E-R

// CrumbsFilterBar.kt:115
Text(text = chip.label.uppercase())  // "ARTICLES" → A-R-T-I-C-L-E-S

// CrumbsBanner.kt:62
Text(text = kickerLine.uppercase())

// CrumbsTopBar.kt:77, 78 (kicker text)
Text(text = kickerText.uppercase())

// AllBookmarksScreen.kt headers
Text(text = "TWITTER"), Text(text = "REDDIT")
```

**TalkBack Experience:** On some Android TTS engines (particularly older Google TTS), a string of all-caps letters with word-level spacing is interpreted as an acronym and spelled out letter-by-letter: "T-A-G", "O-P-E-N", "A-L-L". Mixed-case display with `.uppercase()` applied via `TextStyle.textTransform` (not Compose-native, but achievable via `SpanStyle`) or by providing a `contentDescription` with the original mixed-case string avoids this.

**Affected labels:** "TAG", "OPEN", "SHARE", "DELETE", "RECONNECT", "ALL", "ARTICLES", "VIDEOS", "IMAGES", "TWITTER", "REDDIT", filter chip labels.

**Fix (preferred — provide explicit contentDescription):**
```kotlin
// CrumbsBottomNav — already addressed in A11Y-01 fix via contentDescription = tab.label
// For inline Text that is purely display:
Text(
    text = chip.label.uppercase(),      // visual: "ALL"
    modifier = Modifier.semantics {
        contentDescription = chip.label  // TalkBack reads: "All"
    }
)
```

This pattern needs to be applied wherever `.uppercase()` is called on labels that are the primary semantic content of a focusable element.

---

### A11Y-08: Ripple Removed Without Alternative Focus Indicator [LOW]

**Location:** `CrumbsBottomNav.kt:72-75`, `CrumbsFilterBar.kt:104-108`

**WCAG Violation:** 2.4.7 Focus Visible (AA)

**Vulnerable Code:**
```kotlin
.clickable(
    interactionSource = interactionSource,
    indication = null,   // ripple removed, no focus ring added
) { onTabSelected(tab) }
```

Removing `indication = null` eliminates the only visual focus indicator for Switch Access navigation. Unlike web where CSS `:focus` can replace it, in Compose the `indication = null` pattern leaves no visible focus state at all.

**Fix:** Add a border-based or overlay focus indicator using `Indication` with an `InteractionSource` observer, or use `FocusRequester` + `BorderStroke` that activates on focus:
```kotlin
val isFocused by interactionSource.collectIsFocusedAsState()

.border(
    width = if (isFocused) 2.dp else 0.dp,
    color = if (isFocused) colors.accent else Color.Transparent,
    shape = RectangleShape,
)
```

---

### A11Y-09: Wordmark Incorrectly Clickable [NIT]

**Location:** `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsTopBar.kt:96-101`

**WCAG Violation:** 3.2.1 On Focus — unexpectedly focusable element.

**Vulnerable Code:**
```kotlin
Text(
    text = wordmark,
    ...
    modifier = Modifier
        .testTag("top-bar-wordmark")
        .clickable { /* no-op; wordmark is brand, not interactive */ },
)
```

A no-op `clickable` still makes the element focusable and reachable via Switch Access / TalkBack swipe navigation, where it announces as an activatable element that does nothing.

**Fix:**
```kotlin
Text(
    text = wordmark,
    ...
    modifier = Modifier
        .testTag("top-bar-wordmark")
        .clearAndSetSemantics { },  // remove from accessibility tree
)
```

---

## 4) WCAG 2.1 Compliance Summary (Compose / Android TalkBack)

| WCAG Criterion | Level | Status | Finding(s) |
|----------------|-------|--------|-----------|
| 1.1.1 Non-text Content | A | ⚠️ Partial | A11Y-06 (icon button unlabeled) |
| 1.4.3 Contrast (Minimum) | AA | ✅ Pass | Accent `#FF5A1F` on `#0A0A0A`: ~4.6:1 (passes AA); on `#FFFFFF`: ~3.7:1 (passes large text AA) |
| 2.1.1 Keyboard / Switch Access | A | ✅ Pass | All primary actions use native `Button` or `clickable` |
| 2.4.7 Focus Visible | AA | ⚠️ Partial | A11Y-08 (indication removed) |
| 2.5.5 Target Size | AAA | ❌ Fail | A11Y-04 (36dp, 40dp, 34dp targets) |
| 3.2.1 On Focus | A | ⚠️ Partial | A11Y-09 (no-op clickable wordmark) |
| 4.1.2 Name, Role, Value | A | ❌ Fail | A11Y-01, A11Y-02, A11Y-03, A11Y-06 |
| 4.1.3 Status Messages | AA | ⚠️ Partial | A11Y-05 (banner not announced) |

**Note on color contrast:** The accent `#FF5A1F` on paper-cream background `#EFEFEE9` computes to approximately 3.2:1. This **fails WCAG 4.5:1 AA for normal text**. The captionMono style at 10sp/bold is not large text (large text threshold is 18sp normal or 14sp bold). This would be a HIGH finding if accent-colored text were used for informational content. In current usage, accent appears on: CTA labels in banner/snackbar (action labels), selected tab indicator, filter chip text when selected (white/`onAccent` on accent bg — passes), and `+ N MORE` thread indicator. The `+ N MORE` thread indicator in `CrumbsBookmarkCard.kt:189` uses `colors.accent` for informational text at 10sp, which **fails AA contrast on the paper surface**. Adding this as supplementary:

| A11Y-10 | MED | 1.4.3 Contrast (AA) | `CrumbsBookmarkCard` thread indicator | `colors.accent` (#FF5A1F) on `colors.surface` (#FFFFFF) = ~3.2:1, below 4.5:1 for 10sp normal text |

---

## 5) Contrast Calculation (Key Pairs)

| Foreground | Background | Ratio | WCAG AA Normal | WCAG AA Large | Status |
|-----------|-----------|-------|----------------|---------------|--------|
| Ink `#0A0A0A` | Surface `#FFFFFF` | 21:1 | Pass | Pass | ✅ |
| Ink `#FFFFFF` | Surface `#161616` (dark) | 16:1 | Pass | Pass | ✅ |
| Accent `#FF5A1F` | Surface `#FFFFFF` | ~3.2:1 | Fail (normal) | Pass (large) | ⚠️ |
| Accent `#FF5A1F` | Surface `#161616` (dark) | ~4.6:1 | Pass | Pass | ✅ |
| onAccent `#0A0A0A` | Accent `#FF5A1F` | ~4.6:1 | Pass | Pass | ✅ |
| onSurfaceVariant `#535353` | Surface `#FFFFFF` | ~7.4:1 | Pass | Pass | ✅ |

**Key finding:** `#FF5A1F` accent on light surface fails AA for normal (non-bold ≤18sp) text. The 10sp bold `captionMono` is at the edge — bold 10sp does not qualify as WCAG "large text" (requires 14sp bold). Any accent-colored text at or below 14sp on light surface fails AA.

---

## 6) All Findings (Final Count)

**BLOCKER: 0 | HIGH: 4 | MED: 4 | LOW: 1 | NIT: 1**

(A11Y-10 added from contrast analysis, MED severity)

| ID | Sev | Component | Issue |
|----|-----|-----------|-------|
| A11Y-01 | HIGH | `CrumbsBottomNav` | No `role = Role.Tab` or `selected` semantic on nav tabs |
| A11Y-02 | HIGH | `CrumbsFilterBar` | No `role` or `toggleableState` on filter chips; no `role` on sort trigger |
| A11Y-03 | HIGH | `CrumbsBanner` CTA | Clickable `Text` has no `role = Role.Button` |
| A11Y-04 | HIGH | `CrumbsIconButton` Small/Medium + FilterBar | Touch targets 36dp / 40dp / 34dp below 48dp minimum |
| A11Y-05 | MED | `CrumbsBanner` | No `liveRegion` or alert role; banner appearance not announced |
| A11Y-06 | MED | `CrumbsIconButton` | `contentDescription` nullable, never applied to semantic node; icon may be unlabeled |
| A11Y-07 | MED | All components | String-level `.uppercase()` on semantic labels causes letter-spelling in TalkBack |
| A11Y-10 | MED | `CrumbsBookmarkCard` thread indicator | Accent `#FF5A1F` on white surface = 3.2:1, fails AA for 10sp text |
| A11Y-08 | LOW | `CrumbsBottomNav`, `CrumbsFilterBar` | `indication = null` removes focus indicator; no alternative provided |
| A11Y-09 | NIT | `CrumbsTopBar` wordmark | No-op `clickable` makes wordmark focusable with no action |

---

## 7) Recommendations

### Fix Before Release (HIGH)

1. **A11Y-01 — Add `role = Role.Tab` + `selected` to bottom nav tabs** — 15 min
2. **A11Y-02 — Add `role = Role.Checkbox`/`toggleableState` to filter chips; `role = Role.Button` to sort trigger** — 20 min
3. **A11Y-03 — Add `role = Role.Button` + `contentDescription` to banner CTA `Text`** — 10 min
4. **A11Y-04 — Apply `Modifier.minimumInteractiveComponentSize()` to `CrumbsIconButton` and `CrumbsFilterBar` interactives** — 30 min

### Address in Follow-up (MED)

5. **A11Y-05 — Add `semantics { liveRegion = LiveRegionMode.Assertive }` to `CrumbsBanner`** — 10 min
6. **A11Y-06 — Apply `contentDescription` to `CrumbsIconButton`'s wrapping `Box`; consider non-nullable param** — 20 min
7. **A11Y-07 — Add `contentDescription = originalLabel` (mixed-case) to all `semantics {}` blocks on uppercased interactive labels** — 45 min across all files
8. **A11Y-10 — Change thread indicator `+ N MORE` color to `colors.ink` (not `colors.accent`) on light theme** — 5 min

### Backlog (LOW / NIT)

9. **A11Y-08 — Add focus-visible border to custom `clickable` controls with `indication = null`** — 1 hour
10. **A11Y-09 — Remove no-op `clickable` from wordmark or mark `clearAndSetSemantics {}`** — 5 min

### Maestro Coverage Gaps

The Maestro tests in `maestro/happy_path.yaml`, `maestro/filter_overlay.yaml`, etc. do not cover:
- TalkBack navigation traversal order
- State change announcements (filter chip selected → TalkBack reads new state)
- Banner announcement on sync error
- Snackbar announcement timing

Consider adding Accessibility Scanner sweep to CI or adding `adb shell uiautomator` checks for role/state attributes.

---

## 8) False Positives / Confidence Notes

- **Contrast ratio for accent on light surface:** Calculated from hex values; actual rendering may vary with subpixel AA. Verify with Android Accessibility Scanner.
- **TTS letter-spelling (A11Y-07):** Behavior depends on TTS engine version. Google TTS 3.x handles all-caps word-length strings as words in most cases; the issue is most pronounced for abbreviation-length tokens (3–4 chars: "TAG", "ALL"). Lower confidence for longer words ("ARTICLES", "TWITTER").
- **A11Y-06 (CrumbsIconButton):** Where callers pass `contentDescription` to the `Icon()` composable inside the slot (e.g., `Icon(imageVector, contentDescription = "Search")` in `CrumbsTopBar`), the icon itself is labeled correctly. The finding only applies to callers that pass `contentDescription = null` to `Icon`.

---

*Review date: 2026-05-18 | Platform: Android Jetpack Compose | WCAG Target: 2.1 AA*
