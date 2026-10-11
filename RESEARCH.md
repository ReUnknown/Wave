# Wave — Research Notes
*A simple, ad-free YouTube Music client for Google Pixel, built with Material 3 Expressive.*
Compiled 2026-10-10.

---

## 1. Goal (scope lock)

| In | Out |
|---|---|
| Google sign-in → your YT Music account | Podcasts / episodes |
| Home: profile avatar + search bar at top | Downloads, EQ, crossfade, Discord, Last.fm, lyrics translation, listen-together… |
| "Recently played" (songs + playlists) row first | Video mode |
| Personalized sections from the YTM home feed; each row opens with a big "play this section" thumbnail | Local files |
| Search | Multi-platform |
| Your playlists + shuffle/remix | |
| Background playback, lock screen + Pixel media controls | |
| No ads | |

---

## 2. What the other clients do (and where they fall short)

| App | Status | Notes |
|---|---|---|
| **InnerTune** (z-huang) | Original; largely dormant | Created the Kotlin `innertube` module almost every app below forked. |
| **OuterTune** | Fork of InnerTune | Material 3 (not Expressive), adds local files. |
| **Metrolist** | Fork lineage of InnerTune; **in maintenance mode** (bug fixes only) | The most feature-complete one: downloads, EQ, lyrics, listen-together, widgets, Android Auto. GPL-3.0. |
| **SimpMusic** | Active, Compose Multiplatform | Lots of extras (Spotify Canvas, SponsorBlock, AI suggestions). |
| **ViMusic → RiMusic / ViTune** | Forks; churn | Unusual UI, no real account sync in the original. |
| **Harmony Music** | Flutter | Not native Compose, so it can't use M3E components. |
| **Echo Music, Simple Tube, AQ, …** | Various forks | Mostly re-skins of the same InnerTune base. |

**What they have in common:** the same login flow and API layer. **Where they fall short:** they're overloaded with settings, they use plain M3 (or older), the home screens copy YTM's layout, and they sometimes break when YouTube changes something. That gives Wave room to be the *simple, polished, Pixel-native* option.

### How they actually work (Metrolist's code, verified)
1. **Login**: A WebView loads `https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com`. When the page lands on `music.youtube.com`, the app:
   - reads the cookie string from `CookieManager` and requires a `SAPISID` cookie (that cookie is what proves you're logged in)
   - runs JS to read `window.yt.config_` → `VISITOR_DATA`, `DATASYNC_ID` (trimmed at `||`), `SESSION_INDEX`
   - calls `accountsList` (if you have several channels, it shows a picker) and then `accountInfo` (name, email, avatar)
   - stores all of it in DataStore (**in plain text; Wave should encrypt it**)
2. **Auth on requests**: `Authorization: SAPISIDHASH <ts>_<sha1(ts + " " + SAPISID + " " + origin)>` + cookie + `X-Goog-AuthUser`.
3. **API = InnerTube** (YouTube's private JSON API), using the `WEB_REMIX` client for browse and search:
   - Home: `browse FEmusic_home` (+ continuations for more sections, + chip `params` for filters)
   - Moods & genres: `FEmusic_moods_and_genres`
   - Library: `FEmusic_library_*`, liked songs `VLLM`, playlists `VL<playlistId>`
   - Search: `search` + `music/get_search_suggestions`
   - Radio / "up next": `next` with `playlistId=RDAMVM<videoId>` (this is how "remix my playlist" / endless radio works)
   - History: `FEmusic_history`
4. **Streams**: a `player` request returns signature-ciphered URLs. These need deciphering (NewPipeExtractor's cipher, or a JS cipher server), and now also a **PO Token** (Proof-of-Origin) for some clients. Metrolist relies on NewPipeExtractor plus helper projects for this.
5. **No ads**: ads never get injected, because the app parses the raw API data and only plays the audio stream.

### Risks to accept up front
- **It's against YouTube's ToS.** YouTube has been actively going after ad-free third-party apps since 2024 (playback errors, "content not available on this app"). Fine for a personal sideloaded app. **Never put it on the Play Store.** The worst realistic outcome is playback breaking until the app is patched; account action is unlikely but possible.
- **It will break sometimes.** Stream deciphering and PO Tokens are a moving target. Mitigation: isolate the "get stream URL" code behind one interface and depend on NewPipeExtractor, which gets patched quickly.
- **Google may block WebView sign-in** ("This browser may not be secure"). Today it works for these clients; the fallback is a Custom Tab + manual cookie paste.
- **License**: copying code from InnerTune or Metrolist makes Wave GPL-3.0. That's fine for a personal project. The other option is to write a small InnerTube client ourselves (we only need ~8 endpoints).

---

## 3. Material 3 Expressive — rules that matter for Wave

M3E (announced May 2025, shipped in Android 16 / Pixel) builds on M3 rather than replacing it. Google's guidance: aim for the "excellent" or "transformative" level of expression, and **use expressive moments for key actions, not everywhere.**

### The tactics
| Pillar | Rule | Wave application |
|---|---|---|
| **Color** | Dynamic color (wallpaper-based), richer tonal palettes, high-contrast containers to show what matters most | Dynamic color always on; the Now Playing screen re-themes from album art |
| **Shape** | 35 `MaterialShapes` (Cookie, Clover, Sunny, Pill, Arch, Gem, …); **shape morphing** on state change; mixing round and square shapes creates tension and focus | Section "play" thumbnails clipped to expressive shapes; the play/pause button morphs between a circle and a squircle; avatar in a Cookie shape |
| **Size** | Larger hit targets for primary actions; XL FAB / large buttons | Huge play button on Now Playing; big lead thumbnail per row |
| **Typography** | Emphasized type styles (`*Emphasized`), variable-font axes (Google Sans Flex / Roboto Flex) | Section titles in `titleLargeEmphasized`; Now Playing title in `headlineMediumEmphasized` |
| **Containment** | Group related things inside containers; flexible containers that adapt | Mini-player as a floating container; button groups for controls |
| **Motion** | **Physics-based springs** via `MotionScheme.expressive()`. *Spatial* springs (position/size, slight overshoot) vs *effects* springs (color/alpha, no overshoot); fast/default/slow tokens | Mini-player → full player via a shared-element spring; press-to-squish buttons; carousel physics |

### M3E components to use (Compose `material3`)
- **LoadingIndicator** (morphing-shapes loader). Replaces the spinner.
- **LinearWavyProgressIndicator / wavy slider**. The seek bar is wavy while playing and flattens when paused, same as Pixel's own media controls.
- **ButtonGroup** + toggle buttons with shape morph. Prev / Play / Next, shuffle/repeat.
- **Carousel** (`HorizontalMultiBrowseCarousel` / `HorizontalUncontainedCarousel`). Home section rows.
- **SearchBar / ExpandedFullScreenSearchBar**. Top of home.
- **FloatingToolbar / FlexibleBottomAppBar**. Optional; nav may not even be needed.
- **ShortNavigationBar**. Only if we add a 2nd tab (e.g., Library).
- **SplitButton / FAB menu**. Probably skip (keep it simple).

### Compose library status (verified on developer.android.com, Oct 2026)
- `androidx.compose.material3:material3` **stable = 1.4.0**: expressive APIs were **removed** from stable at 1.4.0-beta01.
- **1.5.0-beta01 (Oct 7, 2026)** / 1.5.0-alpha29: this line has the expressive APIs.
  - Promoted to stable inside 1.5.0 alphas: MotionScheme (alpha15), WavyProgressIndicator + expressive theme/`expressiveLightColorScheme` (alpha18), ButtonGroup (alpha22), FlexibleBottomAppBar (alpha23).
  - **Still experimental**: `MaterialShapes` and `LoadingIndicator` (promotion reverted in alpha19).
  - New `@Material3ExpressiveApi` annotation (no OptIn needed).
- ➡️ **Use `material3:1.5.0-beta01`** and add `graphics-shapes` for `RoundedPolygon.toShape()` morphing.

---

## 4. Pixel-native touches (cheap wins)
- **Media3 `MediaSessionService` + ExoPlayer**: this gives you the lock screen / QS media card / Pixel wavy notification seek bar / Bluetooth / Wear / Android Auto basics for free.
- **Dynamic color** + **themed monochrome icon** (Wave's two lines work perfectly as an adaptive monochrome icon).
- **Edge-to-edge** (required with targetSdk 35+), **predictive back**, haptics on button presses.
- **Audio**: request the `251` Opus (~160kbps) stream, falling back to `140` AAC.

---

## 5. Recommended stack
| Layer | Choice |
|---|---|
| Language | **Kotlin** (2.x) |
| UI | **Jetpack Compose + Material3 1.5.0-beta01 (Expressive)**, `graphics-shapes`, Navigation Compose |
| Playback | **Media3** (ExoPlayer + MediaSessionService + session) |
| Network | Ktor or OkHttp + kotlinx.serialization (InnerTube JSON) |
| Stream decipher | **NewPipeExtractor** (only for the player/stream URL) |
| Images | Coil 3 |
| Storage | DataStore (+ encrypted cookie via Android Keystore) |
| DI | Hilt, or plain manual DI (the app is small) |
| Min / target SDK | minSdk 31 (Android 12, Material You), targetSdk 36 |

---

## 6. Wave's home screen (proposed from your brief)

```
┌───────────────────────────────────────┐
│ (🍪avatar)  [ 🔍 Search music…      ] │  ← profile + M3E SearchBar
├───────────────────────────────────────┤
│ Recently played                        │  ← FEmusic_history + library recents
│ [■][■][■][■][■] →                      │
├───────────────────────────────────────┤
│ Quick picks                            │  ← each FEmusic_home shelf
│ ╭─────╮ [■][■][■][■] →                 │     big expressive-shape "▶ play
│ │ ▶  │                                 │     all" tile leads the row
│ ╰─────╯                                │
├───────────────────────────────────────┤
│ Mixed for you / Listen again / …       │
│ ( … repeated, podcast shelves removed )│
├───────────────────────────────────────┤
│ ▂▃▅ Mini-player (floating container) ▶ │
└───────────────────────────────────────┘
```
- **Lead thumbnail** = plays the whole section. Song shelves get queued together; for playlist/mix shelves, we start the first one, or a radio seeded from the section.
- **No podcasts**: drop shelves or items whose type is podcast/episode or whose browseId starts with `MPSP` / page type `MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE`.
- **Remix**: "Shuffle" on a playlist = shuffle the list itself, plus an optional "Radio" (`RDAMVM…` / `RDAMPL…`) for endless mode.

## 7. Icon
Two parallel sine-wave strokes, rounded caps, about 8% stroke weight, horizontally centered in the 66dp adaptive safe zone. Use a dynamic-color background and a monochrome layer for themed icons. Wordmark: "Wave" in Google Sans Flex, rounded and heavier weight.

---

## Sources
- Metrolist repo + LoginScreen.kt / YouTube.kt — https://github.com/MetrolistGroup/Metrolist
- Compose Material3 release notes — https://developer.android.com/jetpack/androidx/releases/compose-material3
- M3 Expressive design language — https://developer.android.com/design/ui/wear/guides/get-started/design-language
- Material 3 in Compose — https://developer.android.com/jetpack/compose/designsystems/material3
- Androidify (M3E + MaterialShapes in Compose) — https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html
- yt-dlp PO Token Guide — https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
- Music Assistant YT Music notes (cookie-only auth) — https://music-assistant.io/music-providers/youtube-music/
- YouTube third-party app crackdown — https://alternativeto.net/news/2024/4/youtube-is-finally-cracking-down-on-third-party-apps-that-enable-ad-blocking
- App landscape — https://www.alternativeto.net/software/metrolist/ , https://alternativeto.net/software/vimusic
