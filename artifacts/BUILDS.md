# Build archive

Every APK we build is kept here. Never overwrite an entry; add a new row.

| File | versionName | Built | Size | SHA-256 (first 16) | What is in it |
|---|---|---|---|---|---|
| `Miniscreen-Atelier-debug.apk` | 1.0 | 2026-09-13 21:22 | 31,689 B | `d77775f7da8caf21` | Original dial. ATELIER wordmark, three finishes (Midnight/Malachite/Bordeaux), no complications. Built before versioning started. |
| `Miniscreen-v0.0.01-debug.apk` | 0.0.01 | 2026-09-14 00:06 | 20,217 B | `3643bd6995116644` | Same dial as above, version stamped. None of the browser design work is ported yet. |
| `Miniscreen-v0.0.02-debug.apk` | 0.0.02 | 2026-09-14 00:23 | 22,640 B | `5d21971e37a612f5` | Adds MiniscreenWallpaper (WallpaperService) so the dial renders as lock screen and home screen background. Dial artwork unchanged. Still zero permissions. |
| `Miniscreen-v0.0.03-debug.apk` | 0.0.03 | 2026-09-14 01:24 | 243,067 B | `c80f19a552ce58ea` | Stand-in lock screen: LockScreenActivity hosts the WebGL pocket watch in a WebView from assets, with the gyroscope orbiting the camera. **First build with permissions** — SYSTEM_ALERT_WINDOW, foreground service, boot, notifications, keyguard. Size jump is bundled three.js. |

## Conventions

- Filename: `Miniscreen-v<versionName>-<buildType>.apk`
- `versionName` lives in [app/build.gradle](../app/build.gradle); bump it before building.
- Debug builds are signed with the local Android debug key and cannot be published.
- Zero permissions so far. If that ever changes, say so in the row.
