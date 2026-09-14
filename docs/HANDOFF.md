# Minilock — handoff for a fresh session

You are picking up an Android project mid-flight. This document is everything you need to
continue autonomously. Read it fully before touching anything. The user has switched AI models,
so assume **no shared memory** with the previous session beyond this file, the git history, and
`docs/CORRECTIONS.md`.

Current app version: **v0.0.26**. Repo: <https://github.com/Charles0xhorizonxyz/Minilock> (public).

---

## What the app is

Minilock is a native Android app (`com.miniscreen.minilock`, label **Minilock**) whose centre
piece is a **3D gold pocket watch**. The watch is a WebGL scene (three.js) that renders in a
`WebView` from `app/src/main/assets/lock.html`. three.js is bundled in assets — the app holds
**no INTERNET permission and makes no network calls**.

The watch appears on three surfaces, all through `Watch3D.view(...)`:
- **MainActivity** — the app you open from the launcher (the "app").
- **PreviewActivity** — fullscreen preview.
- **LockScreenActivity** — a *stand-in* lock screen (see caveat below).

There is also a **flat 2D Canvas dial** (`WatchView.java`) used by the screensaver
(`MinilockDreamService`) and live wallpaper (`MiniscreenWallpaper`), because a DreamService /
WallpaperService surface **cannot host a WebView**. That flat dial is the *old* design and has
not received the 3D work.

**Never use the name "Atelier".** It was the old project name and was fully removed. The dial
wordmark is MINISCREEN; the only surviving mentions are historical changelog rows.

---

## Absolute rules (the user set these; do not break them)

1. **Deploy only to the GrapheneOS phone, never the emulator.** An emulator is often connected
   at the same time. **Always** pass `-s 192.168.1.51:46683` to every adb call. The phone is a
   Pixel 7 (`panther`) running GrapheneOS — identify it by `ro.build.display.id`, and call it
   "the GrapheneOS phone", never "the Pixel".
2. **Keep every build.** Copy each APK to `artifacts/Minilock-v<version>-debug.apk` and add a
   row to `artifacts/BUILDS.md`. Never overwrite. Gradle only keeps the newest output and
   `app/build/` is gitignored, so un-copied builds are lost.
3. **Bump `versionName` in `app/build.gradle` every build.** The masthead shows the version, so
   the user can confirm what is running.

---

## How to build, deploy, verify

```bash
ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
D="192.168.1.51:46683"

# 1. bump versionCode and versionName in app/build.gradle
# 2. if you changed tools/watch3d.html or the generator, regenerate the asset:
python tools/make-lock-asset.py

# 3. build
./gradlew.bat assembleDebug --console=plain -q

# 4. archive (REQUIRED) — copy APK and log it in artifacts/BUILDS.md

# 5. install to the GrapheneOS phone ONLY
"$ADB" connect $D
"$ADB" -s $D install -r artifacts/Minilock-v<version>-debug.apk

# 6. launch and screenshot to verify
"$ADB" -s $D shell input keyevent KEYCODE_WAKEUP
"$ADB" -s $D shell am force-stop com.miniscreen.minilock
"$ADB" -s $D shell am start -n com.miniscreen.minilock/.MainActivity
"$ADB" -s $D exec-out screencap -p > out.png     # then Read out.png

# check for crashes
"$ADB" -s $D logcat -b crash -d -t 40 | grep -c minilock
```

**Trigger the stand-in lock screen** (to test it): sleep then wake —
`"$ADB" -s $D shell input keyevent KEYCODE_SLEEP; sleep 3; "$ADB" -s $D shell input keyevent KEYCODE_WAKEUP`.
It only appears if the user has enabled "Stand-in lock screen" in the app AND set their real
screen lock to None. Currently the real lock is off and the stand-in is on.

### The critical testing limitation

**adb cannot simulate the gestures the open work depends on.** `input` is single-touch only.
`sendevent` on `/dev/input/event4` (the touchscreen) needs root, which GrapheneOS denies
(`Permission denied`). And nothing can wave the phone for the gyroscope. So **pinch-zoom,
two-finger placement, and the gyroscope cannot be verified from this side.** Do not claim they
work from a screenshot. The previous session repeatedly reported gesture fixes as done when
they were not — do not repeat that. Instead:

- Verify what you *can*: no crash, no JS error, correct render, single-tap still toggles a
  switch, real battery shows on the counter, scrolling reaches the bottom.
- For gestures, there is a **touch readout HUD** at the top of the app (under the version). It
  prints `touch <action> fingers <n> scale <s> pan <p>`. Ask the user to perform the gesture
  and read the HUD back. That is the only reliable verification channel for multi-touch.

---

## What to check — open items, in priority order

### 1. App pinch-zoom (v0.0.26 — the current fix, UNVERIFIED)
The last change: `ZoomLayout.dispatchTouchEvent` now returns `true` on `ACTION_DOWN` so the
view is never dropped from a gesture that starts over non-interactive content (plain text,
background). The theory: a View that does not consume DOWN stops receiving the rest of the
gesture, so the second finger of a pinch never arrived unless DOWN happened to land on an
interactive child. **Ask the user to pinch over plain areas and read the HUD.** If it shows
`fingers 2` and `scale` changing, it works — then remove the HUD (added only for diagnosis).
If it stays `fingers 1`, the touchscreen delivers multi-touch differently and needs a new
approach.

### 2. Gyroscope (v0.0.25 — UNVERIFIED)
Rebuilt on **quaternions** because the real bug was gimbal lock: `getOrientation()` Euler
azimuth is degenerate when the phone is held upright, so the orbit collapsed to centre — that
is the user's "moves a bit and goes back to centre". `TiltBridge` now sends the raw quaternion
(`getQuaternionFromVector`) and `applyCamera()` in `lock.html` sets the camera orientation
directly and stands it off along its own back axis. **Watch should hold still while you walk
the phone around it, past 360°, no recentring.** The axis map is `qDevice.set(x, z, -y, w)` in
`tools/make-lock-asset.py` — if turning shows the wrong side or pitch/yaw are swapped, that one
line and the signs are the fix.

### 3. "Edges cut off" (recurring, possibly a red herring)
The user has reported this ~6 times. History: it was a real camera-framing bug (fixed v0.0.06),
then I reintroduced it (fixed v0.0.13). It may now be a **persisted placement** — the watch
size/position is saved to Prefs (`place`), so a placement saved by accident makes the watch too
big on next launch. There is a **"Reset watch size and position"** button in the app that
clears it. If the user reports edges cut off again, first have them hit reset, then place the
watch by pinch + two-finger drag. Confirm which surface and which edge before changing code.

### 4. Watch positioning & persistence (v0.0.21+ — UNVERIFIED)
Pinch sizes the watch (native `ScaleGestureDetector` → `__lock.setZoom`), two-finger drag moves
it on both axes (`__lock.nudge`), and the placement is saved on gesture-end and restored on
page load (`Prefs.placement` ↔ `Watch3D.savePlacement/restorePlacement`).

### 5. Real battery on the réserve counter (v0.0.18 — VERIFIED, working)
`BatteryBridge` reads `ACTION_BATTERY_CHANGED` (sticky, no permission) and pushes to
`__lock.setBattery`. The gotcha, already handled: the sticky broadcast arrives before the page
loads, so `Watch3D` re-pushes via a page-ready callback. This is the one recent feature
confirmed on-device (dial matched the phone's percent).

---

## Architecture cheat-sheet

| File | Role |
|---|---|
| `MainActivity.java` | The app screen. Hosts the watch WebView (`hero`), the settings toggles, wrapped in `ZoomScrollView` > `ZoomLayout`. |
| `Watch3D.java` | Builds the WebView, loads `lock.html`, wires pinch (native `ScaleGestureDetector`), two-finger placement, card toggle, battery, immersive mode. **`immersive()` must be called AFTER `setContentView`** — calling `getInsetsController()` before returns null and crashes (this bit twice). |
| `TiltBridge.java` | Gyroscope → `__lock.setQuat`. `GAME_ROTATION_VECTOR` (no magnetometer). |
| `BatteryBridge.java` | Battery → `__lock.setBattery`. |
| `ZoomLayout.java` | Pinch-zooms the whole app page by scaling the canvas. Sits **inside** the ScrollView. Claims DOWN (the v0.0.26 fix). Has the touch HUD hook (`setDebug`). |
| `ZoomScrollView.java` | ScrollView subclass that does not intercept pinches or, when zoomed, sideways drags. |
| `LockScreenActivity.java` | Stand-in lock screen. `showWhenLocked`, swipe-up to dismiss. |
| `LockService.java` | Foreground service; launches the lock screen on `ACTION_SCREEN_ON`. Needs `SYSTEM_ALERT_WINDOW`. |
| `WatchView.java` | The OLD flat 2D Canvas dial. Used by screensaver + wallpaper only. |
| `tools/watch3d.html` | The 3D watch source (also a standalone browser artifact). |
| `tools/make-lock-asset.py` | Transforms `watch3d.html` → `app/src/main/assets/lock.html`: bundles three.js, adds the viewport meta, strips page chrome, adds the `window.__lock` bridge (`setQuat/setZoom/nudge/setCard/setBattery/setPlacement`). **Edit the watch here, then regenerate — never hand-edit `lock.html`.** |

The JS↔native bridge is `window.__lock`. Native calls it via
`web.evaluateJavascript("window.__lock&&__lock.xxx(...)", null)`.

---

## What is NOT done (do not imply otherwise to the user)

- **The stand-in lock screen is not a real lock screen.** Android has no lock-screen-replacement
  API. It only works with the real screen lock set to None, so **the phone is not actually
  locked** — Home escapes it. The real solution is a custom GrapheneOS build with the dial as a
  `ClockProviderPlugin`; a Vultr build brief exists at `docs/vultr-provisioning.md` but nothing
  has been built. Also: with the real lock off, **Google Wallet drops payment cards**.
- **The screensaver and live wallpaper show the old flat dial**, not the 3D watch.
- **The prototypes in `tools/` are not all in the app.** `complications.html` (weather, alarms,
  notifications, moonphase) and `keyguard.html` (off/dimmed/always-on modes) were design
  explorations, never ported to Java.

---

## Working style the user expects

- Be honest about verification. Say "unverified — needs your hands" for gestures. The user
  explicitly called out the previous session for reporting unverifiable fixes as done.
- Diagnose root causes, don't guess-and-check. Several bugs here (gimbal lock, DOWN-consumption,
  ScrollView nesting, sticky-broadcast timing) were structural and needed the actual mechanism
  understood, not a parameter tweaked.
- Keep `docs/CORRECTIONS.md` current — it is the running ledger of every request and its status.
- Commit and push after each build. Attribution line is set by the harness; follow whatever it
  currently specifies.
