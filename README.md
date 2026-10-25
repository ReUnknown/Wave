<div align="center">

# 〰️ Wave

**Your YouTube Music. Nothing in the way.**

A simple, ad-free YouTube Music client for Android, Windows, macOS and Linux:
phones, tablets, foldables, Googlebook and computers, built with Material 3 Expressive.

[![Latest release](https://img.shields.io/github/v/release/ReUnknown/Wave?include_prereleases&label=latest&color=a6d0e4)](https://github.com/ReUnknown/Wave/releases)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3ddc84?logo=android&logoColor=white)
![Windows · macOS · Linux](https://img.shields.io/badge/desktop-Windows%20·%20macOS%20·%20Linux-5b6ee1)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff?logo=kotlin&logoColor=white)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[**Download the latest APK →**](https://github.com/ReUnknown/Wave/releases/latest)

</div>

---

## Why Wave

YouTube Music has your library, your mixes and the recommendations that actually know you. Wave
keeps all of that and drops everything else: no ads, no video clutter, no podcasts, no upsells.
Sign in with Google and your home, playlists, likes and history are just there.

It's built to feel at home on whatever screen you're holding, with motion that has a purpose: the
player grows out of the mini player, play morphs into pause, and the whole app takes on the colors
of the song that's playing.

## One app, every screen

| | Layout | Highlights |
|---|---|---|
| 📱 **Phones** | Full-art player with a floating mini player | Swipe the mini player sideways to dismiss it, swipe the player down to close it, swipe up on the tray for volume and speed |
| 📲 **Tablets & unfolded foldables** | Grids instead of sideways rows, a player bar across the bottom | Tap to open the player full screen, or drag the bar to the left or right half to play beside the app |
| 💻 **Googlebook & ChromeOS** | Sidebar with your library, a player bar with the song's art blurred behind it | Dropdown menus where you click, right-click menus, keyboard shortcuts, mouse-wheel scrolling everywhere, optional side-by-side player |
| 🖥️ **Windows, macOS & Linux** | The same app and look, native on your computer | Its own audio engine, sign-in through your system's web engine, adjustable interface size, macOS-native window |
| 🔄 **Landscape & small windows** | Adapts to the space it actually has | A narrow window falls back to the phone layout, so nothing ever gets squeezed |

## Features

**Listening**
- Ad-free playback in the background, with lock screen, notification and Bluetooth controls
- Like and shuffle right from the lock screen and media controls
- A home screen widget that matches Android's own media player
- Full songs, every time: a resilient streaming engine with a local cache
- Autoplay: when a playlist ends, Wave keeps going with songs like it
- Crossfade, even volume (loud masters turned down) and skip silence
- Wave's own volume slider and playback speed (0.5× to 2×), separate from the device volume

**Your music**
- Your YouTube Music home, playlists, liked songs, albums, artists and history
- Plays count toward your YouTube history and recommendations
- Shuffle that keeps the current song first and shows the real order in Up next
- Up next as a coverflow: swipe or click through it, drag to reorder, remove songs
- Synced lyrics, with a special moment for the hook and the drop

**Feel**
- Material 3 Expressive throughout: shape morphs, springy presses, container transforms
- Match song colors: the whole app, menus included, takes on the colors of the art
- Move with the music: shapes and waves pulse with the beat (pauses when Wave isn't in front)
- Predictive back on every screen

**Efficient**
- Low-power playback: in the background, the phone's audio chip decodes the music so the
  processor can sleep, switching over between songs so you never hear it
- Images loaded at the size they're shown, blurs drawn at low resolution, screens that are
  covered stop drawing
- A shrunk, optimized release build of about 10 MB

## Install

Grab the file for your device from the [releases](https://github.com/ReUnknown/Wave/releases):

| Device | File | How |
|---|---|---|
| Android | `Wave-x.y.z.apk` | Open it and allow installing from your browser or file manager |
| macOS (Apple Silicon) | `…-macos-arm64.dmg` | Drag Wave into Applications; the first time, right-click it → Open |
| Windows 10/11 | `…-windows-x64.exe` | Run the installer |
| Linux | `…-linux-x64.flatpak` | `flatpak install --user Wave-…flatpak` |

Then sign in with Google. Google handles your password; Wave never sees it.

Wave checks GitHub for new versions once a day and offers them with a single tap
(**Settings → About** to check by hand or turn this off).

**Requires** Android 12 or newer on a 64-bit device: any recent phone, tablet, foldable,
Googlebook or Chromebook with Android apps.

## Keyboard shortcuts

On a computer or a tablet with a keyboard:

| Keys | Does |
|---|---|
| `Space` | Play / pause |
| `Ctrl` + `→` / `←` | Next / previous song |
| `Ctrl` + `L` | Like |
| `Ctrl` + `F` or `/` | Search |
| `Esc` | Back |

## Build it yourself

```bash
git clone https://github.com/ReUnknown/Wave.git
cd Wave
./gradlew assembleDebug
```

You'll need JDK 21 (the one bundled with Android Studio works). The APK lands in
`app/build/outputs/apk/debug/`. Release builds are signed with a key kept outside the repo.

**Built with** Kotlin, Jetpack Compose, Material 3 Expressive, Media3 (ExoPlayer and
MediaSession), Coil and Ktor.

## Credits & license

The YouTube Music client is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist)'s
InnerTube library and InnerTubeX. Thank you to everyone who works on them.

Wave is free software under the [GNU GPL v3](LICENSE). It isn't affiliated with or endorsed by
YouTube or Google. You need your own YouTube Music account to use it.
