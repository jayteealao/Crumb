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
