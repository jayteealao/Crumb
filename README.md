# Crumb

Single-user Android bookmark manager for Twitter & Reddit. Kotlin + Jetpack Compose, brutalist mono design.

## Build

```
./gradlew :app:assembleDebug
```

Release variant (unsigned unless `SIGNING_STORE_FILE` is exported):

```
./gradlew :app:assembleRelease
```

## Test

```
./gradlew test verifyRoborazziDebug
```

Instrumentation tests (require a running emulator):

```
./gradlew :app:connectedDebugAndroidTest
```

Release-cleanliness gate (confirms `DebugDataInjector` is excluded from release APK):

```
./gradlew :app:verifyReleaseDebugInjectorAbsent
```

## End-to-end (Maestro)

Prereqs:

- Android Studio + AVD profile `Medium_Phone_API_36`
- [Maestro CLI](https://docs.maestro.dev/getting-started/installing-maestro) ≥ 2.4
- [lazylogcat](https://github.com/parfenovvs/lazylogcat) for filtered log capture

Run (Windows):

```
pwsh scripts/run-maestro.ps1
```

Run (macOS / Linux / Git Bash):

```
bash scripts/run-maestro.sh
```

Either script boots the AVD, installs `app-debug.apk`, captures package-scoped logs to `build/maestro-logs/<timestamp>.log`, executes the four flows in `maestro/`, and stops the emulator.

Manual fallback (with the emulator already running):

```
./gradlew :app:installDebug
maestro test maestro/happy_path.yaml maestro/long_press.yaml maestro/filter_overlay.yaml maestro/sync_error.yaml
```

Flows depend on the debug-only `DebugDataInjector`, which seeds the Room database with 8 fake bookmarks + 5 tags when launched with `debug_action=seed`. The injector lives in `app/src/debug/` and is excluded from release builds by AGP source-set rules.

## Project layout

- `app/` — Compose app, NavHost, screens
- `core/designsystem/` — brutalist design system (theme + components + layouts)
- `core/data/` — cross-module data types
- `core/pref/` — DataStore-backed preferences (auth tokens)
- `core/models/` — shared data classes
- `feature/twitter/`, `feature/reddit/` — provider integrations + per-feed view models
- `maestro/` — end-to-end flows
