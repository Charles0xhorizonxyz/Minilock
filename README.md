# Minilock

An original luxury-watch dial for Android — as a screensaver, a live wallpaper, and (in progress) a replacement lock screen clock on a custom GrapheneOS build.

Everything is drawn with `android.graphics.Canvas`. No network access, no accounts, no advertisements, no runtime libraries, and — so far — no permissions of any kind.

> The dial is an original design. It draws on the vocabulary of contemporary fine watchmaking — textured dials, applied indices, pointer counters, guilloché — but reproduces no brand's logo, dial or model, and claims no affiliation with any manufacturer.

## Where things stand

| Piece | State |
|---|---|
| `DreamService` screensaver | Working, installed |
| `WallpaperService` live wallpaper | Working — renders as lock screen and home screen background |
| Redesigned dial (gold alloys, craft refinements, complications) | Designed in the browser, **not yet ported to Java** |
| Custom keyguard clock on GrapheneOS | Planned — needs a custom OS build |

The Android app currently ships the **original** dial. The redesign lives in the prototypes under [`tools/`](tools/) and has not been ported yet.

## Repository layout

```
app/            Android app — DreamService, WallpaperService, the Canvas dial
tools/          Browser design prototypes (open directly in a browser)
artifacts/      Every APK we have built, plus BUILDS.md logging them
```

### Design prototypes

These are self-contained HTML files. Open them in a browser; nothing to install.

| File | What it is |
|---|---|
| [`tools/dial-bench.html`](tools/dial-bench.html) | The shipping dial beside a refined one, with seven toggleable craft refinements and live draw-cost counters |
| [`tools/complications.html`](tools/complications.html) | Phone information — weather, battery, alarms, notifications — arranged as watch complications, with the permission cost of each |
| [`tools/caseback.html`](tools/caseback.html) | An open-face pocket watch you turn over by its crown; settings engraved on the movement side |
| [`tools/watch3d.html`](tools/watch3d.html) | The pocket watch in WebGL — lathed case, procedural studio lighting, turning under angular velocity rather than a tween |
| [`tools/keyguard.html`](tools/keyguard.html) | What a custom GrapheneOS build would put on the lock screen, beside stock |

`watch3d.html` loads three.js from a CDN. The rest have no external dependencies beyond web fonts.

## Building

JDK 17 and Android SDK platform 36. Set `ANDROID_HOME`, or put `sdk.dir` in an untracked `local.properties`.

```powershell
.\gradlew.bat assembleDebug
```

Every build is archived — bump `versionName` in [`app/build.gradle`](app/build.gradle), build, copy the APK into `artifacts/`, and add a row to [`artifacts/BUILDS.md`](artifacts/BUILDS.md). Nothing there is ever overwritten, because Gradle only keeps the most recent output and `app/build/` is ignored.

## Installing

```bash
adb install -r artifacts/Miniscreen-v0.0.02-debug.apk
```

- **Screensaver** — Settings → Display → Screen saver → Miniscreen
- **Live wallpaper** — Settings → Wallpaper → Live wallpapers → Miniscreen

Debug builds are signed with the local Android debug key and are not distributable.

## Why a custom OS for the lock screen

Android has never exposed a way to replace the lock screen. The keyguard belongs to `com.android.systemui`; it is signed with the platform key and sits behind verified boot, so a modified SystemUI means re-signing the whole OS. A live wallpaper can change the lock screen's *background*, but the system still draws its own clock on top.

The plan is to build GrapheneOS with one small patch: the dial as a `ClockProviderPlugin`, plus the SystemUI change that lets plugins signed with our own key load on a user build. The dial then fills the clock slot AOSP already reserves — no second clock, nothing overlapping. After that initial build the dial ships as an ordinary APK, so iterating on it needs no reflash.

## Third-party

`app/src/main/assets/three.min.js` is [three.js](https://threejs.org) r155, MIT licensed, bundled rather than fetched because the app holds no `INTERNET` permission.

## Licence

Not yet chosen.
