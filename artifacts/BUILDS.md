# Build archive

Every APK we build is kept here. Never overwrite an entry; add a new row.

| File | versionName | Built | Size | SHA-256 (first 16) | What is in it |
|---|---|---|---|---|---|
| `Miniscreen-v1.0-debug.apk` | 1.0 | 2026-09-13 21:22 | 31,689 B | `d77775f7da8caf21` | Original dial. ATELIER wordmark, three finishes (Midnight/Malachite/Bordeaux), no complications. Built before versioning started. |
| `Miniscreen-v0.0.01-debug.apk` | 0.0.01 | 2026-09-14 00:06 | 20,217 B | `3643bd6995116644` | Same dial as above, version stamped. None of the browser design work is ported yet. |
| `Miniscreen-v0.0.02-debug.apk` | 0.0.02 | 2026-09-14 00:23 | 22,640 B | `5d21971e37a612f5` | Adds MiniscreenWallpaper (WallpaperService) so the dial renders as lock screen and home screen background. Dial artwork unchanged. Still zero permissions. |
| `Miniscreen-v0.0.03-debug.apk` | 0.0.03 | 2026-09-14 01:24 | 243,067 B | `c80f19a552ce58ea` | Stand-in lock screen: LockScreenActivity hosts the WebGL pocket watch in a WebView from assets, with the gyroscope orbiting the camera. **First build with permissions** — SYSTEM_ALERT_WINDOW, foreground service, boot, notifications, keyguard. Size jump is bundled three.js. |
| `Minilock-v0.0.04-debug.apk` | 0.0.04 | 2026-09-14 01:38 | 242,784 B | `d10089f6318644e3` | Dropped the Atelier name everywhere: package is now com.miniscreen.minilock, AtelierDreamService renamed, label is Minilock, the dial wordmark is MINISCREEN. **Fixed the crash** - getInsetsController() was called before setContentView, so the decor view was null. The 3D pocket watch replaces the flat dial in the app and preview. |
| `Minilock-v0.0.05-debug.apk` | 0.0.05 | 2026-09-14 01:40 | 243,076 B | `e133fad487ed3937` | Chrome switch on the shared WebView host: #bare for the in-app hero, #preview fullscreen, none for the lock screen. Stops the unlock hint and card appearing where they do not belong. |
| `Minilock-v0.0.06-debug.apk` | 0.0.06 | 2026-09-14 02:02 | 263,973 B | `0da3dd9a340c6ef4` | Camera now fits the watch to the viewport, so the case no longer clips on a tall screen. Pinch to zoom, double tap to reset. Gyroscope moved into a shared TiltBridge and wired into all three surfaces, not just the lock screen. Unlock hint removed. |
| `Minilock-v0.0.07-debug.apk` | 0.0.07 | 2026-09-14 02:05 | 244,484 B | `ecadd4739385329d` | Card under the watch became a user setting (Prefs.card), applied live through __lock.setCard rather than on next launch. Hero keeps its gestures so the ScrollView cannot eat the pinch. |
| `Minilock-v0.0.08-debug.apk` | 0.0.08 | 2026-09-14 02:06 | 244,672 B | `b63b4ede2c9e3991` | Camera reserves vertical room when the card is showing. |
| `Minilock-v0.0.09-debug.apk` | 0.0.09 | 2026-09-14 02:08 | 244,744 B | `a9915737ccf10d65` | **Added the viewport meta tag.** WebView defaults to a 980px viewport and scales the page down, which is why every label rendered tiny and soft. Also disabled WebView caching, since the asset URL never changes between versions. |
| `Minilock-v0.0.10-debug.apk` | 0.0.10 | 2026-09-14 02:10 | 244,936 B | `044efe5cd09d669d` | Camera measures the card's real height instead of guessing, fits the watch into the space left, and aims lower so it rides above the text. |
| `Minilock-v0.0.11-debug.apk` | 0.0.11 | 2026-09-14 02:18 | 245,116 B | `1ab7a2913d598928` | Shows the version in the masthead, so which build is running is never a guess. |
| `Minilock-v0.0.12-debug.apk` | 0.0.12 | 2026-09-14 02:21 | 245,188 B | `a688c91a019e3d33` | Taller hero, no scrollbar, larger app text, double tap zooms. |
| `Minilock-v0.0.13-debug.apk` | 0.0.13 | 2026-09-14 02:23 | 245,260 B | `c13aba4d1822c0cc` | **Fixed the framing regression**: the look-at offset that makes room for the card was taken from the fitted distance, so zooming in threw the watch off the top. It now derives from the current distance. Tap no longer flips the watch, which collided with double-tap zoom. |
| `Minilock-v0.0.14-debug.apk` | 0.0.14 | 2026-09-14 02:26 | 245,744 B | `10519d8d556842ae` | **Pinch zoom actually works**: read natively with ScaleGestureDetector instead of the page touch handlers, which a ScrollView claimed as a scroll before the page saw them. |

## Conventions

- Filename: `Miniscreen-v<versionName>-<buildType>.apk`
- `versionName` lives in [app/build.gradle](../app/build.gradle); bump it before building.
- Debug builds are signed with the local Android debug key and cannot be published.
- Zero permissions so far. If that ever changes, say so in the row.
