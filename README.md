<p align="center">
  <img src="docs/branding/hero.png" alt="Better Music Overlay — Your soundtrack. Your world." width="100%">
</p>

# Better Music Overlay

[![Build](https://github.com/LootingVI/BetterMusicOverlay/actions/workflows/build.yml/badge.svg)](https://github.com/LootingVI/BetterMusicOverlay/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/LootingVI/BetterMusicOverlay?color=ae7bdc)](https://github.com/LootingVI/BetterMusicOverlay/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-c9aff5.svg)](LICENSE)

**Your soundtrack. Your world.** A customizable, client-side Fabric music overlay with smooth typography, native media controls, album-inspired colors, and optional synchronized lyrics. All interface text is English.

**[Download the latest release](https://github.com/LootingVI/BetterMusicOverlay/releases/latest)** · **[Installation](#install)** · **[Lyrics setup](#lyrics)** · **[Report a bug](https://github.com/LootingVI/BetterMusicOverlay/issues/new/choose)**

Works with **Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, and 26.3**. Native music integration currently requires **Windows 10/11**. Spotify, Apple Music, and browser players work when they expose a Windows media session; available controls depend on the player.

- **Make it yours:** four layouts, five themes, custom colors, adjustable dimensions, saved profiles, Vinyl and Lyric Lounge scenes.
- **Stay in the game:** artwork, player selection, playback controls, optional automatic collapse, and Minecraft music ducking.
- **Follow the words:** local LRC / Enhanced LRC, optional LRCLIB lookup, a full lyrics reader, and saved timing adjustments.
- **Choose what to share:** online lyrics are off by default; streamer controls can hide track details, lyrics, or selected players.

## See it in action

The cards below are rendered by the mod with original example track text. The reader image is captured in Minecraft; the cover above is promotional artwork.

<p>
  <img src="docs/aurora.png" alt="Aurora layout with album colors and playback controls" width="49%">
  <img src="docs/vinyl.png" alt="Vinyl scene with a circular record" width="49%">
</p>

![Lyric Lounge with three lines and a highlighted current line](docs/lyric-lounge.png)

<details>
<summary>More screenshots: customization, lyrics reader, and privacy controls</summary>

![Music Studio customization](docs/studio-in-minecraft.png)
![Full lyrics reader in Minecraft 1.21.11](docs/lyrics-reader-in-minecraft.png)
![Plain lyrics reader with manual scrolling](docs/plain-lyrics-in-minecraft.png)
![Per-recording lyrics corrections](docs/corrections-in-minecraft.png)

</details>

## New in 1.3 — layout fixes and a proper lyrics reader

**Fix for an overlay stuck in the simple layout:** choosing a layout in **O → Style**, resetting the appearance, or choosing an Identity scene now turns off Smart collapse. Your selected layout stays selected after leaving the editor and restarting Minecraft. The studio displays the selected layout and whether collapse is enabled. If you want automatic collapsing again, explicitly turn it on in **More → Smart HUD**; its countdown now restarts when you enable it or change the design.

The rendering cache now invalidates on the complete visual style and resolution, so toggling artwork, changing dimensions, and changing the GUI scale cannot stretch an old frame into a new design. Settings snapshots no longer serialize JSON during every HUD frame. Privacy-aware card dimensions also match the editor and its control hitboxes. Custom controls now use Minecraft's mouse-button constants, fixing dragging, resizing, seeking, and overlay transport clicks on 26.3 after its move to SDL button numbering.

**O → Lyrics → Open lyrics reader**, or **M → Open lyrics reader**, opens the complete transcript. Timed lyrics follow the current line; scrolling temporarily suspends following, and Resume following returns to playback. Click a timed line to seek, when supported by the selected player. The Earlier / Later controls immediately apply and save ±0.25-second song adjustments without downloading the same lyrics again. Positive offsets display lyrics earlier; negative offsets display them later. The reader can load lyrics while HUD lyrics remain off.

Plain lyrics from a matching LRCLIB record or a local `.txt` file are available for manual reading, labeled **PLAIN TEXT**. They never receive artificial timestamps, automatic line advancement, or seeking. Local text uses the same Artist - Title filename as LRC, with `.txt` instead of `.lrc`; LRC takes priority. The reader uses the same explicit online permission and streamer privacy controls as the HUD.

![Lyrics reader running in Minecraft 1.21.11 with original example lyrics](docs/lyrics-reader-in-minecraft.png)

Other fixes: playback time no longer freezes when the app omits total duration; automatic player selection stays with the active playing session; playback commands target the displayed player; late artwork is retried; cancelled searches cannot republish stale candidates; switching off online lookup cancels searches even when HUD lyrics are off. LRC parsing now preserves timestamp-like text inside lyrics, groups simultaneous translation lines, and recognizes supplied Enhanced LRC line-end timestamps. Intros show a countdown, and supplied end markers clear highlighting during instrumental gaps.

### What informed the lyrics improvements

Reviewed the maintainers' documentation on 2026-10-11. These are design references; no implementation code was copied:

- [PhantomLyrics](https://github.com/Anngiie/PhantomLyrics): quick per-song timing adjustments, lyric-line seeking, and stable selection when several sources are active informed the reader controls and player routing.
- [Hum](https://github.com/basezero-projects/Hum): separate states for synchronized/plain lyrics, instrumentals, errors, and missing results informed clearer result handling. Its documented provider integrations are not included here.
- [MusicShow / LyricSync](https://github.com/ShuangJinl/MusicShow): demonstrates another Fabric 1.21.11 approach using timed LRC and a local service, with lyrics displayed in the world. Better Music keeps the compact HUD and adds a reader screen.

LRCLIB remains the online provider. This release adds neither third-party proxy services nor unofficial provider endpoints.

## New in 1.2 — an overlay that fits how you play

Open **O → More...** for the new pages:

- **Smart HUD:** optionally collapse to Minimal after 2–30 seconds, expand again on a track change, and fade the whole card briefly when your character takes damage. Opening the editor or quick controls shows your full design. Damage fade detects damage, not nearby enemies.
- **Artwork transitions:** blend between decoded covers over 650 ms. Motion and Track fade switches control this effect.
- **Players:** choose a detected Windows media session or automatic selection. Refresh / next players cycles through longer lists. Unsupported media apps cannot be discovered by this backend.
- **Profiles:** save Building, Exploration, Streaming, and two custom appearance slots. Export/import theme JSON through the clipboard or keep the files in `config/better-music-profiles/`. Loading a theme preserves online consent, player selection, and privacy settings. The Streaming slot is an appearance preset slot; enable Streamer mode separately.
- **Privacy:** hide titles, artist, artwork and provider identity; hide all lyrics; or hide lyrics for tracks you manually mark explicit. Comma-separated player-ID fragments hide the entire card for matching players. There is **no automatic explicit-content classifier**: Hide all is the default lyrics privacy choice. Privacy applies to this mod, not to another app or an OS notification.
- **Correction:** save a per-recording offset up to ±30 seconds, mark a track explicit, or search and explicitly choose an alternative LRCLIB recording. The global offset adds to the saved song offset; positive values advance lyrics. Local LRC still takes priority. Reset song restores automatic matching. Only explicitly saved corrections persist, in `config/better-music-lyrics/corrections/`; no listening history is recorded.
- **Editor:** drag the bottom-right corner to resize; snap to screen edges or centers; use Undo for up to 30 move/resize edits in the current editor session.
- **Music ducking:** optionally lower only Minecraft's MUSIC category while the selected external player is playing, then restore its multiplier smoothly. Saved Minecraft volume settings remain untouched. Other mods manipulating the same category multiplier may conflict.
- **Word highlighting:** local Enhanced LRC timestamps highlight supplied words as they start. Standard LRC remains line synchronized; no word timing is guessed. Disable Word highlighting to use line display everywhere.

Example **original** Enhanced LRC content, stored with the same `.lrc` filename as ordinary local lyrics:

```lrc
[00:10.00]<00:10.00>Your <00:10.50>own <00:11.00>frequency
[00:13.00]<00:13.00>Stay <00:13.50>in <00:13.80>your <00:14.10>world
```

The parser follows the inline word timestamp form documented by [QuickLRC's Enhanced LRC format guide](https://www.quicklrc.com/subtitle-formats/enhanced-lrc). Reversed timestamps and ambiguous repeated-line word stamps fall back to line display. Online lookup currently consumes LRCLIB's synchronized LRC field; it does not consume its separate YAML lyricsfile format.

## New in 1.1 — give your soundtrack an identity

Open **O → Identity** for four scenes:

- **Reference:** the original compact purple card.
- **Aurora:** larger artwork, clearer track hierarchy, source badge where space allows, dedicated transport controls, and an accent-colored timeline.
- **Vinyl:** a circular album record with subtle grooves and slow decorative rotation during playback. Disable Motion to stop animation.
- **Lyric Lounge:** Aurora with three lyric lines, a larger current line, and a colored focus marker.

**Album colors** extracts an accent from the cover, ignoring neutral/transparent pixels. **Ambient glow**, **Track fade**, and **Large lyrics** are independently switchable. Track changes and lyric changes use short fades; long lyric lines restart their scrolling when they become current. The original reference layout remains the default. Switching scenes preserves screen placement, preferred player, and online-lyrics permission.

Click the timeline in the studio preview or **M** controls to seek, if the current app supports seeking. Standard LRC remains line synchronized; Enhanced LRC supports supplied word timestamps. There is no simulated audio analysis.

## Install

1. Install **Fabric Loader 0.19.5 or newer** for your Minecraft version.
2. Install the matching **Fabric API** from https://modrinth.com/mod/fabric-api.
3. Download **one** matching `better-music-overlay-<minecraft>-1.3.0.jar` from [GitHub Releases](https://github.com/LootingVI/BetterMusicOverlay/releases/latest) and copy it into your instance's `mods` folder. Remove the previous Better Music JAR when upgrading. Local builds are packaged under `dist/1.3.0/`.
4. Start music in a desktop app or browser and launch Minecraft.

Separate builds are provided for **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, and 26.3**. Future 26.x releases are not automatically compatible. Minecraft 1.21.11 needs Java 21 or newer; the 26.x builds need Java 25 or newer.

**Media integration currently supports Windows 10/11.** It uses Windows Global System Media Transport Controls, without account credentials. Apple Music, Spotify, browser players, and other applications work when they publish a Windows media session. Artwork, timing, and available playback commands depend on the application. There are no provider-specific streaming APIs and the mod does not stream or download music. macOS and Linux media backends are not included.

## Controls

- **O**: open Music Studio. `/music` also opens it in-game.
- **M**: release the cursor into quick music controls. Click previous, play/pause, or next; press Escape to return.
- The studio preview's playback icons are clickable too.
- Set optional play/pause, next, previous, and overlay-toggle shortcuts in Minecraft's Controls menu.
- **Layout → Move overlay with mouse**: drag the card anywhere, then save or press Escape.

## Customize

Music Studio provides Studio, Aurora, Compact, and Minimal layouts; Midnight, Matcha, Rose, Paper, and Ocean themes; custom hex colors; opacity; corner radius; width; scale; relative screen position; artwork; progress; scrolling titles; and automatic hiding when no track is available.

The card uses bundled **Inter** typography and antialiased, resolution-aware rendering, rather than Minecraft's bitmap font. Drawing is prepared off the render thread, capped at 30 updates/second with motion on or 10 with motion off. Album artwork is decoded off-thread with size limits. The animated title is decorative and is not an audio spectrum analyzer.

Settings are saved in `config/better-music-overlay.json`. Invalid numeric settings are clamped to safe values. The optional preferred-app field matches part of the Windows app ID, for example `spotify`, `apple`, or `chrome`. Leave it empty for automatic selection. If the selected app is absent, the overlay remains idle instead of controlling another app.

## Lyrics

Open **O → Lyrics** and enable **Show lyrics**. Choose one to three lines and adjust the timing by up to ten seconds; a positive adjustment advances the displayed line.

- **Local:** click **Open local LRC folder**. Use **Copy LRC filename** to get the current track's expected filename, put a UTF-8 `.lrc` file there, then open the lyrics reader and click **Reload lyrics**. Files belong in `config/better-music-lyrics/`. Local files take priority.
- **Online:** enable **Online lookup** to retrieve synchronized lyrics from [LRCLIB](https://lrclib.net/docs). This sends the current title, artist, available album, and duration to LRCLIB. It is off by default. Results are cached only in memory for the current game session.
- Missing, instrumental, offline, and unsynchronized results show an honest status; the mod never invents lyric timing. Windows media sessions themselves do not provide the lyrics.

An example local file:

```lrc
[ar:Example Artist]
[ti:Example Track]
[00:00.00]An original opening line
[00:10.50]An original second line
[00:20.00]An original third line
```

The parser supports repeated timestamps, fractional seconds, blank instrumental sections, and `[offset:milliseconds]` metadata. Track matching depends on the metadata your player supplies; use local lyrics when a provider adds extra text to the artist or title.

### Lyrics sources and matching

Primary-source documentation checked on 2026-10-09:

- [LRCLIB official API](https://lrclib.net/docs): selected for this release. Provides synchronized LRC, an exact lookup, and search without a required API key. Its documented duration tolerance is two seconds. Community contributions mean coverage and correctness vary; API accessibility is not a guarantee of publisher licensing for every record.
- [Musixmatch official API collection](https://www.postman.com/musixmatch-dev/musixmatch-apis/collection/pqm8o6w/lyrics-api): requires an API key. Consider a separate provider integration once access and applicable display terms are arranged. No private application tokens or unofficial endpoints are used here.
- [LyricFind Lyric Display](https://www.lyricfind.com/products/lyric-display): offers line- and word-synchronized display through its partner offering. Not integrated without an access arrangement.
- [lyrics.ovh project documentation](https://github.com/NTag/lyrics.ovh): documents plain-text output and aggregation from lyrics websites. It does not document synchronized timestamps, so it is not used as an automatic timing fallback.

Local LRC takes priority. Online lookup checks title, artist, album when known, and duration within two seconds. After an unsuccessful exact match, search is accepted only with a known duration and the same checks. Live/remix/acoustic qualifiers are retained. This intentionally rejects ambiguous results rather than displaying an unrelated recording. Apple Music's appended album is removed from the artist only when it matches independently supplied album metadata.

The card labels the source; **Lyrics → View lyrics source** opens the actual LRCLIB record when available. Failed lookups, instrumental tracks, and unsynchronized records have distinct states. Requests have body-size limits and timeouts; stale requests are cancelled on track changes. Server errors get one bounded retry, and HTTP 429 responses set a cooldown. No timing is synthesized from plain lyrics.

## Build and verification

Release 1.3.0 was verified on 2026-10-11: 37 automated test cases passed on each of the six Minecraft targets (222 passes, zero failures). The opt-in network test is skipped in regular builds and also passed separately against LRCLIB. Development clients for 1.21.11 and 26.3 verified actual layout-button clicks, the selected HUD after the collapse deadline, lyric-reader timing buttons, and compact screen layouts. These checks used original example lyrics. The release JARs were checked for the correct Minecraft metadata, bundled font and native helper, exclusion of the smoke harness, and matching SHA-256 checksums.

Use a **JDK 25** installation to build all targets:

```powershell
.\gradlew.bat build
```

Outputs are in each target's `build/libs/`. `mc12111` uses remapped Mojang names; the 26.x targets use unobfuscated Minecraft classes. The shared source generator applies the small, explicit GUI/API naming differences in `build.gradle`.

The tiny Windows thumbnail helper is bundled. Rebuild it after editing `native/ThumbnailReader.cs` using the Windows .NET Framework compiler:

```powershell
.\scripts\build-native.ps1
```

Tests cover LRC ordering and supplied word timestamps, repeated timestamps, blank lines, offsets, seeking, playback time clamping, invalid configuration, safe filenames, Smart HUD timing, damage fade, ducking recovery, privacy redaction, safe profile imports, per-recording corrections, and rendering each layout with/without lyrics. The 1.3 regression suite also covers selected-layout persistence, playback with unknown duration, plain-text fallback, stale-search cancellation, immediate timing adjustments, translation grouping, and explicit lyric end markers. Rendering tests produce previews in `<target>/build/previews/`.

An optional development-only smoke harness opens the real UI, captures screenshots, and exits:

```powershell
.\gradlew.bat :mc12111:runClient -Psmoke
.\gradlew.bat :mc263:runClient -Psmoke
```

Never distribute artifacts built with `-Psmoke`. Use a normal `clean build` for release. The harness is excluded from normal builds.

## Troubleshooting

- Start playback before opening the overlay; some apps expose sessions only after playback begins.
- In browsers, media-session support depends on the browser and website. Enable the browser's media-key integration if disabled.
- An app may expose metadata but reject next/previous; this is reported in the studio status.
- If Windows PowerShell or WinRT access is restricted on your machine, the studio reports the bridge failure. Diagnostics are stored in `.better-music/bridge.log` within the game instance.
- No genuine background blur is claimed: the reference appearance uses a translucent purple gradient. Low-resolution artwork supplied by an app cannot be made detailed by the renderer.

Code: MIT. Inter: SIL Open Font License, included at `src/main/resources/assets/bettermusic/font/ofl.txt`.

Artwork provenance and the cover-generation prompt are documented in [Artwork notes](docs/branding/ARTWORK.md). This is an independent project, not an official Minecraft, Spotify, or Apple product.
