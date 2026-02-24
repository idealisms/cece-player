# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Install

**Prerequisites:**
- Android SDK at `~/android-sdk`
- Gradle at `~/gradle/gradle-8.9/bin/gradle`
- ADB at `/mnt/c/platform-tools/adb.exe`

**Build:**
```bash
export ANDROID_HOME=~/android-sdk && export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java)))) && ~/gradle/gradle-8.9/bin/gradle assembleDebug --no-daemon
```

**Install to device:**
```bash
/mnt/c/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

**Run unit tests:**
```bash
export ANDROID_HOME=~/android-sdk && export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java)))) && ~/gradle/gradle-8.9/bin/gradle test --no-daemon
```

Pure-logic unit tests live in `app/src/test/java/com/cece/player/TrackUtilsTest.kt`. They cover `TrackUtils` (album name extraction, title extraction, track index wrap-around) and run on the JVM with no device needed.

## Architecture

This is a single-activity Android app (`MainActivity.kt`) — all logic lives there. There are no fragments, view models, services, or additional activities.

**Playback flow:**
1. On startup, permissions are requested (`READ_MEDIA_AUDIO` + `READ_MEDIA_IMAGES` on API 33+, `READ_EXTERNAL_STORAGE` on API ≤ 32)
2. `loadTracks()` queries `MediaStore.Audio.Media` filtering for files whose `DATA` path contains `/Cece/`
3. Album name = the first path segment inside `/Cece/`; tracks sort by `RELATIVE_PATH, DISPLAY_NAME`
4. `startPlayback()` creates a fresh `MediaPlayer` each time a track starts; on completion it calls `playNext()`
5. `updateAlbumArt()` looks for a `.png` file at the same path as the audio file; if found, it scales the bitmap to the largest integer multiple of 16 that fits the view (nearest-neighbor, no filtering) and shows it in the center slot

**Center button behavior:** the center slot (`centerContainer`) is a `FrameLayout` that layers `btnPlay` (circle), `albumArt` (ImageView), and `pauseOverlay` (semi-transparent `⏸` text). When a PNG icon exists for the current track, `btnPlay` is hidden and `albumArt` is shown; the overlay appears/disappears on pause/resume.

**Prev button behavior:** if `currentPosition > 3000ms`, seeks to 0; otherwise goes to previous track.

**Kiosk behavior:** `startLockTask()` is called in `onResume()` (screen pinning); back button is swallowed; system UI is hidden with immersive sticky mode and re-hidden 2s after any transient reveal.

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/cece/player/MainActivity.kt` | All app logic |
| `app/src/main/java/com/cece/player/TrackUtils.kt` | Pure functions: album/title parsing, index navigation |
| `app/src/main/java/com/cece/player/BatteryView.kt` | Custom view: battery level indicator (top-left) |
| `app/src/main/java/com/cece/player/HeadphonesView.kt` | Custom view: Bluetooth headphones indicator (top-right) |
| `app/src/main/res/layout/activity_main.xml` | Three-column layout: prev / center / next |
| `app/src/main/res/values/colors.xml` | Color palette (`#000000` bg, `#FF4F00` orange buttons) |
| `app/src/main/res/drawable/circle_btn.xml` | Prev/next button shape (orange circle) |
| `app/src/main/res/drawable/circle_btn_play.xml` | Play button shape (dark blue circle) |
| `app/src/main/AndroidManifest.xml` | `sensorLandscape`, `singleInstance`, permissions |
| `app/src/test/java/com/cece/player/TrackUtilsTest.kt` | JUnit tests for TrackUtils |

## Target Device

Samsung Galaxy S20 (ADB ID: `RFCNA0HDLGW`), Android 10+ (API 29). App is landscape-only, fullscreen, and runs pinned (kiosk mode). Screen pinning must be enabled in device settings before it takes effect.

## Icon Files

PNG track icons live alongside MP3s on the device, with the same filename but `.png` extension. During development, source icons are at `/home/tc/source/cece-player/icons/` (not committed).
