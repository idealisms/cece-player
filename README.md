# Cece Player

A simple Android music player built for my 3-year-old daughter. Inspired by the [Yoto Mini](https://us.yotoplay.com/yoto-mini) player — big buttons, no distractions, hard to escape.

## Screenshots

![Now playing with pixel art track icon](Screenshot_20260222_135225_Cece%20Player.jpg)

![Screen pinning active — navigation is locked](Screenshot_20260222_135253_Cece%20Player.jpg)

## Features

- **Scans for music automatically** — finds all audio files in any folder named `Cece` on the device. Sub-folders are used as album names (e.g. `Cece/Frozen/` → album "Frozen")
- **Pixel art track icons** — if a `.png` with the same name as the audio file exists alongside it, it's shown in the center using nearest-neighbor scaling to keep the pixel art sharp
- **Three big buttons** — previous track, play/pause, next track. Tapping the track icon also toggles play/pause
- **Landscape only** — locked to landscape in both orientations
- **Hard to exit:**
  - Back button is disabled
  - Navigation bar is hidden (immersive fullscreen)
  - Screen pinning (`startLockTask()`) locks out the notification shade and system navigation — requires screen pinning to be enabled in Settings first
- **Screen stays on** while the app is in the foreground
- **Playback pauses** when the app leaves the foreground

## Setup

### Music

Put audio files on the device under a folder called `Cece` anywhere in storage (e.g. `Music/Cece/`). Organise into sub-folders by album:

```
Music/
  Cece/
    Frozen/
      01 Frozen Heart.mp3
      01 Frozen Heart.png   ← optional pixel art icon (16×16 recommended)
      05 Let It Go.mp3
      05 Let It Go.png
    Moana/
      01 Tulou Tagaloa.mp3
```

### Screen pinning

To lock out the notification shade and system navigation, enable screen pinning on the device first:

**Settings → Biometrics and security → Screen pinning → On**

The app activates pinning automatically on launch. To exit, hold **Back** and **Recents** simultaneously.

## Building

Requires Android SDK and JDK 17+. Set your SDK path in `local.properties`:

```
sdk.dir=/path/to/android-sdk
```

Then build and install:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Targets Android 10+ (API 29).
