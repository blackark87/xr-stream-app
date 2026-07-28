# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

XR Video Player - An Android XR application for streaming and playing videos from SMB network shares and local storage in immersive VR environments. Built using Jetpack Compose, Jetpack XR SDK, and ExoPlayer.

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
```

## Codex CLI Build and Compilation Prohibition

- Never run builds, compilation, tests, packaging, installation, or resource compilation from the Codex CLI in this repository. This includes every Gradle task such as `./gradlew build`, `./gradlew test`, and `./gradlew installDebug`, because test tasks also compile project sources.
- Do not install, download, configure, or reuse an Android SDK, including a temporary or cached SDK, to bypass this rule.
- The Build & Run commands above are reference commands for the user's Android development or device environment only. Codex must not execute them.
- Limit Codex validation to source inspection and non-compiling static checks such as `git diff --check`. Report all Android builds, compilation, and tests as not run under this repository policy.

## Architecture

### Core Technology Stack
- **UI Framework**: Jetpack Compose with Jetpack XR SDK for immersive 3D/VR UI
- **Media Playback**: Media3 ExoPlayer with custom SMB data source
- **Database**: Room for persistent storage (saved servers, recent videos, favorites)
- **Networking**: JCIFS-NG for SMB/CIFS protocol support
- **Image Loading**: Coil with custom video thumbnail fetcher
- **Navigation**: Jetpack Navigation Compose

### Key Architectural Patterns

**MVVM Architecture**
- ViewModels manage UI state and business logic
- Repository pattern abstracts data sources (Room database)
- StateFlow for reactive state management
- ViewModelFactory for dependency injection into ViewModels

**Custom Media Pipeline**
- `SMBDataSource`: Custom ExoPlayer DataSource for streaming video from SMB shares
- `SMBDataSource.Factory`: Creates data source instances with SMB credentials
- `VideoThumbnailFetcher`: Coil integration for generating video thumbnails from SMB sources
- Both components share the same SMB configuration and credentials

**XR-Specific Components**
- `XRPlaybackControls`: Spatial UI controls for VR playback
- `VideoPlayerScreen`: Immersive video player with 2D/180/360 format support
- Stereo mode detection (Side-by-Side, Top-Bottom) from filenames
- Head tracking and controller input support

### Data Flow

1. **Server Connection**: User connects to SMB server → `SMBClient` establishes connection → Server saved to Room DB via `ServerRepository`
2. **File Browsing**: `FileBrowserViewModel` uses `SMBClient` to list files → Video thumbnails loaded via Coil + `VideoThumbnailFetcher`
3. **Video Playback**: User selects video → `VideoPlayerViewModel` creates ExoPlayer with `SMBDataSource.Factory` → Video streams from SMB share
4. **State Persistence**: Playback position, favorites, and video format preferences saved to `RecentVideo` entity via `VideoRepository`

### Database Schema

**SavedServer Table**
- Stores SMB server credentials and connection info
- Special handling for local storage (`isLocalStorage` flag)
- WARNING: Passwords stored in plain text - consider encryption for production

**RecentVideo Table**
- Tracks playback history with resume positions
- Stores video format (2D/180/360) and stereo mode (Mono/SBS/TB)
- Favorites system via `isFavorite` flag

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

### XR Activity Configuration

The app runs in "Full Space Managed" XR mode (`XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED`), meaning:
- Entire activity runs in immersive 3D space
- No traditional 2D window chrome
- All UI rendered via Jetpack XR Compose APIs

**Required XR Features**
- OpenXR API (version 1.1+)
- XR controller input (required)
- Hand tracking, eye tracking, head tracking (optional but requested)

### ViewModels and State Management

**MainDashboardViewModel**
- Manages server connections and file browsing
- Handles recent videos and favorites
- Requires Context, ServerRepository, and VideoRepository dependencies

**VideoPlayerViewModel**
- Controls ExoPlayer lifecycle
- Auto-detects video format and stereo mode from filename patterns
- Persists playback state to database
- Requires VideoRepository dependency

**Factory Pattern**
- All ViewModels use custom factories for dependency injection
- Factories instantiated at navigation composition level with appropriate dependencies

### Key Input Handling

`MainActivity.dispatchKeyEvent()` captures hardware key events and emits them to `AppState.keyEvents` SharedFlow for XR controller input handling throughout the app.

## Development Guidelines

### Adding New Video Formats

When adding support for new video formats or stereo modes:
1. Update `VideoFormat` or `StereoMode` enums in `VideoPlayerViewModel.kt`
2. Add detection patterns in `detectStereoFromFilename()` and `detectFormatFromFilename()`
3. Update `RecentVideo` entity schema and increment database version
4. Add Room migration or use `fallbackToDestructiveMigration()` for development

### Working with SMB

When modifying SMB functionality:
- Test with both SMB 2.x and 3.x servers
- Handle authentication failures gracefully (guest vs. authenticated access)
- Use `withContext(Dispatchers.IO)` for all SMB operations
- Remember that paths in `SMBFileItem` include the full SMB URL structure

### XR UI Development

- Use `SpatialPanel` and `SpatialDialog` from Jetpack XR Compose
- Controller raycasting handled by XR runtime
- Test on both XR devices and emulator (XR features gracefully degrade)

### Testing SMB Streaming

For reliable testing of video streaming:
1. Use test videos of varying sizes (small <100MB, large >1GB)
2. Test seeking/scrubbing to different positions
3. Verify resume from saved position works correctly
4. Test network interruptions and reconnection

## Project Structure

```
app/src/main/java/com/example/myapplication/
├── data/
│   ├── database/         # Room database, DAOs, entities
│   └── repository/       # Repository pattern for data access
├── network/              # SMB client and file operations
├── player/               # Custom ExoPlayer SMB data source
├── ui/
│   ├── components/       # Reusable XR UI components
│   ├── navigation/       # Navigation setup and routes
│   ├── screens/          # Screen composables
│   ├── theme/            # Material theme configuration
│   └── viewmodel/        # ViewModels and factories
├── utils/                # Utilities (video thumbnail fetcher)
├── AppState.kt           # Global app state (key events)
├── MainActivity.kt       # Entry point
└── XRStreamApplication.kt # Application class with Coil configuration
```
