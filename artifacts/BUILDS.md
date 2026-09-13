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

## Conventions

- Filename: `Miniscreen-v<versionName>-<buildType>.apk`
- `versionName` lives in [app/build.gradle](../app/build.gradle); bump it before building.
- Debug builds are signed with the local Android debug key and cannot be published.
- Zero permissions so far. If that ever changes, say so in the row.
