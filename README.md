<div align="center">
  <img src="kawinnext-icon-preview.png" width="120" alt="KawinNext icon">
  <h1>KawinNext</h1>
  <p><b>Patches for YouTube, YT Music, Reddit and more — applied at runtime, not baked into the APK.</b></p>
</div>

## What this is

KawinNext is an LSPosed module that adds features to apps you already have installed. It finds the methods it needs inside the host app with DexKit and hooks them with Xposed, so nothing is unpacked, patched, re-signed or reinstalled — the store version stays the store version.

The patch logic and the in-app extension code come from the [Morphe](https://morphe.software) patch set; this repository is the runtime side of it: the module, the per-app switches, and the hooks that make those patches work without a patched APK.

**Root and LSPosed are required.** Android 8.1 or newer.

## Install

1. Download the APK from [Releases](https://github.com/KawinPattanai/KawinNext/releases/latest). The `autobuild` pre-release is rebuilt from every push to `main`, and is where fixes land first.
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

These are the switches the module shows for each app, one entry per patch that is registered in that app's hook. An app whose hook carries a patch gets its settings screen in the app itself, under **Settings → Morphe**.

| App | Patches |
| --- | --- |
| YouTube | 45 |
| YouTube Music | 20 |
| Reddit | 3 |
| Google Photos | 2 |
| Photomath | 1 |
| Instagram | 1 |
| Threads | 1 |
| Strava | 2 |
| AllTrails | 1 |

### YouTube

- Add to queue
- Ambient mode
- Auto captions
- Autoplay control
- Bypass image region restrictions
- Change start page
- Channel search
- Channel whitelist
- Copy text
- Copy video link
- DeArrow
- Disable Shorts resuming on startup
- Disable video codecs
- Downloads
- Enable debugging
- Feed thumbnail like position
- Fix media session stop
- Force original audio
- Fullscreen playlist panel
- Hide ads
- Hide layout components
- Hide Shorts components
- Hide video action buttons
- Miniplayer
- Native playlist queue
- Navigation bar
- Niconico comments
- On-the-go player
- Open system share sheet
- Patches update notice
- Playback speed
- Playlist button
- Playlist improvements
- Remove background playback restrictions
- Return YouTube Dislike
- Sanitize sharing links
- Seekbar
- SponsorBlock
- Spoof video streams
- Swipe controls
- Video quality

### YouTube Music

- A-B loop
- Change miniplayer color
- Disable auto captions
- Enable debugging
- Enable exclusive audio playback
- Hide ads
- Hide layout components
- Hide upgrade button
- Navigation bar
- Remember video quality
- Remove background playback restrictions
- Sanitize sharing links
- SponsorBlock

### Reddit

- Hide ads
- Sanitize sharing links
- Start as guest

### Google Photos

- Enable DCIM folders backup control
- Spoof features

### Photomath

- Unlock plus

### Instagram

- Hide ads

### Threads

- Hide ads

### Strava

- Disable subscription suggestions
- Unlock subscription features

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
