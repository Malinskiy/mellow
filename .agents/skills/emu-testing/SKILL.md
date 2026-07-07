---
name: emu-testing
description: "Run Mellow on an Android emulator using EMU MCP tools. Use when user asks to 'test on emulator', 'run the app', 'check on device', 'test Android Auto', 'verify playback', or wants to see the app running. Trigger on 'test', 'run', 'emulator', 'device', 'Android Auto test', 'smoke test'."
---

# EMU MCP Testing — Mellow

Test Mellow on Android emulators using EMU MCP tools for automated interaction and verification.

## Prerequisites

1. Android SDK installed with emulator support
2. EMU MCP server running (configured in opencode)
3. Mellow APK built: `./gradlew assembleDebug`

## Setup

### 1. Ensure an AVD exists

```
emu list_avds
```

If none exist, create one:
```
emu check_licenses package="system-images;android-36;google_apis;x86_64"
emu accept_licenses  (if needed)
emu install_system_image package="system-images;android-36;google_apis;x86_64"
emu create_avd name="mellow-test" package="system-images;android-36;google_apis;x86_64"
```

### 2. Start emulator

```
emu start_emulator avd_name="mellow-test"
```

Wait for boot (the tool polls automatically, ~60-120s).

### 3. Install Mellow

Build and install:
```bash
./gradlew assembleDebug
```

Then:
```
emu install_apk paths=["app/build/outputs/apk/debug/app-debug.apk"]
```

### 4. Launch Mellow

```
emu launch_app package="dev.mellow.app"
```

## Testing Workflows

### Smoke Test (Basic App Launch)

1. `emu launch_app package="dev.mellow.app"`
2. `emu wait_for_element query="Mellow"` — verify app launched
3. `emu screenshot` — capture initial state
4. `emu ui_dump` — verify UI tree is accessible

### Server Connection Test

1. Launch Mellow
2. Navigate to server setup (when implemented)
3. `emu input_text text="http://jellyfin-server:8096"` — enter server URL
4. Verify connection indicator

### Playback Test

1. Connect to a Jellyfin server
2. Navigate to an album
3. Tap play
4. Verify:
   - `emu ui_dump` — check for now-playing elements
   - `emu logcat package="dev.mellow.app" min_level="I"` — check for playback logs
   - `emu screenshot` — capture playback state

### Android Auto Test

Android Auto testing requires the DHU (Desktop Head Unit) or a car. On emulator:

1. Install Android Auto app on emulator
2. `emu launch_app package="com.google.android.projection.gearhead"`
3. Verify Mellow appears in the media app list
4. Test browsing and playback via auto UI

### Background Playback Test

1. Start playback
2. `emu press_key keycode="KEYCODE_HOME"` — go to home screen
3. Wait 30 seconds
4. `emu logcat package="dev.mellow.app" query="MellowMediaService"` — verify service still running
5. `emu screenshot` — verify media notification visible

### Battery Drain Test

1. `emu set_battery level=100 status="discharging" charger="none"`
2. Start playback, minimize app
3. Wait 5 minutes
4. `emu device_info` — check battery level
5. Stop playback
6. Wait 2 minutes
7. `emu logcat package="dev.mellow.app" query="MellowMediaService"` — verify service stopped

### Network Resilience Test

1. Start playback (streaming)
2. `emu set_network profile="Slow 3G"` — throttle network
3. Verify playback continues (cache should handle it)
4. `emu set_network profile="No throttling"` — restore
5. Verify playback resumes normally

### Crash Detection

After any test:
```
emu logcat package="dev.mellow.app" min_level="E"
```

Check for:
- `FATAL EXCEPTION` — app crash
- `ANR` — app not responding
- `BackgroundServiceStartNotAllowedException` — the #1 Jellyfin client killer

### Bug Report Collection

If a bug is found:
```
emu collect_bug_report title="[description]" notes="Steps to reproduce..."
```

## Findings Format

Report every finding:

```
FINDING-N: [severity: bug|cosmetic|unclear]
Step: [what was being tested]
Expected: [what should happen]
Actual: [what happened]
Evidence: [screenshot/logcat/ui_dump reference]
```

## Cleanup

```
emu stop_app package="dev.mellow.app"
```

Do NOT stop the emulator unless explicitly asked — reuse it for subsequent tests.
