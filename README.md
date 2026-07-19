# Mellow

A fast, offline-first [Jellyfin](https://jellyfin.org/) music player for Android with first-class Android Auto support.

📖 [Read the story behind Mellow](https://blog.marathonlabs.io/blog/dogfooding-emu-building-mellow/)

<p align="center">
  <img src="docs/screenshot-home.png" width="240" alt="Home" />
  <img src="docs/screenshot-album.png" width="240" alt="Album detail" />
  <img src="docs/screenshot-login.png" width="240" alt="Login" />
</p>

<p align="center">
  <img src="docs/screenshot-tablet.png" alt="Tablet layout" />
</p>

## Why

Every open-source Jellyfin music client I've tried has the same gaps: background playback breaks on modern Android, offline is an afterthought, and Android Auto is perpetually "coming soon." Mellow fixes all three.

## Features

- **Offline-first** — all metadata synced to a local Room database. Music downloads with configurable WiFi-only and storage caps
- **Android Auto** — full browse tree, playback
- **Background playback** — MediaLibraryService with proper foreground service lifecycle on Android 12+
- **Lyrics** — synced LRC with tap-to-seek
- **Responsive** — single-column on phones, nav rail + multi-column on tablets, landscape layouts
- **Battery-aware** — GPU shader album backdrops pause when battery is low

## Built with Emu

Mellow was developed and tested using [Emu](https://emu.marathonlabs.io/?utm_source=github&utm_medium=mellow&utm_campaign=emu-dogfooding), an Android emulator companion tool.

<p align="center">
  <a href="https://blog.marathonlabs.io/blog/dogfooding-emu-building-mellow/">
    <img src="docs/emu-poster.png" width="720" alt="Mellow running in Emu — click for demo video" />
  </a>
</p>

▶️ [Watch the demo](https://blog.marathonlabs.io/blog/dogfooding-emu-building-mellow/) · [Try Emu](https://emu.marathonlabs.io/?utm_source=github&utm_medium=mellow&utm_campaign=emu-dogfooding)

Emu provides device mirroring, MCP tools for automation, Android Auto projection, battery/network mocking, and quality video recording — all of which were used daily while building this app.

<p align="center">
  <a href="https://blog.marathonlabs.io/blog/dogfooding-emu-building-mellow/">
    <img src="docs/aa-poster.png" width="720" alt="Mellow Android Auto in Emu — click for demo video" />
  </a>
</p>

The Android Auto implementation was tested entirely through Emu's built-in AA projection (no DHU or real car needed). Offline mode was verified by toggling network profiles. Battery-aware animations were tested by setting battery level to any value on demand. [See the full demo videos in the blog post.](https://blog.marathonlabs.io/blog/dogfooding-emu-building-mellow/)

## Tech stack

```
Kotlin · Jetpack Compose · Material 3 · Media3 (ExoPlayer)
Hilt · Room · Coil · WorkManager · jellyfin-sdk-kotlin
```

## Install

Download the latest APK from [GitHub Releases](https://github.com/Malinskiy/mellow/releases/latest):

1. Download `app-release.apk` from the latest release
2. Transfer the APK to your Android device (or download directly on device)
3. Open the APK and install — you may need to allow installation from unknown sources in your device settings

## Build from source

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Android Auto

Since Mellow is installed outside the Play Store, you need to enable unknown sources in Android Auto:

1. Open the **Android Auto** app on your phone
2. Tap **Settings** → scroll to **Version** → tap it **10 times** to enable developer mode
3. Tap the **⋮** overflow menu (top-right) → **Developer settings**
4. Enable **Unknown sources**

Without this, Android Auto will not show sideloaded apps in the car display.

## Screenshot tests

168 screenshot test cases across 4 device configurations (Pixel 10 portrait/landscape, Pixel Tablet portrait/landscape). Run via Robolectric with native graphics rendering:

```bash
./gradlew :app:testDebugUnitTest --tests "dev.mellow.app.screenshot.*"
```

Baselines live in `.marathon-snapshots/` and are compared via (internal as of this moment) version of [Marathon Cloud](https://marathonlabs.io).

## License

Apache License 2.0. See [LICENSE](LICENSE).
