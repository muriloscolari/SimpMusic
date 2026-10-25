# AGENTS.md - SimpMusic (Custom Fork) Agent Guidelines

This document provides essential architectural context, operational rules, workflow conventions, and gotchas for AI coding agents working on this repository.

---

## 🎯 1. Repository Identity & Branch Workflow

### 1.1 Fork Hierarchy
- **Main App Repository**: `muriloscolari/SimpMusic` (forked from `maxrave-dev/SimpMusic`).
- **Core Submodule (`core/`)**: `muriloscolari/core` (forked from `maxrave-dev/core` or `MediaServiceCore`).
- **Base Integration Branch**: **`custom`** in BOTH repositories.

### 1.2 Branching Rules (MANDATORY)
1. **Never work directly on `main` or `dev`**: Upstream updates land there.
2. **Always branch off `custom`**:
   - Create atomic branches named `feat/<feature-name>`, `fix/<bug-name>`, or `perf/<improvement-name>` directly from `custom`.
   - Test and verify changes locally.
   - Commit atomic changes with clear Conventional Commit messages (`feat:`, `fix:`, `refactor:`, `chore:`).
   - **Bump app version** (`version-name` and `version-code` in `gradle/libs.versions.toml`) before merging into `custom`.
   - Merge back into `custom` and push both the feature branch and `custom` to `origin`.
3. **Always Bump Version Before Merging into `custom` (MANDATORY)**:
   - Every push to `custom` triggers the automated CI/CD release workflow (`.github/workflows/custom-build-release.yml`), creating a public release tagged as `v${VERSION}`.
   - To avoid release tag collisions and ensure the in-app auto-updater (`VersionManager.kt`) notifies users of new builds, **always increment** the version numbers in `gradle/libs.versions.toml`:
     - `version-name` (e.g. `2.3.2` -> `2.3.3`)
     - `version-code` (e.g. `62` -> `63`)
   - Never merge into `custom` with an already-released version number.
4. **Submodule Synchronization**:
   - If a change requires edits inside `core/`, work on an atomic branch inside `core` derived from its `custom` branch.
   - Commit and push to `muriloscolari/core:custom` FIRST.
   - Only then stage and commit the updated `core` submodule commit pointer in the root `SimpMusic` repository.

### 1.3 Communication & Language Convention
- **User Interaction**: Respond in the user's preferred language (Portuguese by default in this workspace).
- **Code Comments & Commits**: Write in English with descriptive summaries and rationale. Preserve all pre-existing architectural comments and notes.

---

## 🏗️ 2. High-Level Architecture & Code Map

The project is built on **Kotlin Multiplatform (Compose Multiplatform)** targeting Android and Desktop (Linux, Windows, macOS):

```
SimpMusic (Root)
├── composeApp/                 # Shared UI & ViewModels (Android + Desktop JVM)
│   ├── src/commonMain/kotlin/  # Multiplatform shared Compose UI & logic
│   │   └── com/maxrave/simpmusic/
│   │       ├── ui/screen/player/
│   │       │   ├── NowPlayingScreen.kt          # Shell, sheets, dialogs, state machines
│   │       │   ├── MiniPlayer.kt                # Bottom bar player across desktop & mobile
│   │       │   ├── FullscreenPlayer.kt          # Fullscreen landscape player
│   │       │   └── content/                     # Three distinct player styles
│   │       │       ├── NowPlayingContentSpotify.kt       # Spotify / Classic mode
│   │       │       ├── NowPlayingContentAppleMusic.kt    # Apple Music mode
│   │       │       ├── NowPlayingContentM3Expressive.kt  # Material 3 Expressive mode
│   │       │       ├── NowPlayingContentState.kt         # Shared immutable state & actions
│   │       │       └── applemusic/                       # Apple Music sub-components
│   │       ├── viewModel/                       # SharedViewModel, LibraryViewModel, etc.
│   │       └── service/                         # SimpConnect (LAN remote control)
│   ├── src/androidMain/kotlin/ # Android-specific Compose adaptations
│   └── src/jvmMain/kotlin/     # Desktop-specific adaptations (JNA, Taskbar, System Tray)
│
├── core/ (Git Submodule)       # Business logic, repositories, media players
│   ├── domain/                 # Models, interfaces (MediaPlayerHandler, Repositories)
│   ├── data/                   # Data sources, Room database, network APIs, Media handlers
│   │   ├── src/androidMain/    # Android MediaServiceHandlerImpl
│   │   └── src/jvmMain/        # Desktop JvmMediaPlayerHandlerImpl (libmpv)
│   ├── media/media3/           # Android ExoPlayer / Media3 service & Android Auto
│   ├── media/media-jvm/        # Desktop MPV player (MpvPlayer.kt)
│   └── service/                # Background sync, scraper, AI, lyrics
│
├── androidApp/                 # Android application entry point & manifest
└── desktopApp/                 # Desktop JVM launcher & Conveyor packaging configuration
```

---

## 🎨 3. Key Custom Features & Parity Rules

When modifying or introducing features, keep these custom fork components in mind:

1. **Player Style Parity (3 Player Modes)**:
   - SimpMusic supports 3 selectable player visual modes: **Spotify**, **Apple Music**, and **Material 3 Expressive**.
   - When adding controls, toggles, or playback features (e.g., Start Radio, queue controls, device handoff), **implement in all 3 styles**. Never leave any mode behind.
2. **SimpConnect (LAN Remote Control & Handoff)**:
   - Spotify Connect-like LAN discovery via mDNS and WebSocket/P2P control between Android and Desktop instances.
   - Controller state and playback handoff live in `SimpConnectManager` and `SharedViewModel`.
3. **Desktop Media Enhancements**:
   - **Perceptual Volume Scaling**: Located in `core/media/media-jvm/src/main/java/com/simpmusic/media_jvm/mpv/MpvPlayer.kt`. Volume curve uses `masterFraction.pow(1.45)` to ensure smooth acoustic granularity below 50%.
   - **Windows Taskbar Thumbnail Toolbar**: Located in `composeApp/src/jvmMain/kotlin/com/maxrave/simpmusic/WindowsTaskbarThumbnailController.kt`. Controls media buttons (Prev/Play/Next) natively via JNA `ITaskbarList3`.
   - **Linux AppImage Auto-Updates**: Integrated zsync delta updates against GitHub releases.
   - **Windows Silent Updates**: Handled via `.appinstaller` / MSIX background updates.

---

## ⚙️ 4. Build, Verification & Testing Commands

### 4.1 Fast Development Validation
```bash
# 1. Compile Shared UI and Desktop JVM:
./gradlew :composeApp:compileKotlinJvm :desktopApp:compileKotlinJvm

# 2. Compile Android Main:
./gradlew :composeApp:compileAndroidMain

# 3. Run Unit Tests:
./gradlew :composeApp:jvmTest
```

### 4.2 Packaging & Local Release Builds
Release builds follow `.github/workflows/custom-build-release.yml`:
```bash
# Export library definitions
./gradlew exportLibraryDefinitions --no-configuration-cache

# Build Android APKs (arm64, armv7, x86_64, universal)
./gradlew :androidApp:assembleRelease --no-configuration-cache

# Prepare Desktop MPV native binaries
./gradlew :composeApp:mpvSetupAll --no-configuration-cache

# Write Conveyor configuration
./gradlew :desktopApp:writeConveyorConfig --no-configuration-cache

# Package Linux AppImage
conveyor --agree-to-license=1 --console=plain -Kapp.machines=linux.amd64-glibc make linux-app
./gradlew :desktopApp:packageConveyorAppImage --no-configuration-cache

# Package Windows MSIX and site
conveyor --agree-to-license=1 --console=plain -Kapp.machines=windows.amd64 make site
```

> [!CAUTION]
> **Never prefix Gradle or Conveyor commands with `timeout`** when running on the local machine unless explicitly instructed, as this can orphan child processes or break terminal locks.

---

## 🚨 5. Common Pitfalls & Rules of Thumb (Gotchas)

1. **Submodule Drift**:
   - Always run `git status` AND `git -C core status` before starting and after finishing work.
   - Avoid unintentional dirty submodule pointer commits.
2. **DataStore Disk Writes**:
   - Persisting to DataStore is an asynchronous, suspending disk write. Never block the UI thread or gate immediate audio/visual feedback behind DataStore completion.
3. **Do Not Touch iOS or macOS Targets**:
   - Focus strictly on Android, Linux, and Windows unless the user explicitly requests otherwise.
4. **Preserve Existing Documentation**:
   - Maintain documentation integrity. Do not delete or summarize away explanatory comments explaining edge cases (e.g. Android 12 background restrictions, MPV soname workarounds, Skia rendering constraints).
