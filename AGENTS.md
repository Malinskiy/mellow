# AGENTS.md — Agentic Coding Guidelines for Mellow

## Project Overview

Mellow is a native Android music player for Jellyfin. Kotlin, Jetpack Compose, Media3, offline-first Room database.

### Code Structure

```
mellow/
├── app/                    # Application entry, DI wiring, navigation
├── core/
│   ├── common/             # Shared utilities, result types, extensions
│   ├── model/              # Domain models (pure Kotlin, no Android deps)
│   ├── network/            # Jellyfin SDK wrapper (jellyfin-sdk-kotlin)
│   ├── database/           # Room DB, DAOs, entities, migrations
│   ├── data/               # Repository implementations (offline-first bridge)
│   └── player/             # Media3 player, MediaLibraryService, Android Auto
├── feature/
│   ├── home/               # Home screen (recent, favorites, quick access)
│   ├── library/            # Library browser (albums, artists, genres)
│   ├── player/             # Now playing, queue, lyrics
│   ├── search/             # Global search
│   └── settings/           # App configuration
└── sync/                   # WorkManager-based library sync
```

### Module Dependency Rules (HARD BLOCKS)

- `core/model` has ZERO Android dependencies (pure Kotlin data classes)
- `core/network` NEVER imports Room classes
- `core/database` NEVER imports jellyfin-sdk classes
- `core/data` is the ONLY module that bridges network ↔ database
- `feature/*` modules NEVER depend on each other
- Only `app` may depend on all modules (wiring)

### Offline-First Architecture (CRITICAL)

Room is the source of truth. The Jellyfin API is a sync target.

- Read path: Always read from Room. Background sync keeps it fresh.
- Write path: Write to Room immediately. Sync to server when online.
- Favorites, ratings, play counts all work offline and sync later.

**Never bypass Room to read directly from the API in feature code.**

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run specific module tests
./gradlew :core:database:test
./gradlew :core:player:test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Check all (build + test + lint)
./gradlew check
```

### Gradle Wrapper

If the wrapper is missing, generate it:
```bash
gradle wrapper --gradle-version 8.12
```

## Code Style Guidelines

### Kotlin

1. **Error Handling**:
   - Use `MellowResult<T>` (sealed interface in `core/common`) for all repository methods
   - Never throw exceptions from repositories — wrap in `MellowResult.Error`
   - ViewModels convert `MellowResult` to UI state

2. **Imports**:
   - Group: stdlib, kotlinx, android/androidx, third-party, project
   - Use explicit imports (no wildcard `*`)

3. **Naming**:
   - `camelCase` for functions, properties, local variables
   - `PascalCase` for classes, interfaces, type aliases, composables
   - `SCREAMING_SNAKE_CASE` for constants
   - Prefix unused variables with `_`

4. **Constructors and IO**:
   - NEVER perform IO in constructors or `init {}` blocks
   - Use suspend functions for all IO operations

5. **Coroutines**:
   - Use `Flow<T>` for observable data streams from Room
   - Use `StateFlow<T>` in ViewModels for UI state
   - Use `viewModelScope` for coroutine launches in ViewModels
   - Never use `GlobalScope`

6. **Formatting**:
   - Follow standard Kotlin style (ktlint defaults)
   - Max line length: 120 characters

### Jetpack Compose

1. **Composable Functions**:
   - `@Composable` functions that emit UI start with uppercase (e.g., `AlbumCard`)
   - Accept `Modifier` as first optional parameter: `fun AlbumCard(modifier: Modifier = Modifier, ...)`
   - Use `remember` and `derivedStateOf` for computed values

2. **State Management**:
   - ViewModels expose `StateFlow<UiState>`
   - Screens collect state with `collectAsStateWithLifecycle()`
   - One-shot events use `Channel<Event>` consumed via `LaunchedEffect`

3. **Lists**:
   - Use `LazyColumn`/`LazyVerticalGrid` for all scrollable lists
   - Use Paging 3 with `collectAsLazyPagingItems()` for large datasets
   - Always provide stable `key` parameter

4. **Images**:
   - Use Coil `AsyncImage` for all network images
   - Provide `placeholder` (blurhash) and `error` drawables
   - Set explicit `contentScale` and `contentDescription`

### Room Database

1. **Entities**: 
   - All entities in `core/database/entity/`
   - Use `@Upsert` for sync operations (not INSERT OR REPLACE)
   - Include `lastSynced: Long` on every entity

2. **DAOs**:
   - Return `Flow<List<T>>` for observable queries
   - Return `PagingSource<Int, T>` for paginated queries
   - Suspend functions for writes

3. **Migrations**:
   - Export schemas (`room { schemaDirectory(...) }`)
   - Write migration tests for every schema change
   - Never use `fallbackToDestructiveMigration()`

### Media3

1. **MellowMediaService**:
   - Extends `MediaLibraryService` (not `MediaBrowserServiceCompat`)
   - Creates ExoPlayer with audio-only attributes
   - Exposes content tree for Android Auto via `LibrarySessionCallback`

2. **Audio Attributes**:
   - `AUDIO_CONTENT_TYPE_MUSIC` + `USAGE_MEDIA`
   - `handleAudioFocus = true` (automatic focus management)
   - `handleAudioBecomingNoisy = true` (pause on headphone disconnect)

3. **Caching**:
   - `SimpleCache` with `LeastRecentlyUsedCacheEvictor` for streaming
   - Separate `DownloadManager` for offline downloads
   - Shared cache directory between streaming and downloads

### Jellyfin SDK

1. **Client**:
   - Use `JellyfinClientWrapper` (in `core/network`) — never create `Jellyfin` instances directly
   - Call `connect()` before any API use
   - Call `authenticate()` with stored token on app start

2. **API Calls**:
   - Always map SDK DTOs (`BaseItemDto`) to domain models (`Album`, `Track`, `Artist`)
   - Mapping happens in `core/data` repository implementations
   - Never expose SDK types to feature modules

3. **Images**:
   - Build image URLs: `${serverUrl}/Items/${itemId}/Images/Primary?maxWidth=600&quality=90`
   - Use `ImageBlurHashes` for progressive loading placeholders

### Artwork Pipeline

Two delivery mechanisms — use the right one for the context:

1. **In-app UI** (Compose screens — Coil `AsyncImage`):
   - Use `jellyfinImageUrl(serverUrl, itemId)` → HTTPS URL loaded by Coil
   - For tracks: always fall back to album image: `track.imageId ?: track.albumId`
   - ~54% of tracks have no own `imageTag` — the album holds the art

2. **System surfaces** (notification, Android Auto, MediaSession):
   - Use `content://` URIs via `ArtworkProvider` ContentProvider
   - URI format: `content://${packageName}.artwork/${itemId}`
   - ArtworkProvider fetches from Jellyfin API, caches to `cacheDir/artwork/{itemId}.jpg`
   - Cached images survive offline — Android Auto works without server
   - `ContentBitmapLoader` on MediaSession resolves `content://` URIs (default Media3 BitmapLoader only handles HTTPS)

**Never use HTTPS URLs for MediaSession/notification artwork** — Media3's default `SimpleBitmapLoader` uses `URL.openStream()` which doesn't support `content://`, and Android Auto rejects non-content:// URIs.

**Never gate artwork on `track.imageId != null`** — fall back to `track.albumId` since most tracks inherit album art.

## UI State Pattern (MANDATORY for all data-dependent screens)

Every screen or section that loads data from a repository, API, or database MUST handle all four states:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}
```

### Rules

1. **Never show placeholder/fake data as if it were real** — if data hasn't loaded, show a loading indicator
2. **Never show a blank screen** — always show either loading spinner, empty state message, or error with retry
3. **Loading**: `CircularProgressIndicator` centered, or shimmer placeholders for lists/grids
4. **Empty**: Icon + message explaining why it's empty ("No albums yet", "No servers found on network")
5. **Error**: Error message + "Retry" button. Never crash or show raw exception text
6. **Success**: Render the actual data

### Where this applies

- Library tabs (albums, artists, tracks, genres, folders)
- Album detail (track list)
- Artist detail (top tracks, discography)
- Search results
- Favorites (tracks, albums, artists)
- Playlists list + playlist detail
- Login screen server discovery section
- Settings server status

### Mock data for screenshot tests

Screenshot tests call composables with mock data directly — they bypass ViewModels entirely.
Mock data is passed via default parameter values on screen composables. This means:
- Production: ViewModel provides real state (loading/empty/error/success)
- Screenshot tests: Call `LibraryScreen(albumItems = mockData)` — always gets success path
- The loading/empty/error paths need separate screenshot test cases if visual testing is desired

## Bug Fix Workflow

1. **Reproduce first**: Write a test (unit or instrumented) that fails
2. **Fix minimally**: Smallest change that fixes the bug
3. **Verify**: Run the failing test — it must pass
4. **No refactoring during bugfix**: Fix only. Refactor separately.

## Pre-Commit Checklist

Before every commit:
```bash
./gradlew check
```

This runs: compile, lint, unit tests. Fix all issues before committing.

## Important Files

- `gradle/libs.versions.toml` — Version catalog (all dependency versions)
- `settings.gradle.kts` — Module includes and repository configuration
- `ARCHITECTURE.md` — Detailed architecture decisions and rationale
- `FEATURES.md` — Complete feature list with priorities
- `core/player/src/main/java/dev/mellow/core/player/MellowMediaService.kt` — The media service
- `core/database/src/main/java/dev/mellow/core/database/MellowDatabase.kt` — Room database

## Emulator Image Interaction Rule

When taking screenshots or interacting with images via EMU MCP or any visual tool, the widest dimension must be under 2000px. Agents will break/error on images with 2000+ px widest dimension. Always verify device screen dimensions before capturing screenshots (`device_info` tool), and use appropriate `maxWidth`/`maxHeight` parameters when building image URLs.

## Android Auto Testing

Use the Desktop Head Unit (DHU) for Android Auto testing:
```bash
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

Or use EMU MCP tools to test on emulator — see `.agents/skills/emu-testing/SKILL.md`.

## Environment

- Kotlin 2.1.10
- AGP 8.8.2
- Compose BOM 2025.06.01
- Media3 1.6.0
- Hilt 2.56.2
- Room 2.7.1
- jellyfin-sdk-kotlin 1.8.7
- minSdk 26, targetSdk 36
