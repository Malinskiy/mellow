# Mellow — Feature List

## Design Status

Features are listed by priority. Design will be done after skeleton is finalized.

**Workflow**: Skeleton → Feature list finalized → Designer handoff → Implement design → Iterate

---

## P0 — Must Have for v1.0

### Server Connection & Auth
- [ ] Server discovery (manual URL entry + mDNS/SSDP auto-discovery)
- [ ] Username/password authentication via Jellyfin API
- [ ] Multi-server support (switch between servers)
- [ ] Token persistence across app restarts (EncryptedSharedPreferences)
- [ ] Automatic re-authentication on token expiry
- [ ] Server health check and connection status indicator

### Library Browsing
- [ ] Albums view (grid + list modes, sortable by name/date/artist/year)
- [ ] Artists view (list with album count, sortable)
- [ ] Tracks view (paginated, sortable by name/album/artist/date added)
- [ ] Genres view (list with item count)
- [ ] Album detail (tracks, album art, metadata, play/shuffle actions)
- [ ] Artist detail (albums, top tracks, similar artists)
- [ ] Folder browsing (raw filesystem navigation for non-standard libraries)
- [ ] Pull-to-refresh on all library views

### Playback
- [ ] Play, pause, skip next/previous, seek
- [ ] Queue management (view, reorder, remove, clear)
- [ ] Shuffle (queue-based, not destructive to original order)
- [ ] Repeat (off, one, all)
- [ ] Gapless playback (Media3 playlist API, encoder delay stripping)
- [ ] Direct play (when device supports codec)
- [ ] Server-side transcoding (configurable bitrate: 64/128/192/256/320 kbps)
- [ ] Now Playing screen with album art, track info, progress bar
- [ ] Media notification (play/pause/skip controls)
- [ ] Audio focus handling (duck/pause for calls, navigation, etc.)
- [ ] Playback resume after interruption (call, other app)
- [ ] Background playback (foreground service, survives app swipe-away)

### Android Auto (DAY ONE — NOT AN AFTERTHOUGHT)
- [ ] Media browsing via car display (albums, artists, playlists, favorites)
- [ ] Playback controls on car display (no phone interaction needed)
- [ ] Voice search ("play Dark Side of the Moon", "play music by Pink Floyd")
- [ ] Album art on car display
- [ ] Queue visibility on car display
- [ ] Content tree: Recent, Library (Albums/Artists/Genres), Playlists, Favorites

### Search
- [ ] Global search across albums, artists, tracks, playlists
- [ ] Instant results from local Room database
- [ ] Server-side search as fallback for un-synced content
- [ ] Search history (recent searches)

### Favorites & User Data
- [ ] Mark/unmark tracks, albums, artists as favorite
- [ ] Favorites view (all favorited items)
- [ ] Sync favorites bidirectionally with Jellyfin server
- [ ] Play count tracking (local + server sync)
- [ ] Playback progress reporting to Jellyfin server

### Playlists
- [ ] View server playlists
- [ ] Create new playlists
- [ ] Add/remove tracks from playlists
- [ ] Reorder playlist tracks
- [ ] Delete playlists
- [ ] Playlist artwork (first 4 album arts collage or custom)

---

## P1 — High Priority (v1.1)

### Offline Mode
- [ ] Download tracks/albums/playlists for offline playback
- [ ] Transcoded downloads (download FLAC → Opus 128k to save space)
- [ ] Download queue management (pause, resume, cancel, priority)
- [ ] Storage usage display and limits
- [ ] Auto-download favorites (configurable)
- [ ] Offline library browsing (full metadata available offline via Room DB)
- [ ] Offline favorites and ratings (sync when back online)
- [ ] Download over Wi-Fi only option
- [ ] Storage location selection (internal/SD card)

### Smart Playlists
- [ ] Filter by: rating, genre, year, play count, last played, date added
- [ ] Compound filters (AND/OR logic)
- [ ] Auto-update (re-evaluate filters on library change)
- [ ] Example presets: "Recently Added", "Most Played", "Never Played", "5★ Only"

### Instant Mix / Radio
- [ ] Generate radio from track (Jellyfin InstantMix API)
- [ ] Generate radio from artist
- [ ] Generate radio from album
- [ ] Generate radio from genre
- [ ] Continuous playback (auto-add tracks when queue ends)

### Audio Normalization
- [ ] ReplayGain support (track + album gain from Jellyfin metadata)
- [ ] Configurable pre-amp
- [ ] Peak-limiting to prevent clipping

### Lyrics
- [ ] Display synced lyrics (LRC format) with current line highlighting
- [ ] Display plain text lyrics
- [ ] Tap lyric line to seek
- [ ] Lyrics view in Now Playing screen

---

## P2 — Medium Priority (v1.2+)

### Listening Stats
- [ ] Local play history (track, timestamp, duration, skipped?)
- [ ] Most played tracks/albums/artists (this week/month/year/all time)
- [ ] Listening time stats (daily/weekly/monthly)
- [ ] Recently played history

### Personal Mixes
- [ ] Auto-generated playlists based on listening patterns
- [ ] "Discover" mix (least-played tracks from library)
- [ ] Genre-based mixes from listening history
- [ ] Time-of-day based suggestions

### Appearance
- [ ] Material You dynamic theming (colors from album art)
- [ ] Light/dark/system theme
- [ ] Grid size options for album views (2/3/4 columns)
- [ ] Now Playing screen themes (full art, minimal, etc.)

### Equalizer & DSP
- [ ] Built-in parametric EQ
- [ ] Preset EQ profiles (Bass Boost, Vocal, etc.)
- [ ] Per-output-device EQ profiles (speakers vs headphones)
- [ ] Skip silence (for audiobooks/podcasts)
- [ ] Playback speed control (0.5x–3.0x)

### Casting
- [ ] Chromecast support
- [ ] DLNA/UPnP rendering

### Widget
- [ ] Home screen widget (Glance framework)
- [ ] Now playing info + controls
- [ ] Multiple widget sizes

### Wear OS Companion
- [ ] Browse library on watch
- [ ] Playback controls on watch
- [ ] Offline sync to watch (transcoded)

---

## P3 — Nice to Have (Future)

### Multi-User
- [ ] Quick user switching on same server
- [ ] Per-user library and preferences

### SyncPlay
- [ ] Synchronized playback with other Jellyfin clients (listening party)
- [ ] Uses Jellyfin WebSocket SyncPlayCommand API

### Audiobook Support
- [ ] Chapter navigation
- [ ] Sleep timer
- [ ] Playback speed persistence per book
- [ ] Resume position per book

### Car Thing / External Displays
- [ ] Dedicated car mode UI (large buttons, high contrast)
- [ ] External display support

### Import/Export
- [ ] Export playlists (M3U, JSON)
- [ ] Import playlists from other players
- [ ] Migrate from Finamp (import downloaded files + favorites)

---

## Non-Functional Requirements

### Performance
- [ ] Library browse: <100ms for cached data, <2s for first load
- [ ] Search: <50ms for local results
- [ ] Playback start: <500ms from tap to audio (cached), <2s (network)
- [ ] Smooth scrolling at 60fps for 100K+ track libraries
- [ ] Memory: <150MB resident for normal use

### Battery
- [ ] Background playback: <5% battery/hour (audio offload enabled)
- [ ] No battery drain when playback is stopped
- [ ] Proper foreground service lifecycle (no zombie processes)

### Reliability
- [ ] Crash-free rate: >99.5%
- [ ] Graceful handling of server unreachable (offline mode fallback)
- [ ] Playback resume after process death (Media3 resumption)
- [ ] No data loss on unexpected termination

### Accessibility
- [ ] TalkBack support for all screens
- [ ] Content descriptions for all interactive elements
- [ ] Minimum touch target size 48dp
- [ ] High contrast support

### Security
- [ ] No credentials in logs
- [ ] EncryptedSharedPreferences for tokens
- [ ] Certificate pinning optional (for self-hosted servers)
- [ ] No analytics or telemetry without opt-in
