# Crumbs Design System Module

**Date:** 2026-01-03
**Status:** Approved for Implementation

## Overview

A custom design system module for the Crumbs Android app with Roborazzi screenshot testing. This module provides UI components, design tokens (colors, typography, spacing, shapes), and a consistent visual language with a distinctive cut-corner aesthetic.

## Module Architecture

**Location:** `core/designsystem`

**Package Structure:**
```
com.github.jayteealao.crumbs.designsystem/
├── components/           # UI components
│   ├── CrumbsBottomNav.kt
│   ├── CrumbsButton.kt
│   ├── CrumbsCard.kt
│   ├── CrumbsTextField.kt
│   └── CrumbsChip.kt
├── theme/               # Design tokens
│   ├── CrumbsColors.kt      # Color definitions
│   ├── CrumbsTypography.kt  # Typography scale
│   ├── CrumbsShapes.kt      # Corner cuts & shapes
│   ├── CrumbsSpacing.kt     # Spacing scale
│   └── CrumbsTheme.kt       # Theme provider composable
└── icons/               # Custom icon components
    └── CrumbsIcons.kt
```

**Test Structure:**
```
test/
├── components/
│   ├── CrumbsBottomNavTest.kt
│   ├── CrumbsButtonTest.kt
│   ├── CrumbsCardTest.kt
│   ├── CrumbsTextFieldTest.kt
│   └── CrumbsChipTest.kt
└── screenshots/         # Roborazzi generates here
    ├── CrumbsBottomNav_*.png
    ├── CrumbsButton_*.png
    └── ...
```

## Design Tokens

### Colors

**Palette:**
- `#c2e7da` - Soft mint/seafoam
- `#28666e` - Deep teal
- `#f1ffe7` - Pale cream
- `#1a1b41` - Dark navy-purple
- `#baff29` - Neon lime

**Light Theme:**
- Background: `#f1ffe7` (pale cream)
- Surface: `#c2e7da` (mint)
- Primary: `#28666e` (deep teal)
- Accent: `#baff29` (neon lime)
- Text Primary: `#1a1b41` (dark navy)
- Text Secondary: `#28666e` @ 70% opacity

**Dark Theme:**
- Background: `#1a1b41` (dark navy)
- Surface: `#28666e` (teal)
- Primary: `#c2e7da` (mint)
- Accent: `#baff29` (neon lime)
- Text Primary: `#f1ffe7` (cream)
- Text Secondary: `#c2e7da` @ 70% opacity

**Implementation:**
```kotlin
data class CrumbsColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentAlpha: Color,
    val surfaceVariant: Color,
    val error: Color = Color(0xFFFF5555)
)

val LightColors = CrumbsColors(...)
val DarkColors = CrumbsColors(...)
```

### Shapes

**Cut Corner System:**

All components use box shapes with strategic cut corners for visual identity:

```kotlin
object CrumbsShapes {
    private val smallCut = 4.dp
    private val mediumCut = 8.dp
    private val largeCut = 12.dp

    // Top-end cuts: Actions, forward movement
    val button = CutCornerShape(topEnd = mediumCut)
    val buttonSmall = CutCornerShape(topEnd = smallCut)

    // Bottom-end cuts: Content, stability
    val card = CutCornerShape(bottomEnd = largeCut)
    val cardSmall = CutCornerShape(bottomEnd = mediumCut)

    // Bottom-start cuts: Input, foundation
    val textField = CutCornerShape(bottomStart = mediumCut)

    // Top-start cuts: Labels, metadata
    val chip = CutCornerShape(topStart = smallCut)

    // Dual cuts: Elevated importance
    val dialog = CutCornerShape(
        topEnd = largeCut,
        bottomStart = largeCut
    )

    // Navigation: Anchored
    val navigationBar = CutCornerShape(bottomStart = mediumCut)

    val rectangle = RectangleShape
}
```

**Cut Corner Logic:**
- **Top-end**: Actions, forward movement (buttons)
- **Bottom-end**: Content, stability (cards)
- **Bottom-start**: Input, foundation (text fields)
- **Top-start**: Labels, metadata (chips)
- **Dual cuts**: Elevated importance (dialogs)

### Typography

Standard text styles with system fonts or custom font family:
- Display (large headings)
- Heading (section titles)
- Title (subsections)
- Body (content text)
- Label (UI labels, buttons)
- Caption (metadata, timestamps)

### Spacing

Standard spacing scale:
- `xs`: 4.dp
- `sm`: 8.dp
- `md`: 12.dp
- `lg`: 16.dp
- `xl`: 24.dp
- `xxl`: 32.dp

## Theme Provider

```kotlin
@Composable
fun CrumbsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalCrumbsColors provides colors,
        LocalCrumbsSpacing provides CrumbsSpacing,
        LocalCrumbsTypography provides CrumbsTypography
    ) {
        content()
    }
}

// CompositionLocals
val LocalCrumbsColors = compositionLocalOf { LightColors }
val LocalCrumbsSpacing = compositionLocalOf { CrumbsSpacing }
val LocalCrumbsTypography = compositionLocalOf { CrumbsTypography }
```

## Component Architecture

Each component follows this pattern:
```kotlin
@Composable
fun CrumbsButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium
) {
    val colors = LocalCrumbsColors.current
    val shapes = CrumbsShapes

    Surface(
        modifier = modifier,
        shape = when(size) {
            ButtonSize.Small -> shapes.buttonSmall
            ButtonSize.Medium -> shapes.button
        },
        color = colors.primary,
        onClick = onClick,
        enabled = enabled
    ) {
        // Button content using colors.textPrimary
    }
}
```

**Initial Component Set:**
1. **CrumbsBottomNav** - Bottom navigation (refactor existing)
2. **CrumbsButton** - Primary action button
3. **CrumbsCard** - Content container
4. **CrumbsTextField** - Text input field
5. **CrumbsChip** - Tags/labels

**Component State Variants:**

Each component documents its testable variants:
- Enabled/Disabled states
- Size variants (Small/Medium/Large)
- Style variants (Primary/Secondary)
- Loading states
- Error states
- Empty states
- Light/Dark theme

## Roborazzi Screenshot Testing

### Dependencies

```gradle
testImplementation("io.github.takahirom.roborazzi:roborazzi:1.7.0")
testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.7.0")
testImplementation("io.github.takahirom.roborazzi:roborazzi-rule:1.7.0")
testImplementation("org.robolectric:robolectric:4.11.1")
testImplementation("junit:junit:4.13.2")
testImplementation("androidx.compose.ui:ui-test-junit4")
```

### Test Structure

**Component-focused testing approach:**

```kotlin
class CrumbsButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun button_enabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Click Me"
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("screenshots/CrumbsButton_enabled_light.png")
    }

    @Test
    fun button_disabled_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsButton(
                    onClick = {},
                    text = "Click Me",
                    enabled = false
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("screenshots/CrumbsButton_disabled_dark.png")
    }

    // More tests for each variant...
}
```

**Test Naming Convention:**
`[Component]_[state]_[theme].png`

Examples:
- `CrumbsButton_enabled_light.png`
- `CrumbsButton_disabled_dark.png`
- `CrumbsCard_withImage_dark.png`
- `CrumbsBottomNav_twitterSelected_light.png`

**Running Tests:**
```bash
./gradlew :core:designsystem:recordRoborazziDebug  # Record new screenshots
./gradlew :core:designsystem:verifyRoborazziDebug  # Compare against existing
./gradlew :core:designsystem:compareRoborazziDebug # Generate comparison report
```

**Screenshot Management:**
- Screenshots stored in `src/test/screenshots/`
- Committed to version control for visual regression testing
- CI/CD pipeline runs verification on PRs
- Failed comparisons block merge

## Build Configuration

**Module `build.gradle`:**

```gradle
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.plugin.compose'
    id 'io.github.takahirom.roborazzi' version '1.7.0'
}

android {
    namespace 'com.github.jayteealao.crumbs.designsystem'
    compileSdk 33

    defaultConfig {
        minSdk 24
        targetSdk 33

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }

    testOptions {
        unitTests {
            includeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Compose BOM
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.foundation:foundation'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    debugImplementation 'androidx.compose.ui:ui-tooling'

    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.11.1'
    testImplementation 'androidx.compose.ui:ui-test-junit4'
    testImplementation 'io.github.takahirom.roborazzi:roborazzi:1.7.0'
    testImplementation 'io.github.takahirom.roborazzi:roborazzi-compose:1.7.0'
    testImplementation 'io.github.takahirom.roborazzi:roborazzi-rule:1.7.0'
}
```

**Add to `settings.gradle`:**
```gradle
include ':core:designsystem'
```

## Implementation Phases

### Phase 1: Module Setup
- Create build.gradle
- Add to settings.gradle
- Set up directory structure
- Configure Roborazzi

### Phase 2: Design Tokens
- Implement CrumbsColors (light & dark)
- Implement CrumbsShapes (cut corners)
- Implement CrumbsTypography
- Implement CrumbsSpacing
- Create CrumbsTheme provider

### Phase 3: Initial Components
- Refactor existing DudsBottomNav → CrumbsBottomNav
- Implement CrumbsButton
- Implement CrumbsCard
- Write Roborazzi tests for each

### Phase 4: Additional Components & Testing
- Implement CrumbsTextField
- Implement CrumbsChip
- Complete test coverage
- Document component usage

## Migration Strategy

**Existing Code:**
- `DudsBottomNav.kt` exists with references to `DudsColors`
- Needs refactoring to use new Crumbs theme

**Approach:**
1. Set up new theme system alongside existing code
2. Refactor DudsBottomNav to CrumbsBottomNav
3. Test new components in isolation
4. Gradually migrate app to use design system
5. Remove old theme references

## Success Criteria

- ✅ Module builds successfully
- ✅ All 5 initial components implemented
- ✅ Light & dark themes work correctly
- ✅ Cut corner shapes render properly
- ✅ Roborazzi tests pass for all component variants
- ✅ Screenshots committed to version control
- ✅ Documentation complete for component usage

## Future Considerations

- Custom font family integration
- Animation tokens (durations, easing)
- Accessibility tokens (touch targets, contrast ratios)
- Expanded component library (dialogs, snackbars, etc.)
- Dark theme color palette refinement based on usage
