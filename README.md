<div align="center">
  <img src="kawinnext-icon-preview.png" width="120" alt="KawinNext icon">
  <h1>KawinNext</h1>
  <p><b>Patches for YouTube, YT Music, Reddit and more — applied at runtime, not baked into the APK.</b></p>
  <a href="https://github.com/KawinPattanai/KawinNext/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/KawinPattanai/KawinNext?include_prereleases"></a>
  <a href="https://github.com/KawinPattanai/KawinNext/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/KawinPattanai/KawinNext/total"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-blue"></a>
  <a href="https://discord.gg/QWUrAA2mKq"><img alt="Discord" src="https://img.shields.io/badge/Discord-join-5865F2?logo=discord&logoColor=white"></a>
  <a href="https://t.me/revancedxposed"><img alt="Telegram" src="https://img.shields.io/badge/Telegram-channel-2CA5E0?logo=telegram&logoColor=white"></a>
</div>

## What this is

KawinNext is an LSPosed module that adds features to apps you already have installed. It finds the methods it needs inside the host app with DexKit and hooks them with Xposed, so nothing is unpacked, patched, re-signed or reinstalled — the store version stays the store version.

The patch logic and the in-app extension code come from the [Morphe](https://morphe.software) patch set; this repository is the runtime side of it: the module, the per-app switches, and the hooks that make those patches work without a patched APK.

**Root and LSPosed are required.** Android 8.1 or newer.

## Install

1. Download the APK from [Releases](https://github.com/KawinPattanai/KawinNext/releases/latest).
2. Install it, then enable the module in LSPosed and tick the apps you want in its scope.
3. Reboot the device.
4. Open the module and pick which patches each app gets. Every patch also has its own settings screen inside the target app: **YouTube → Settings → Morphe**.

## Supported apps

| App | Package |
| --- | --- |
| YouTube | `com.google.android.youtube` |
| YouTube Music | `com.google.android.apps.youtube.music` |
| Reddit | `com.reddit.frontpage` |
| Google Photos | `com.google.android.apps.photos` |
| Photomath | `com.microblink.photomath` |
| Instagram | `com.instagram.android` |
| Threads | `com.instagram.barcelona` |
| Strava | `com.strava` |
| AllTrails | `com.alltrails.alltrails` |

## Patches

### YouTube

- Remove ads
- SponsorBlock
- Return YouTube Dislike
- DeArrow (alternative titles and thumbnails)
- Remove background playback restrictions
- AI channel filtering and reporting (AiSList)
- Add to queue
- Swipe controls
- Seekbar
- Remove share links tracking query parameter
- Open system share sheet
- Hide and change navigation buttons
- Remember video quality changes
- Show video quality button
- Show advanced video quality menu
- Copy video url video player button
- Copy video titles and comments
- Open external downloader app
- Custom playback speed
- Remember playback speed
- Playback speed dialog button
- Hide layout components
- Hide feed flyout menu items
- Hide video action buttons
- Disable Shorts resuming on startup
- Disable video codecs
- Disable auto captions
- Bypass image region restrictions
- Change start page
- Channel search
- Channel whitelist

### YouTube Music

- Remove music video ads
- Remove background playback restrictions
- Hide upgrade button
- Hide 'Get Music Premium' label
- Enable exclusive audio playback

### Reddit

- Hide ads
- Sanitize sharing links
- Start as guest

### Google Photos

- Spoof Pixel XL

### Photomath

- Unlock plus

### Instagram

- Hide ads

### Threads

- Hide ads

### Strava

- Unlock subscription features
- Disable subscription suggestions

### AllTrails

- Enable Peak membership

## Limits worth knowing

- The module refuses to run on an APK that is already patched by Morphe or ReVanced. Running both would hook the same code twice.
- A patch only applies where its fingerprinted method still exists in that app version. When one fails, the module reports it and keeps the rest running.
- Root is not optional, and an unrooted device cannot use this module at all.

## Help

Bugs, crashes and patch requests: [open an issue](https://github.com/KawinPattanai/KawinNext/issues). Include the app version, the module version and the LSPosed log line for the failing patch.

## Credits

- [Morphe](https://morphe.software) — patch set and extension code this module runs.
- [ReVanced](https://revanced.app) — the project the patch format and many patches originated from.
- [DexKit](https://luckypray.org/DexKit/en/) — the dex parsing library that finds the methods to hook.

This is not an official Morphe or ReVanced project. Please report issues here, not to their developers.

## Upgrading from ReVancedXposed

KawinNext keeps the original package id, so it installs over the old module as an update. Settings are not migrated automatically: export them from the old version and import them in the new one.

## License

GPL-3.0. See [LICENSE](LICENSE).
