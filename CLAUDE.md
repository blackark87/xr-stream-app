# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

XR Video Player - An Android XR application for streaming and playing videos from SMB network shares and local storage in immersive VR environments. Built using Jetpack Compose, Jetpack XR SDK, and ExoPlayer.

- **Package**: `blackark.app.vr`
- **App ID**: `blackark.app.vr`
- **Version**: 0.1.1 (versionCode 2)
- **Min SDK**: 34 / Target SDK: 36 / Compile SDK: 36

## Build & Run Commands

```bash
# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Install on device
./gradlew installDebug

# Lint
./gradlew lint
```

## SMB Default Credentials (Dev Testing)

Set in `gradle.properties` (not committed):
```
SMB_DEFAULT_USERNAME=youruser
SMB_DEFAULT_PASSWORD=yourpass
```
These are injected as `BuildConfig.SMB_DEFAULT_USERNAME` / `BuildConfig.SMB_DEFAULT_PASSWORD`.

## Architecture

### Core Technology Stack
- **UI Framework**: Jetpack Compose with Jetpack XR SDK for immersive 3D/VR UI
- **Media Playback**: Media3 ExoPlayer with custom SMB data source
- **Database**: Room for persistent storage (version 12)
- **Networking**: JCIFS-NG for SMB/CIFS protocol support
- **Image Loading**: Coil with custom video thumbnail fetcher (`ThumbnailImageLoaderProvider` singleton)
- **Navigation**: Jetpack Navigation Compose
- **HTML Parsing**: jsoup 1.18.3 (for JVR Library metadata scraping)

### Key Architectural Patterns

**MVVM Architecture**
- ViewModels manage UI state and business logic
- Repository pattern abstracts data sources (Room database)
- StateFlow for reactive state management
- ViewModelFactory for dependency injection into ViewModels

**Custom Media Pipeline**
- `SMBDataSource`: Custom ExoPlayer DataSource for streaming video from SMB shares
- `SMBDataSource.Factory`: Creates data source instances with SMB credentials
- `VideoThumbnailFetcher`: Coil integration for generating video thumbnails from SMB and local sources
- Both components share the same SMB configuration and credentials

**XR-Specific Components**
- `XRPlaybackControls`: Spatial UI controls for VR playback
- `VideoPlayerScreen`: Immersive video player with 2D/180/360 format support
- Stereo mode detection (Side-by-Side, Top-Bottom) from filenames and video frame analysis
- Head tracking and controller input support (including 6DoF thumbstick axis events)

### Data Flow

1. **Server Connection**: User connects to SMB server or selects local storage → `SMBClient` / `LocalFileClient` establishes connection → Server saved to Room DB via `ServerRepository`
2. **File Browsing**: `FileBrowserViewModel` uses `SMBClient` or `LocalFileClient` to list files → Video thumbnails loaded via Coil + `VideoThumbnailFetcher`
3. **Video Playback**: User selects video → `VideoPlayerViewModel` creates ExoPlayer with `SMBDataSource.Factory` → Video streams from SMB share (or local file)
4. **State Persistence**: Playback position, favorites, video format preferences saved to `RecentVideo` / `VideoDisplaySettings` entities via repositories
5. **AV Library**: Background indexing scans connected source → `VideoLibraryIdentity` extracts codes → `AvLibraryRepository` tracks assets/locations → metadata enriched via `VirtualGroupMetadata` → displayed in `AvLibraryPanel`

### Database Schema (Version 12)

**SavedServer** — SMB server credentials and connection info. `isLocalStorage` flag for local device storage. WARNING: passwords stored in plain text.

**RecentVideo** — Playback history with resume positions, video format (2D/180/360), and stereo mode (Mono/SBS/TB).

**FavoriteVideo** — Favorites with source filtering (server + share).

**VideoDisplaySettings** — Per-video format/stereo mode preferences keyed by `filePath`.

**AvLibraryAsset** *(new in v12)* — One row per unique video work (identified by `sourceScope` + `normalizedCode`). Caches metadata (title, poster, studio, release date) via `metadataCacheKey` linking to `VirtualGroupMetadata`. Tracks presence and scan timestamps.

**AvAssetLocation** *(new in v12)* — One row per physical file. Foreign key to `AvLibraryAsset`. Tracks `filePath`, `partNumber`, file size, modification time, and content fingerprint. Cascade-deletes with parent asset.

**VirtualGroupMetadata / JVR Tables** — Cached JVR Library metadata (title, performers, genres, cover images): `VirtualGroupMetadata`, `VirtualGroupMetadataGenre`, `JvrPerformer`, `VirtualGroupMetadataPerformerCrossRef`, `VirtualGroupMetadataRecord`.

### SMB Integration Details

**SMB Configuration**
- Uses SMB 2.02 to 3.11 protocol versions
- Default port: 445
- Supports both authenticated shares and guest access
- URL format: `smb://server:port/share/path/to/file`

**Critical SMB Implementation Notes**
- `SMBDataSource` creates a new CIFS context per instance for thread safety
- Random access file support enables seeking/scrubbing in videos
- Graceful handling of interruptions during player shutdown
- 1MB buffer size for optimal SMB 3.x streaming performance

**Local Storage**
- `LocalFileClient` handles device-local file browsing (Movies, Downloads directories)
- Identified by `LOCAL_STORAGE_ADDRESS` constant in `SavedServer`
- Supports file deletion; SMB shares do not

### AV Library System

The AV library is a metadata-driven catalogue built from background indexing of connected sources.

**Key components:**
- `VideoLibraryIdentity`: Extracts normalized codes (e.g., `ABC-123`) from filenames/paths, detects multi-part videos, builds `sourceScope` (`server::share`) and `assetKey`
- `AvMetadataResolver`: Resolves linked `VirtualGroupMetadata` for a video path using DB cache key or direct code lookup
- `AvLibraryRepository`: Abstracts `AvLibraryDao` + `VirtualGroupMetadataDao`; supports fingerprint-based asset recovery for moved/renamed files
- `AvLibraryState`: Aggregates `AvLibrarySnapshot` (all works + filter options), `AvFilterState` (active filter family + selections), `AvScanState` (background scan progress)
- `AvLibraryPanel`: XR UI screen showing filterable work list with Studio / Cast / Release Date filters and a calendar date picker
- `AvLibrarySettingsStore`: SharedPreferences wrapper; `backgroundIndexingEnabled` defaults to **false** (opt-in)

**Filter families** (mutually exclusive): Studio (single select), Casts (multi-select), Release Date (single date via calendar).

**Multi-part video**: Files grouped by `assetKey` + `partNumber`. `AvLibraryWork` aggregates all parts.

### XR Activity Configuration

The app runs in "Full Space Managed" XR mode (`XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED`), meaning:
- Entire activity runs in immersive 3D space
- No traditional 2D window chrome
- All UI rendered via Jetpack XR Compose APIs

**Required XR Features**
- OpenXR API (version 1.1+) — required
- XR controller input (required), DPad binding extension
- Hand tracking, eye tracking, head tracking, scene understanding (optional)

### ViewModels and State Management

**MainDashboardViewModel**
- Manages both SMB and local storage connections
- Handles recent videos and favorites with source filtering
- File deletion support (local storage only)
- `FileBrowserViewMode` enum (List/Thumbnail) toggle
- AV library indexing: starts/stops based on `AvLibrarySettingsStore.backgroundIndexingEnabled`
- Requires Context, ServerRepository, VideoRepository, VideoDisplaySettingsRepository, AvLibraryRepository

**VideoPlayerViewModel**
- Controls ExoPlayer lifecycle
- Auto-detects video format and stereo mode from filename patterns
- `VideoDisplayHeuristics.inferDisplayProfileFromFrame()` for runtime frame-based detection (min 4096×2048, 2:1 aspect ±5%)
- Persists playback state and display settings to database
- Requires VideoRepository, VideoDisplaySettingsRepository

**Factory Pattern**
- All ViewModels use custom factories for dependency injection
- Factories instantiated at navigation composition level with appropriate dependencies

### AppState (Global State)

`AppState.kt` exposes:
- `keyEvents`: SharedFlow for hardware key events
- `controllerAxisEvents`: SharedFlow for 6DoF thumbstick axis events (`ControllerAxisEvent`)
- `dashboardPanelPose`: StateFlow for persistent XR spatial panel positioning
- `consumePlaybackBackKeyEvents`: StateFlow for playback mode key handling

### Key Input Handling

`MainActivity` captures two event types:
- `dispatchKeyEvent()` → emits to `AppState.keyEvents`
- `dispatchGenericMotionEvent()` → normalized 6DoF axis events with deadzone (8% threshold), checks X axes (X, HAT_X, Z, RX, HSCROLL) and Y axes (Y, HAT_Y, RZ, RY, VSCROLL, SCROLL), emits to `AppState.controllerAxisEvents`

### JVR Library Metadata

`JvrLibraryMetadataProvider` scrapes and caches metadata from JVR Library:
- HTML parsing via jsoup
- Automatic image downloading and local caching
- Cross-reference support for genres and performers
- HTTP retry logic; results stored in Room DB

## Development Guidelines

### Adding New Video Formats

When adding support for new video formats or stereo modes:
1. Update `VideoFormat` or `StereoMode` enums in `VideoPlayerViewModel.kt`
2. Add detection patterns in `detectStereoFromFilename()` and `detectFormatFromFilename()`
3. Update `VideoDisplaySettings` entity schema and increment database version
4. Add Room migration or use `fallbackToDestructiveMigration()` for development

### Working with SMB

When modifying SMB functionality:
- Test with both SMB 2.x and 3.x servers
- Handle authentication failures gracefully (guest vs. authenticated access)
- Use `withContext(Dispatchers.IO)` for all SMB operations
- Remember that paths in `SMBFileItem` include the full SMB URL structure

### Local Storage

When modifying local storage functionality:
- Use `LocalFileClient` for file listing and deletion
- Check `SavedServer.isLocalStorage` flag before applying SMB-specific logic
- File deletion is only supported for local storage, not SMB

### AV Library Development

- Background indexing is **opt-in** — always check `AvLibrarySettingsStore.isBackgroundIndexingEnabled()` before starting a scan
- Asset identity is `sourceScope + normalizedCode`; `sourceScope` format is `server::share`
- Content fingerprints (`AvAssetLocation.fingerprint`) enable recovery of assets after file rename/move
- When incrementing the DB version, add both `AvLibraryAsset` and `AvAssetLocation` to the migration

### XR UI Development

- Use `SpatialPanel` and `SpatialDialog` from Jetpack XR Compose
- Controller raycasting handled by XR runtime
- Persist spatial panel pose via `AppState.dashboardPanelPose`
- Test on both XR devices and emulator (XR features gracefully degrade)

### Testing SMB Streaming

For reliable testing of video streaming:
1. Use test videos of varying sizes (small <100MB, large >1GB)
2. Test seeking/scrubbing to different positions
3. Verify resume from saved position works correctly
4. Test network interruptions and reconnection

## Project Structure

```
app/src/main/java/blackark/app/vr/
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt                        # Room DB version 12
│   │   ├── dao/
│   │   │   ├── AvLibraryDao.kt                   # AV asset/location queries
│   │   │   ├── FavoriteVideoDao.kt
│   │   │   ├── ServerDao.kt
│   │   │   ├── VideoDao.kt
│   │   │   ├── VideoDisplaySettingsDao.kt
│   │   │   └── VirtualGroupMetadataDao.kt
│   │   └── entity/
│   │       ├── AvAssetLocation.kt                # Individual video files
│   │       ├── AvLibraryAsset.kt                 # Video works/titles
│   │       ├── FavoriteVideo.kt
│   │       ├── JvrPerformer.kt
│   │       ├── RecentVideo.kt
│   │       ├── SavedServer.kt
│   │       ├── VideoDisplaySettings.kt
│   │       ├── VirtualGroupMetadata.kt
│   │       ├── VirtualGroupMetadataGenre.kt
│   │       ├── VirtualGroupMetadataPerformerCrossRef.kt
│   │       └── VirtualGroupMetadataRecord.kt
│   ├── model/
│   │   ├── AvLibraryModels.kt                    # AvLibraryWork, AvLibrarySnapshot, filter options
│   │   └── LibraryVideoItem.kt                   # Interface for video model abstraction
│   └── repository/
│       ├── AvLibraryRepository.kt                # AV asset/location + metadata access
│       ├── ServerRepository.kt
│       ├── VideoDisplaySettingsRepository.kt
│       └── VideoRepository.kt
├── network/
│   ├── LocalFileClient.kt                        # Local device storage browsing
│   ├── SMBClient.kt
│   ├── SMBConfig.kt
│   └── SMBFileItem.kt
├── player/
│   └── SMBDataSource.kt
├── ui/
│   ├── components/
│   │   ├── FancyCards.kt
│   │   └── XRPlaybackControls.kt
│   ├── navigation/
│   │   └── Navigation.kt
│   ├── screens/
│   │   ├── AvLibraryPanel.kt                     # AV library filter/browse UI
│   │   ├── FileBrowserScreen.kt
│   │   ├── MainDashboardScreen.kt
│   │   ├── ServerConnectionScreen.kt
│   │   └── VideoPlayerScreen.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── viewmodel/
│       ├── AvLibraryState.kt                     # AvFilterState, AvScanState, AvLibraryState
│       ├── FileBrowserViewModel.kt
│       ├── MainDashboardViewModel.kt
│       ├── MainDashboardViewModelFactory.kt
│       ├── ServerConnectionViewModel.kt
│       ├── VideoPlayerViewModel.kt
│       └── VideoPlayerViewModelFactory.kt
├── utils/
│   ├── AvLibrarySettingsStore.kt                 # SharedPrefs: backgroundIndexingEnabled
│   ├── AvMetadataResolver.kt                     # Resolves VirtualGroupMetadata for a video path
│   ├── JvrLibraryMetadataProvider.kt             # JVR Library metadata scraper (jsoup)
│   ├── ServerCredentialAutofillStore.kt          # SharedPreferences credential cache
│   ├── ThumbnailImageLoaderProvider.kt           # Coil ImageLoader singleton
│   ├── VideoDisplayHeuristics.kt                 # Frame-based stereo/format detection
│   ├── VideoLibraryIdentity.kt                   # Code extraction, assetKey/sourceScope building
│   └── VideoThumbnailFetcher.kt
├── AppState.kt                                   # Global: keyEvents, controllerAxisEvents, panelPose
├── MainActivity.kt                               # Entry point, key + motion event dispatch
└── XRStreamApplication.kt                        # Application class with Coil configuration
```