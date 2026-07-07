---
name: jellyfin-api
description: "Reference for Jellyfin REST API endpoints used by Mellow. Use when implementing API calls, mapping DTOs, building repositories, or debugging server communication. Trigger on 'jellyfin api', 'endpoint', 'BaseItemDto', 'API call', 'server request', 'streaming URL', 'auth flow'."
---

# Jellyfin API Reference for Mellow

## SDK

Mellow uses `jellyfin-sdk-kotlin` v1.8.7 (official, generated from OpenAPI spec).

Access via `JellyfinClientWrapper` in `core/network`. NEVER create Jellyfin instances directly.

```kotlin
val api = jellyfinClient.api
val items by api.itemsApi.getItems(userId = userId, ...)
```

## Authentication

```
POST /Users/AuthenticateByName
Header: Authorization: MediaBrowser Client="Mellow", Device="...", DeviceId="...", Version="..."
Body: { "Username": "...", "Pw": "..." }
Response: { "User": {...}, "AccessToken": "...", "ServerId": "..." }
```

Subsequent requests include token in Authorization header. SDK handles this automatically after `api.update(accessToken = token)`.

## Core Music Endpoints

### Library Browsing (ItemsApi)

```
GET /Items?userId={uid}&parentId={libraryId}&includeItemTypes=MusicAlbum&recursive=true&sortBy=SortName&sortOrder=Ascending&fields=Genres,DateCreated&enableUserData=true&limit=50&startIndex=0
```

`includeItemTypes`: `MusicAlbum`, `Audio`, `MusicArtist`, `MusicGenre`, `Playlist`
`sortBy`: `SortName`, `DateCreated`, `PremiereDate`, `PlayCount`, `Random`
`fields`: `Genres`, `MediaStreams`, `Overview`, `ParentId`, `DateCreated`

### Audio Streaming (AudioApi)

Direct play: `GET /Audio/{id}/stream?static=true`
Transcode: `GET /Audio/{id}/stream.mp3?audioBitRate=128000&audioCodec=mp3`
Universal: `GET /Audio/{id}/universal?container=mp3,aac,flac&maxStreamingBitrate=192000`

Ticks: `milliseconds * 10000`

### Favorites (UserDataApi)

```
POST /Users/{userId}/FavoriteItems/{itemId}    → mark favorite
DELETE /Users/{userId}/FavoriteItems/{itemId}   → unfavorite
```

### Playlists (PlaylistApi)

```
GET /Playlists/{id}/Items                      → playlist tracks
POST /Playlists                                → create (body: { Name, Ids, UserId, MediaType: "Audio" })
POST /Playlists/{id}/Items                     → add tracks
DELETE /Playlists/{id}/Items?entryIds=id1,id2  → remove tracks
```

### Instant Mix (InstantMixApi)

```
GET /Items/{itemId}/InstantMix?userId={uid}&limit=50
GET /Artists/{id}/InstantMix?userId={uid}&limit=50
```

### Images (ImageApi)

```
GET /Items/{id}/Images/Primary?maxWidth=600&quality=90
```

`ImageBlurHashes` in `BaseItemDto` provides blurhash for progressive loading.

### Playback Reporting (SessionApi)

```
POST /Sessions/Playing          → report now playing
POST /Sessions/Playing/Progress → periodic progress (every 10s)
POST /Sessions/Playing/Stopped  → report stopped
```

### Search

```
GET /Items?userId={uid}&searchTerm=query&includeItemTypes=Audio,MusicAlbum,MusicArtist&recursive=true
```

## DTO Mapping

Always map `BaseItemDto` → domain models in `core/data`:

```kotlin
fun BaseItemDto.toAlbum() = Album(
    id = id.toString(),
    name = name.orEmpty(),
    artistId = albumArtists?.firstOrNull()?.id?.toString(),
    artistName = albumArtist,
    year = productionYear,
    trackCount = childCount ?: 0,
    genres = genres.orEmpty(),
    imageId = imageTags?.get(ImageType.PRIMARY)?.let { id.toString() },
    isFavorite = userData?.isFavorite ?: false,
)
```

## WebSocket

`ws://server:8096/socket` — real-time events:
- `LibraryChanged` → trigger sync
- `UserDataChanged` → sync favorites/ratings
- `PlaystateCommand` → remote control
