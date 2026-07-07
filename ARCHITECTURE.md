# Mellow Architecture

A fast, offline-first Jellyfin music player for Android with first-class Android Auto support.

## Why Mellow Exists

Every open-source Jellyfin music client fails at the same things:
- **Background playback breaks on modern Android** (12+) due to incorrect service lifecycle management
- **Offline is an afterthought** — downloaded files with no metadata queries, no offline favorites
- **Android Auto is perpetually "coming soon"** — Finamp #24 (2021), Fintunes #57 (2021), both unresolved
- **Large libraries (10K+ tracks) are unusable** — full-library loads into memory, no pagination
- **No smart features** — no smart playlists, no personal mixes, no listening stats

Symfonium (closed-source, paid) proves the feature set users want. Mellow brings that quality to open source.

## Tech Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | Kotlin | Direct Media3 API access, no bridge overhead (Flutter/RN killed Finamp/Fintunes) |
| UI | Jetpack Compose + Material 3 | Modern declarative UI, dynamic color from album art |
| Media Engine | Media3 (ExoPlayer) | Gapless playback, audio offload, Android Auto built-in |
| Android Auto | MediaLibraryService | Content tree browsing, voice search, no phone interaction needed |
| DI | Hilt | Standard Android DI, Compose integration |
| Networking | jellyfin-sdk-kotlin + OkHttp | Official SDK with 47 type-safe API classes, generated from OpenAPI spec |
| Database | Room (SQLite) | SQL enables smart playlist queries, offline-first source of truth |
| Image Loading | Coil | Compose-native, memory-efficient, disk cache for album art |
| Background Sync | WorkManager | Reliable background work without battery drain |
| Caching | Media3 SimpleCache | Bounded streaming cache with LRU eviction |
| Serialization | kotlinx.serialization | SDK-compatible, no reflection |
| Navigation | Compose Navigation | Type-safe routes |
| Lists | Paging 3 | Incremental loading — fixes the "46K tracks = multi-minute load" problem |

## Module Structure

```
mellow/
├── app/                    # Application entry point, DI wiring, navigation
├── core/
│   ├── common/             # Shared utilities, extensions, result types
│   ├── model/              # Domain models (pure Kotlin, no Android deps)
│   ├── network/            # Jellyfin API client (jellyfin-sdk-kotlin wrapper)
│   ├── database/           # Room database, DAOs, entities, migrations
│   ├── data/               # Repository implementations (offline-first)
│   └── player/             # Media3 player, MediaLibraryService, Android Auto
├── feature/
│   ├── home/               # Home screen (recent, favorites, quick access)
│   ├── library/            # Library browser (albums, artists, genres, tracks)
│   ├── player/             # Now playing screen, queue, lyrics
│   ├── search/             # Global search with filters
│   └── settings/           # Server config, playback, download, appearance
└── sync/                   # WorkManager-based library sync engine
```

### Module Dependency Rules

```
feature/* → core/data, core/model, core/common, core/player
core/data → core/network, core/database, core/model, core/common
core/network → core/model (API DTOs mapped to domain models)
core/database → core/model (entities mapped to domain models)
core/player → core/model, core/data, core/common
sync → core/data, core/network, core/database, core/model
app → everything (wiring only)
```

**Hard rules:**
- `core/model` has ZERO Android dependencies (pure Kotlin)
- `core/network` NEVER imports Room. `core/database` NEVER imports jellyfin-sdk
- `feature/*` modules NEVER depend on each other
- `core/data` is the ONLY module that bridges network ↔ database

## Key Architecture Decisions

### 1. Offline-First (NON-NEGOTIABLE)

Room database is the **source of truth**. The Jellyfin server is a sync target.

```
UI → ViewModel → Repository → Room DB (always)
                            ↕ Jellyfin API (when online)
```

- Read path: Always read from Room. Background sync keeps it fresh.
- Write path: Write to Room immediately, sync to server when online.
- Favorites, ratings, play counts all work offline and sync later.
- This is what Symfonium does. Every OSS client that skips this breaks offline.

### 2. Media3 Foreground Service from Launch

When playback starts, `MellowMediaService` (extending `MediaLibraryService`) runs as a foreground service **immediately**. No background-to-foreground transition. This avoids the `ForegroundServiceStartNotAllowedException` that crashes every other Jellyfin client on Android 14+.

```kotlin
// MellowMediaService extends MediaLibraryService
// - Creates ExoPlayer with audio-only renderers
// - Exposes MediaLibrarySession for Android Auto content tree
// - Manages audio focus automatically via Media3
// - Handles gapless playback via playlist API
// - Reports playback progress to Jellyfin server
```

### 3. Android Auto Content Tree

MediaLibraryService exposes a browsable content tree:

```
[Root]
├── Recent              (recently played albums/tracks)
├── Library             (browse by album/artist/genre)
│   ├── Albums
│   ├── Artists
│   └── Genres
├── Playlists           (user playlists + smart playlists)
└── Favorites           (favorited tracks)
```

Max 4 root children for Android Auto. Voice search via `onGetSearchResult` queries the Room database directly for instant results.

### 4. Streaming Architecture

```
Direct Play (preferred) ← when device supports the codec
    GET /Audio/{id}/stream?static=true

Transcoding (fallback) ← server converts on the fly
    GET /Audio/{id}/stream.mp3?audioBitRate=128000

Universal Audio (auto-select)
    GET /Audio/{id}/universal?container=mp3,aac,flac&maxStreamingBitrate=192000
```

Media3 SimpleCache (LRU, configurable size) caches streamed audio. DownloadManager handles explicit offline downloads with transcoding support (download FLAC as Opus 128k to save space).

### 5. Authentication

Jellyfin uses custom token-based auth:
```
POST /Users/AuthenticateByName
Authorization: MediaBrowser Client="Mellow", Device="...", DeviceId="...", Version="..."
Body: { "Username": "...", "Pw": "..." }
→ AccessToken (session-scoped, persists across app restarts)
```

Token stored in EncryptedSharedPreferences. Multi-server support via Room (one row per server).

### 6. Image Strategy

Album art URLs: `GET /Items/{id}/Images/Primary?maxWidth=600&quality=90`

Coil handles memory + disk caching. `BaseItemDto.ImageBlurHashes` provides blurhash for progressive loading (show blurred placeholder while full image loads).

## Data Flow Example: Playing an Album

```
1. User taps album in Library screen
2. LibraryViewModel.loadAlbumTracks(albumId)
3. → AlbumRepository.getAlbumTracks(albumId)
4.   → Room: SELECT * FROM tracks WHERE albumId = ? ORDER BY trackNumber
5.   → (background) Jellyfin API: GET /Items?parentId={albumId}&includeItemTypes=Audio
6.   → Merge: update Room with fresh API data, emit Room flow
7. User taps "Play All"
8. PlayerViewModel.playTracks(tracks)
9. → MellowPlayer.setQueue(tracks.map { it.toMediaItem() })
10.  → ExoPlayer.setMediaItems(mediaItems)
11.  → ExoPlayer.prepare() + play()
12.  → Media3 builds stream URLs, starts foreground service
13.  → MediaSession updates → notification + Android Auto
14. Playback progress reported to Jellyfin every 10 seconds
```

## Jellyfin SDK Integration

Using `jellyfin-sdk-kotlin` v1.8.7 (official, generated from OpenAPI spec):

```kotlin
val jellyfin = Jellyfin {
    clientInfo = ClientInfo("Mellow", BuildConfig.VERSION_NAME)
    deviceInfo = DeviceInfo(deviceId, Build.MODEL)
}
val api = jellyfin.createApi(baseUrl = serverUrl)
api.update(accessToken = storedToken)

// All 47 API classes available: ItemsApi, AudioApi, PlaylistApi,
// ImageApi, UserDataApi, InstantMixApi, SearchApi, LyricApi, etc.
```

## WebSocket Integration

Jellyfin WebSocket (`ws://server/socket`) provides real-time events:
- `LibraryChanged` — triggers background sync
- `UserDataChanged` — syncs favorites/ratings from other clients
- `PlaystateCommand` — remote control from server dashboard

Used for: keeping Room DB in sync without polling.
