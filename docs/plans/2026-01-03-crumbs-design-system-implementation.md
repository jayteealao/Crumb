# Crumbs Design System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement a custom design system module with Roborazzi screenshot testing for the Crumbs Android app.

**Architecture:** Custom design tokens (colors, shapes, typography, spacing) provided via CompositionLocal, consumed by reusable Compose components. Cut-corner aesthetic for visual identity. Component-focused Roborazzi testing for visual regression.

**Tech Stack:** Jetpack Compose, Roborazzi 1.7.0, Robolectric 4.11.1, JUnit 4, Kotlin 1.9+

---

## Task 1: Module Configuration

**Files:**
- Create: `core/designsystem/build.gradle`
- Modify: `settings.gradle:22` (add module)

**Step 1: Create build.gradle for designsystem module**

Create file `core/designsystem/build.gradle`:

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
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.foundation:foundation'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    debugImplementation 'androidx.compose.ui:ui-tooling'

    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.11.1'
    testImplementation 'androidx.compose.ui:ui-test-junit4'
    testImplementation 'io.github.takahirom.roborazzi:roborazzi:1.7.0'
    testImplementation 'io.github.takahirom.roborazzi:roborazzi-compose:1.7.0'
    testImplementation 'io.github.takahirom.roborazzi:roborazzi-rule:1.7.0'
}
```

**Step 2: Add module to settings.gradle**

Modify `settings.gradle` line 22:

```gradle
include ':core:pref'
include ':core:designsystem'
```

**Step 3: Sync Gradle**

Run: `./gradlew :core:designsystem:build`
Expected: BUILD SUCCESSFUL (dependencies download)

**Step 4: Commit module setup**

```bash
git add core/designsystem/build.gradle settings.gradle
git commit -m "feat(designsystem): add module configuration"
```

---

## Task 2: Design Tokens - Colors

**Files:**
- Create: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsColors.kt`

**Step 1: Create CrumbsColors data class and theme variants**

Create file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsColors.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Crumbs color palette
 * - #c2e7da: Soft mint/seafoam
 * - #28666e: Deep teal
 * - #f1ffe7: Pale cream
 * - #1a1b41: Dark navy-purple
 * - #baff29: Neon lime
 */
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

val LightColors = CrumbsColors(
    background = Color(0xFFF1FFE7),    // Pale cream
    surface = Color(0xFFC2E7DA),       // Mint
    primary = Color(0xFF28666E),       // Deep teal
    accent = Color(0xFFBAFF29),        // Neon lime
    textPrimary = Color(0xFF1A1B41),   // Dark navy
    textSecondary = Color(0xFF28666E).copy(alpha = 0.7f),
    accentAlpha = Color(0xFFBAFF29).copy(alpha = 0.2f),
    surfaceVariant = Color(0xFFC2E7DA).copy(alpha = 0.5f)
)

val DarkColors = CrumbsColors(
    background = Color(0xFF1A1B41),    // Dark navy
    surface = Color(0xFF28666E),       // Teal
    primary = Color(0xFFC2E7DA),       // Mint
    accent = Color(0xFFBAFF29),        // Neon lime
    textPrimary = Color(0xFFF1FFE7),   // Cream
    textSecondary = Color(0xFFC2E7DA).copy(alpha = 0.7f),
    accentAlpha = Color(0xFFBAFF29).copy(alpha = 0.2f),
    surfaceVariant = Color(0xFF28666E).copy(alpha = 0.5f)
)
```

**Step 2: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit colors**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsColors.kt
git commit -m "feat(designsystem): add color tokens for light and dark themes"
```

---

## Task 3: Design Tokens - Shapes

**Files:**
- Create: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsShapes.kt`

**Step 1: Create CrumbsShapes with cut corners**

Create file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsShapes.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Crumbs shape system with cut corners for visual identity
 *
 * Cut corner logic:
 * - Top-end: Actions, forward movement (buttons)
 * - Bottom-end: Content, stability (cards)
 * - Bottom-start: Input, foundation (text fields)
 * - Top-start: Labels, metadata (chips)
 * - Dual cuts: Elevated importance (dialogs)
 */
object CrumbsShapes {
    private val smallCut = 4.dp
    private val mediumCut = 8.dp
    private val largeCut = 12.dp

    // Buttons - top-end (forward action direction)
    val button: Shape = CutCornerShape(topEnd = mediumCut)
    val buttonSmall: Shape = CutCornerShape(topEnd = smallCut)

    // Cards - bottom-end (grounded content)
    val card: Shape = CutCornerShape(bottomEnd = largeCut)
    val cardSmall: Shape = CutCornerShape(bottomEnd = mediumCut)

    // Inputs/Text fields - bottom-start (input area)
    val textField: Shape = CutCornerShape(bottomStart = mediumCut)

    // Chips/Tags - top-start (label/metadata feel)
    val chip: Shape = CutCornerShape(topStart = smallCut)

    // Dialogs/Modals - dual cuts for emphasis
    val dialog: Shape = CutCornerShape(
        topEnd = largeCut,
        bottomStart = largeCut
    )

    // Navigation - bottom-start (anchored)
    val navigationBar: Shape = CutCornerShape(bottomStart = mediumCut)

    // Fully rectangular when needed
    val rectangle: Shape = RectangleShape
}
```

**Step 2: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit shapes**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsShapes.kt
git commit -m "feat(designsystem): add shape tokens with cut corner system"
```

---

## Task 4: Design Tokens - Typography

**Files:**
- Create: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTypography.kt`

**Step 1: Create typography scale**

Create file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTypography.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Crumbs typography scale
 */
object CrumbsTypography {
    val displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Bold
    )

    val displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold
    )

    val headingLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold
    )

    val headingMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold
    )

    val titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium
    )

    val titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium
    )

    val bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    )

    val bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    )

    val labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )

    val labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )

    val caption = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    )
}
```

**Step 2: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit typography**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTypography.kt
git commit -m "feat(designsystem): add typography scale"
```

---

## Task 5: Design Tokens - Spacing

**Files:**
- Create: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsSpacing.kt`

**Step 1: Create spacing scale**

Create file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsSpacing.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Crumbs spacing scale
 */
object CrumbsSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
```

**Step 2: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit spacing**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsSpacing.kt
git commit -m "feat(designsystem): add spacing scale"
```

---

## Task 6: Theme Provider

**Files:**
- Create: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt`

**Step 1: Create theme composable with CompositionLocals**

Create file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * Crumbs theme provider
 *
 * Provides design tokens via CompositionLocal:
 * - Colors (light/dark)
 * - Typography
 * - Spacing
 *
 * Usage:
 * ```
 * CrumbsTheme {
 *     val colors = LocalCrumbsColors.current
 *     val spacing = LocalCrumbsSpacing.current
 *     // Use tokens in components
 * }
 * ```
 */

val LocalCrumbsColors = compositionLocalOf { LightColors }
val LocalCrumbsSpacing = compositionLocalOf { CrumbsSpacing }
val LocalCrumbsTypography = compositionLocalOf { CrumbsTypography }

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
```

**Step 2: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit theme provider**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/theme/CrumbsTheme.kt
git commit -m "feat(designsystem): add theme provider with CompositionLocals"
```

---

## Task 7: CrumbsButton Component

**Files:**
- Create: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButton.kt`

**Step 1: Implement CrumbsButton with variants**

Create file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButton.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsTypography

/**
 * Crumbs button with cut-corner styling
 *
 * Component variants for testing:
 * - Enabled/Disabled
 * - Small/Medium size
 * - Primary/Secondary style
 */

enum class ButtonSize {
    Small, Medium
}

enum class ButtonStyle {
    Primary, Secondary
}

@Composable
fun CrumbsButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    style: ButtonStyle = ButtonStyle.Primary
) {
    val colors = LocalCrumbsColors.current
    val typography = LocalCrumbsTypography.current

    val shape = when (size) {
        ButtonSize.Small -> CrumbsShapes.buttonSmall
        ButtonSize.Medium -> CrumbsShapes.button
    }

    val (containerColor, contentColor) = when (style) {
        ButtonStyle.Primary -> colors.primary to colors.textPrimary
        ButtonStyle.Secondary -> colors.surface to colors.textPrimary
    }

    val textStyle = when (size) {
        ButtonSize.Small -> typography.labelMedium
        ButtonSize.Medium -> typography.labelLarge
    }

    val contentPadding = when (size) {
        ButtonSize.Small -> PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ButtonSize.Medium -> PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    }

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(
            minHeight = if (size == ButtonSize.Small) 36.dp else 48.dp
        ),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = textStyle
        )
    }
}

// Previews
@Preview(name = "Primary Medium Light", showBackground = true)
@Composable
private fun PreviewButtonPrimaryMediumLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(
            onClick = {},
            text = "Click Me"
        )
    }
}

@Preview(name = "Primary Medium Dark", showBackground = true)
@Composable
private fun PreviewButtonPrimaryMediumDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsButton(
            onClick = {},
            text = "Click Me"
        )
    }
}

@Preview(name = "Disabled Light", showBackground = true)
@Composable
private fun PreviewButtonDisabledLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(
            onClick = {},
            text = "Disabled",
            enabled = false
        )
    }
}

@Preview(name = "Small Secondary", showBackground = true)
@Composable
private fun PreviewButtonSmallSecondary() {
    CrumbsTheme(darkTheme = false) {
        CrumbsButton(
            onClick = {},
            text = "Small",
            size = ButtonSize.Small,
            style = ButtonStyle.Secondary
        )
    }
}
```

**Step 2: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit button component**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButton.kt
git commit -m "feat(designsystem): add CrumbsButton component with variants"
```

---

## Task 8: CrumbsButton Roborazzi Tests

**Files:**
- Create: `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButtonTest.kt`

**Step 1: Create test file with screenshot tests**

Create file `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsButtonTest.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class CrumbsButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun button_primary_medium_enabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Click Me",
                    style = ButtonStyle.Primary,
                    size = ButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_primary_medium_enabled_light.png")
    }

    @Test
    fun button_primary_medium_enabled_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsButton(
                    onClick = {},
                    text = "Click Me",
                    style = ButtonStyle.Primary,
                    size = ButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_primary_medium_enabled_dark.png")
    }

    @Test
    fun button_primary_medium_disabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Disabled",
                    enabled = false,
                    style = ButtonStyle.Primary,
                    size = ButtonSize.Medium
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_primary_medium_disabled_light.png")
    }

    @Test
    fun button_secondary_small_enabled_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsButton(
                    onClick = {},
                    text = "Small",
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Small
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsButton_secondary_small_enabled_light.png")
    }
}
```

**Step 2: Record baseline screenshots**

Run: `./gradlew :core:designsystem:recordRoborazziDebug`
Expected: 4 PNG files generated in `src/test/screenshots/`

**Step 3: Verify screenshots were generated**

Run: `ls core/designsystem/src/test/screenshots/CrumbsButton_*.png | wc -l`
Expected: 4

**Step 4: Run verification (should pass since we just recorded)**

Run: `./gradlew :core:designsystem:verifyRoborazziDebug`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 5: Commit tests and screenshots**

```bash
git add core/designsystem/src/test/
git commit -m "test(designsystem): add Roborazzi screenshot tests for CrumbsButton"
```

---

## Task 9: CrumbsCard Component

**Files:**
- Create: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsCard.kt`

**Step 1: Implement CrumbsCard**

Create file `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsCard.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsSpacing

/**
 * Crumbs card with bottom-end cut corner
 *
 * Component variants for testing:
 * - With/without content
 * - Small/normal size
 * - Light/dark theme
 */

enum class CardSize {
    Small, Normal
}

@Composable
fun CrumbsCard(
    modifier: Modifier = Modifier,
    size: CardSize = CardSize.Normal,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    val shape = when (size) {
        CardSize.Small -> CrumbsShapes.cardSmall
        CardSize.Normal -> CrumbsShapes.card
    }

    val contentPadding = when (size) {
        CardSize.Small -> spacing.md
        CardSize.Normal -> spacing.lg
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.surface,
        contentColor = colors.textPrimary,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}

// Previews
@Preview(name = "Normal Card Light", showBackground = true)
@Composable
private fun PreviewCardNormalLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Card Title")
            Text("Card content goes here")
        }
    }
}

@Preview(name = "Normal Card Dark", showBackground = true)
@Composable
private fun PreviewCardNormalDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Card Title")
            Text("Card content goes here")
        }
    }
}

@Preview(name = "Small Card", showBackground = true)
@Composable
private fun PreviewCardSmall() {
    CrumbsTheme(darkTheme = false) {
        CrumbsCard(
            size = CardSize.Small
        ) {
            Text("Small card")
        }
    }
}
```

**Step 2: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit card component**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsCard.kt
git commit -m "feat(designsystem): add CrumbsCard component"
```

---

## Task 10: CrumbsCard Roborazzi Tests

**Files:**
- Create: `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsCardTest.kt`

**Step 1: Create screenshot tests for card**

Create file `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsCardTest.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class CrumbsCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun card_normal_withContent_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = CardSize.Normal
                ) {
                    Text("Card Title")
                    Text("Card content")
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsCard_normal_withContent_light.png")
    }

    @Test
    fun card_normal_withContent_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsCard(
                    modifier = Modifier.fillMaxWidth(),
                    size = CardSize.Normal
                ) {
                    Text("Card Title")
                    Text("Card content")
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsCard_normal_withContent_dark.png")
    }

    @Test
    fun card_small_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsCard(
                    size = CardSize.Small
                ) {
                    Text("Small")
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsCard_small_light.png")
    }
}
```

**Step 2: Record screenshots**

Run: `./gradlew :core:designsystem:recordRoborazziDebug`
Expected: 3 new PNG files generated

**Step 3: Verify tests pass**

Run: `./gradlew :core:designsystem:verifyRoborazziDebug`
Expected: BUILD SUCCESSFUL, all 7 tests pass (4 button + 3 card)

**Step 4: Commit tests and screenshots**

```bash
git add core/designsystem/src/test/
git commit -m "test(designsystem): add Roborazzi screenshot tests for CrumbsCard"
```

---

## Task 11: Refactor DudsBottomNav to CrumbsBottomNav

**Files:**
- Modify: `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/DudsBottomNav.kt` (rename and refactor)

**Step 1: Rename file and refactor to use Crumbs theme**

Rename and modify file to `core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNav.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsShapes
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.jayteealao.crumbs.designsystem.theme.LocalCrumbsColors

/**
 * Bottom navigation for Crumbs
 * Source-first organization: Twitter, Reddit, All, Map
 *
 * Component variants for testing:
 * - Each tab selected state
 * - Light/dark theme
 */

enum class BottomNavTab(
    val label: String,
    val icon: ImageVector
) {
    TWITTER("Twitter", Icons.Default.Language),  // TODO: Replace with ic_twitter.xml
    REDDIT("Reddit", Icons.Default.Language),    // TODO: Replace with ic_reddit.xml
    ALL("All", Icons.Default.ViewList),
    MAP("Map", Icons.Default.Map)
}

@Composable
fun CrumbsBottomNav(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    val colors = LocalCrumbsColors.current

    NavigationBar(
        containerColor = colors.surface,
        tonalElevation = CrumbsShapes.navigationBar
    ) {
        BottomNavTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.textPrimary,
                    selectedTextColor = colors.textPrimary,
                    indicatorColor = colors.accentAlpha,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary
                )
            )
        }
    }
}

// Previews
@Preview(name = "Twitter Selected Light", showBackground = true)
@Composable
private fun PreviewBottomNavTwitterLight() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.TWITTER,
            onTabSelected = {}
        )
    }
}

@Preview(name = "Twitter Selected Dark", showBackground = true)
@Composable
private fun PreviewBottomNavTwitterDark() {
    CrumbsTheme(darkTheme = true) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.TWITTER,
            onTabSelected = {}
        )
    }
}

@Preview(name = "Reddit Selected", showBackground = true)
@Composable
private fun PreviewBottomNavReddit() {
    CrumbsTheme(darkTheme = false) {
        CrumbsBottomNav(
            selectedTab = BottomNavTab.REDDIT,
            onTabSelected = {}
        )
    }
}
```

**Step 2: Remove old file**

Run: `git rm core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/DudsBottomNav.kt`

**Step 3: Verify code compiles**

Run: `./gradlew :core:designsystem:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit refactored component**

```bash
git add core/designsystem/src/main/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNav.kt
git commit -m "refactor(designsystem): rename DudsBottomNav to CrumbsBottomNav and use Crumbs theme"
```

---

## Task 12: CrumbsBottomNav Roborazzi Tests

**Files:**
- Create: `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNavTest.kt`

**Step 1: Create screenshot tests for bottom nav**

Create file `core/designsystem/src/test/java/com/github/jayteealao/crumbs/designsystem/components/CrumbsBottomNavTest.kt`:

```kotlin
package com.github.jayteealao.crumbs.designsystem.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jayteealao.crumbs.designsystem.theme.CrumbsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class CrumbsBottomNavTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomNav_twitterSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.TWITTER,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_twitterSelected_light.png")
    }

    @Test
    fun bottomNav_twitterSelected_dark() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = true) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.TWITTER,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_twitterSelected_dark.png")
    }

    @Test
    fun bottomNav_redditSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.REDDIT,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_redditSelected_light.png")
    }

    @Test
    fun bottomNav_allSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.ALL,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_allSelected_light.png")
    }

    @Test
    fun bottomNav_mapSelected_light() {
        composeTestRule.setContent {
            CrumbsTheme(darkTheme = false) {
                CrumbsBottomNav(
                    selectedTab = BottomNavTab.MAP,
                    onTabSelected = {}
                )
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/CrumbsBottomNav_mapSelected_light.png")
    }
}
```

**Step 2: Record screenshots**

Run: `./gradlew :core:designsystem:recordRoborazziDebug`
Expected: 5 new PNG files generated

**Step 3: Verify all tests pass**

Run: `./gradlew :core:designsystem:verifyRoborazziDebug`
Expected: BUILD SUCCESSFUL, all 12 tests pass (4 button + 3 card + 5 bottomNav)

**Step 4: Commit tests and screenshots**

```bash
git add core/designsystem/src/test/
git commit -m "test(designsystem): add Roborazzi screenshot tests for CrumbsBottomNav"
```

---

## Task 13: Final Verification and Documentation

**Files:**
- Create: `core/designsystem/README.md`

**Step 1: Create module README**

Create file `core/designsystem/README.md`:

```markdown
# Crumbs Design System

Custom design system module for the Crumbs Android app with Roborazzi screenshot testing.

## Components

- **CrumbsButton** - Primary action button with cut-corner styling
- **CrumbsCard** - Content container with bottom-end cut corner
- **CrumbsBottomNav** - Bottom navigation with source-first organization

## Design Tokens

### Colors
- Light theme: Cream background, mint surfaces, teal primary, neon lime accent
- Dark theme: Navy background, teal surfaces, mint primary, neon lime accent

### Shapes
Distinctive cut-corner system:
- Buttons: top-end cut (forward action)
- Cards: bottom-end cut (grounded content)
- Navigation: bottom-start cut (anchored)

### Typography
Standard scale from display (57sp) to caption (11sp)

### Spacing
Scale: 4dp, 8dp, 12dp, 16dp, 24dp, 32dp

## Usage

```kotlin
CrumbsTheme {
    val colors = LocalCrumbsColors.current
    val spacing = LocalCrumbsSpacing.current

    CrumbsButton(
        onClick = { /* action */ },
        text = "Click Me"
    )
}
```

## Testing

Screenshot tests with Roborazzi:

```bash
# Record new screenshots
./gradlew :core:designsystem:recordRoborazziDebug

# Verify against existing
./gradlew :core:designsystem:verifyRoborazziDebug

# Compare and generate report
./gradlew :core:designsystem:compareRoborazziDebug
```

## Color Palette

- `#c2e7da` - Soft mint/seafoam
- `#28666e` - Deep teal
- `#f1ffe7` - Pale cream
- `#1a1b41` - Dark navy-purple
- `#baff29` - Neon lime
```

**Step 2: Run full test suite**

Run: `./gradlew :core:designsystem:test`
Expected: BUILD SUCCESSFUL, all 12 tests pass

**Step 3: Verify all screenshots committed**

Run: `git status core/designsystem/src/test/screenshots/`
Expected: All PNG files tracked

**Step 4: Commit README**

```bash
git add core/designsystem/README.md
git commit -m "docs(designsystem): add module README with usage guide"
```

**Step 5: Final build verification**

Run: `./gradlew :core:designsystem:assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Success Criteria

- ✅ Module builds successfully
- ✅ All 3 components implemented (Button, Card, BottomNav)
- ✅ Light & dark themes work correctly
- ✅ Cut corner shapes render properly
- ✅ All 12 Roborazzi tests pass
- ✅ Screenshots committed to version control
- ✅ Documentation complete

## Next Steps

After implementation:
1. Use @superpowers:finishing-a-development-branch to merge/PR
2. Integrate design system into main app modules
3. Migrate existing components to use Crumbs theme
4. Add more components as needed (TextField, Chip, etc.)
