# Miniwatch — handoff for a fresh session

You are picking up an Android project mid-flight. This document is everything you need to
continue autonomously. Read it fully before touching anything. The user has switched AI models,
so assume **no shared memory** with the previous session beyond this file, the git history, and
`docs/CORRECTIONS.md`.

Current app version: **v0.0.62**. Since v0.0.62 the app is **Miniwatch** (`com.miniscreen.miniwatch`, preferences file `miniwatch`, page bridge `miniwatch`, env `MINIWATCH_DEVICE`); Minilock was its name from v0.0.11 to v0.0.61 and survives only in the old APK filenames and the GitHub repository name. `Watch3D.sync(web)` applies design, background, plate, text and 3D/2D; it runs on page load and in `onResume` of the lock screen and the preview, because the staged lock screen keeps its page across wakes (and Home leaves it alive). Wake on pickup: `LockService` arms sensor type 25 (`TYPE_PICK_UP_GESTURE`, hidden in the SDK; falls back to significant motion) with `requestTriggerSensor` on SCREEN_OFF and wakes the screen with a `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` lock held 1.5 s; `dumpsys sensorservice` shows the `0x01010013 ... LockService` registration after screen-off. The 2D pendulum sign: positive `swingGroup.rotation.z` moves the body to the right, so `phiTarget = +atan2(gx, -gy)`. Near the top of the settings: a "Watch motion" dropdown, preference `motion` = `orbit` (floating, the camera orbits), `hang3d` (held by the ring, in 3D) or `hang2d` (held by the ring, in 2D), pushed with `__lock.setMotion(m)` by `Watch3D.applyFlat`; the older `threeD` boolean is honoured when `motion` is unset, and `setFlat` remains as an alias. Held in 3D: the swing group (pivot at the ring, Euler order XZY so the twist never moves the body off gravity) also tilts in depth (`psi`, a spring toward `atan2(-gz,-gy)`, clamped 0.7 rad) and twists about the ring (`yaw`: each change of the phone's heading, measured from the device x axis which is level whether the phone is upright or flat, kicks it the other way, then the torsion spring `-1.2*yaw - 1.6*vyaw` unwinds it over a few seconds), and `phiTarget` becomes the true 3D angle `atan2(gx, hypot(gy,gz))`; the camera stays at identity as in 2D. Verified with pretend turns through `page-eval.py`; note that DevTools evaluation sees the page's `var` names and `__lock`, not its `let`/`const` names, so probe through `var`s (`psi`, `yaw`, `psiTarget`, `phiTarget`, `hang3d`). Held in 2D (`hang2d`; the camera stays at identity and the watch is a pendulum: `retarget()` derives gravity on the screen from the absolute orientation into `phiTarget`, and the generator swaps the bow's rock spring in `step()` for a softer, wider spring toward it that pulls the short way round (`atan2(sin, cos)` of the difference) with no limit since v0.0.61: `phi` wraps, `hangK` (a `var`) slackens the spring within about 7° of flat and ramps it to full by 45°, `pose()` moves `swingGroup.position` to `(-1.42 sin phi, 1.42 cos phi)` so the body stays put while the ring goes round it, and `__lock.hang()` reads the angle in degrees; a pretend test needs `testTurn(0,90,roll)` to stand a flat phone up first) and "Gyroscope" (off = `TiltBridge` never started; `__lock.gyroReset()` on the live switch-off). The user has had the gyroscope OFF at times, which makes `testTurn` a no-op (no sample, no baseline); check `tools/prefs-set.py` before reading a turn test. The lock screen's stay/fade clock lives in `LockScreenActivity` (arm/fadeOut/dark/wake on a Handler) and the page's `__lock.fade(s)`/`__lock.wake()` (a `#nightfall` veil; `dark` skips rendering in the loop wrapper); in 2D mode a native veil view does the same. To check the hold, read `dumpsys power` for the `SCREEN_BRIGHT_WAKE_LOCK 'WindowManager` line: present while the watch shows, gone once dark. **Edit preferences on the phone only with `tools/prefs-set.py`** (force-stops the app, pulls, edits, pushes): sed over adb shell once dropped the file's closing tag. Repo: <https://github.com/Charles0xhorizonxyz/Minilock> (public).

---

## What the app is

Miniwatch is a native Android app (`com.miniscreen.miniwatch`, label **Miniwatch**) whose centre
piece is a **3D gold pocket watch**. The watch is a WebGL scene (three.js) that renders in a
`WebView` from `app/src/main/assets/lock.html`. three.js is bundled in assets — the app holds
**no INTERNET permission and makes no network calls**.

The watch appears on three surfaces, all through `Watch3D.view(...)`:
- **MainActivity** — the app you open from the launcher (the "app").
- **PreviewActivity** — fullscreen preview.
- **LockScreenActivity** — a *stand-in* lock screen (see caveat below).

There is also a **flat 2D Canvas dial** (`WatchView.java`) used by the screensaver
(`MiniwatchDreamService`) and live wallpaper (`MiniscreenWallpaper`), because a DreamService /
WallpaperService surface **cannot host a WebView**. That flat dial is the *old* design and has
not received the 3D work.

**Never use the name "Atelier".** It was the old project name and was fully removed. The dial
wordmark is MINISCREEN; the only surviving mentions are historical changelog rows.

---

## Absolute rules (the user set these; do not break them)

1. **Deploy only to the GrapheneOS phone, never the emulator.** An emulator is often connected
   at the same time. **Always** pass `-s <the phone's address>` to every adb call. The address
   is wireless debugging and it CHANGES (it was `192.168.1.51:46683`, then `192.168.1.208:40685`
   on 2026-09-14): find it with `adb mdns services` (the `_adb-tls-connect._tcp` entry), connect,
   and confirm `ro.build.display.id` is `2026091001` before doing anything. If the phone does
   not answer pings, it is off the network; ask the user. The phone is a
   Pixel 7 (`panther`) running GrapheneOS — identify it by `ro.build.display.id`, and call it
   "the GrapheneOS phone", never "the Pixel".
2. **Keep every build.** Copy each APK to `artifacts/Miniwatch-v<version>-debug.apk` and add a
   row to `artifacts/BUILDS.md`. Never overwrite. Gradle only keeps the newest output and
   `app/build/` is gitignored, so un-copied builds are lost.
3. **Bump `versionName` in `app/build.gradle` every build.** The masthead shows the version, so
   the user can confirm what is running.

---

## How to build, deploy, verify

```bash
ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
D="192.168.1.208:40685"     # changes; see rule 1

# 1. bump versionCode and versionName in app/build.gradle
# 2. if you changed tools/watch3d.html or the generator, regenerate the asset:
python tools/make-lock-asset.py

# 3. build
./gradlew.bat assembleDebug --console=plain -q

# 4. archive (REQUIRED) — copy APK and log it in artifacts/BUILDS.md

# 5. install to the GrapheneOS phone ONLY
"$ADB" connect $D
"$ADB" -s $D install -r artifacts/Miniwatch-v<version>-debug.apk

# 6. launch and screenshot to verify
"$ADB" -s $D shell input keyevent KEYCODE_WAKEUP
"$ADB" -s $D shell am force-stop com.miniscreen.miniwatch
"$ADB" -s $D shell am start -n com.miniscreen.miniwatch/.MainActivity
"$ADB" -s $D exec-out screencap -p > out.png     # then Read out.png

# check for crashes
"$ADB" -s $D logcat -b crash -d -t 40 | grep -c miniwatch
```

**Trigger the stand-in lock screen** (to test it): sleep then wake —
`"$ADB" -s $D shell input keyevent KEYCODE_SLEEP; sleep 3; "$ADB" -s $D shell input keyevent KEYCODE_WAKEUP`.
It only appears if the user has enabled "Stand-in lock screen" in the app AND set their real
screen lock to None. Currently the real lock is off and the stand-in is on. Since v0.0.36 the
watch is **staged on SCREEN_OFF** (it exists, paused, while the phone sleeps) so it is in front
the instant the screen wakes and never launches after the system's double-tap-power camera;
SCREEN_ON only launches it if nothing was staged. `input keyevent KEYCODE_POWER KEYCODE_POWER`
reproduces the camera gesture from adb. Since v0.0.42/44 flicks of the dial are gestures: the
page's `maybeGesture` names them ("left1": one flick right to left from the front; "right2":
one hard flick left to right that carries the watch two full turns, armed at release and
decided frame by frame in `spinCheck` at 3.5 pi from where the finger landed) and calls
`miniwatch.gesture(name)`; `Watch3D.view`
takes an `onGesture` consumer that only `LockScreenActivity` passes, and `Gestures.perform`
runs the user's choice from Prefs `g_left1`/`g_right2` (defaults unlock / camera; options none,
unlock, camera, torch, app, alarms; two dropdown rows in the app). `__lock.face()` returns cos
of the turn angle (1 dial, -1 caseback) so a test can check the face first. Real `input swipe`
flicks work but collide with the user's fingers; the reliable test dispatches synthetic pointer
events inside the page with in-page `setTimeout` timing (a flick must finish within 1 s, which
round-trips through page-eval cannot do; a two-turn flick needs two quick moves before the up
so the release velocity is high). Swipe up still
dismisses. `BootReceiver` also restarts the service on `MY_PACKAGE_REPLACED`, so the watch is
staged straight after an install.

### Testing from this side: what adb can and cannot do

`input` is single-touch only, `sendevent` on the touchscreen needs root (GrapheneOS denies it),
and nothing here can move the phone. So **pinch, two-finger placement and the real sensor still
need the user's hands.** Do not claim they work from a screenshot. Earlier sessions did, repeatedly.

What you CAN do, which the earlier sessions could not:

- **Evaluate JavaScript inside the live page.** The debug build exposes WebView DevTools.
  `python tools/page-eval.py "<expression>"` forwards the socket and evaluates in every open
  page (the in-app hero and the preview both load `lock.html`; tell them apart by `vh`).
  Read `__lock.placement()`, read the readout text, or call any bridge function.
- **Pretend the phone turned:** `__lock.testTurn(yaw, pitch, roll)` in degrees, about the
  phone's own axes, held until `testTurn(null)`. Screenshot after about a second. This verifies
  the camera maths; it cannot verify the sensor's sign convention.
- **Drive zoom and placement:** `__lock.setZoom(z)` (camera distance multiplier, 0.42–2.4)
  and `__lock.nudge(dx, dy)` in pixels. `__lock.setBackground(0..1)` paints the studio.
- **Test a one-finger gesture without touching the screen:** dispatch synthetic pointer events
  in the page. `python tools/page-eval.py "$(cat tools/probe-carry.js)"` does this for the
  ring carry, restoring the placement in the same tick so nothing renders or saves. Read the
  header of that file for the two quirks (setPointerCapture throws for synthetic pointers; the
  module's own variables are not reachable from the evaluator).
- **Read what the sensor delivers:** the fullscreen preview shows a readout under the top edge,
  `gyro #<samples>  yaw  pitch  roll` (Euler YXZ of the orientation relative to the start, in
  degrees; `TEST` while a pretend turn is active). The app screen has the touch readout
  `touch <action> fingers <n> scale <s> pan <p>`. Ask the user to perform the gesture or the
  movement and read the line back. Both readouts are temporary diagnostics.

Practical gotchas that cost time:

- Git Bash rewrites `/data/local/tmp/...` into a Windows path before adb sees it. Put
  `export MSYS_NO_PATHCONV=1` in front of any adb shell command that carries a device path.
- `PreviewActivity` is not exported, so `am start` is refused. Open it through the app: start
  MainActivity, `input swipe 540 1900 540 500 300` twice, then `input tap 540 1980` on
  "Preview fullscreen". Tapping the watch in the app does NOT open the preview: the WebView
  swallows the click so the hero's click listener never fires. Small bug, unfixed.
- The phone sleeps; a black screenshot means that. Send `KEYCODE_WAKEUP`, and expect the
  stand-in lock screen on wake — dismiss it with `input swipe 540 1800 540 800 200`.
- `uiautomator dump /data/local/tmp/ui.xml` finds a button's bounds by its text.
- The preview is not orientation-locked. Turning the phone to landscape recreates the activity,
  reloads the page and takes a new gyro baseline. Keep it portrait during gyro tests, or lock it.
- While the user is handling the phone, adb gestures land in their session and confuse both of
  you. Prefer the in-page probes above; save screen gestures for when the phone is on the desk.

---

## What to check — open items, in priority order

### 1. Gyroscope (v0.0.28 — two real bugs fixed, needs the user's hands)

Both found by reading the code, both fixed in v0.0.28:

- **The camera pass never ran.** Since v0.0.05 the generator wrapped the render loop as
  `var loop = function loop(now){...}`. Inside a named function expression the name binds to
  the function itself, so the loop's own `requestAnimationFrame(loop)` re-scheduled the raw
  loop and the wrapper carrying `applyCamera()` ran once per page load. Everything in
  `applyCamera` — camera fit, zoom, placement, card offset, gyroscope orbit — was dead in every
  build to v0.0.27. That is the recurring "edges cut off" (the fitted distance never applied;
  the camera sat at the original fixed 9.2) and the "moves a bit and goes back to centre" (the
  bow spring in `setQuat`, which does run). Fixed by naming the inner function `rawLoop`.
- **The axis map was a conjugation.** `qDevice.set(x, z, -y, w)` relabelled the rotation axis,
  turning a yaw about the screen's vertical axis into a roll about the viewing axis. The sensor
  quaternion maps device axes (X right, Y up the screen, Z out of the screen) to the world, and
  a three.js camera's local axes are the same, so the components pass straight through and the
  baseline product cancels the world frame. Now `qDevice.set(x, y, z, w)`.

Verified from here with `testTurn`: yaw 60 shows the case from the side, pitch 60 goes over the
top, roll 60 spins it flat. **Not verified:** the real sensor's sign convention (does walking to
the left read as positive yaw and show the watch's right side?). If the user reports a mirrored
or wrong-side motion, negate the offending component in `setQuat`; do not reorder the axes.
The baseline is the first sample after page load, so the phone should already be held the way
the user wants to see the watch head-on when the preview opens.

### 2. App pinch-zoom (v0.0.26 — UNVERIFIED, unchanged)

`ZoomLayout.dispatchTouchEvent` returns `true` on `ACTION_DOWN` so the view is never dropped
from a gesture that starts over plain text. That diagnosis is right but covers half the failure:
if the first finger drifts more than the touch slop vertically before the second lands, the
ScrollView intercepts, sends the ZoomLayout a CANCEL and owns the rest; Android never consults
`onInterceptTouchEvent` again, so the "two fingers is never a scroll" rule cannot run. **Ask the
user to pinch over the title text and read the HUD.** `fingers 2` with scale changing: works,
remove the HUD. `CANCEL fingers 1` then nothing: the interception gap — fix by having
`ZoomScrollView.onTouchEvent` hand a `POINTER_DOWN` back to the ZoomLayout. `fingers 1`
throughout with no CANCEL: the second pointer is not reaching the window at all.

### 3. Placement (v0.0.28 — live for the first time)

Prefs holds `place = 0.931216,-0.748,2.224` (zoom, camera X, camera Y in world units), saved by
an accidental gesture while nothing rendered. Now that the camera pass runs it puts the watch in
the bottom-right corner of the hero, the preview and the lock screen. The user should tap
**"Reset watch size and position"** in the app (it clears the pref and reloads). Do not clear
app data; the previous session was called out for that. Note the sign: `userX/userY` move the
CAMERA, so the watch goes the other way.

### 4. Background scale and carry by the ring (v0.0.29)

Both shipped in v0.0.29; the scale became a rainbow in v0.0.31 and natural paper tones in
v0.0.39. **Background**: a `SeekBar` in the app whose track carries eight even stops of
seamless-paper colours (ivory, rose clay, ochre, sage, teal grey, slate blue, plum, charcoal)
writes Prefs `bg` (0 = the dark studio, 100 = ivory); `Watch3D.applyBackground` pushes it on
page load and live while sliding; `PAPERS`/`bgColour(v)` in the generator interpolate the same
stops and `__lock.setBackground` regenerates the backdrop texture in that colour with the usual
vignette; the card's ink follows the colour's luminance. Since v0.0.40 the backdrop plane
follows the camera in `applyCamera` (square-on, six units behind the watch), so the orbit never
reaches its edge. Verified on the phone.
**Carry**: capture-phase pointer listeners on the canvas hit-test the bow (local `(0, 1.452, 0)`
projected to the screen, reach `max(30px, 10% of width)`), call `__lock.nudge` per move, and
stop the event so the turn handler never starts; a second finger hands over to the native
two-finger placement. Native saves the placement on every `ACTION_UP` in `enablePinch`.
Verified in the page with synthetic events; **not yet with a real finger**. Placement is now a
screen-space offset (camera right/up), so it survives the gyroscope orbit.

### 5. The caseback controls are painted on the metal (v0.0.32–v0.0.34)

The HTML plate (`#plate`) is hidden by CSS and kept only as the state model. `backControls(g,F)`
in the generator draws the alloy medallions, six slide levers and the counter selector into the
caseback texture after `backArt`, by wrapping `paintBack`; it records `backRegions` in design
units (the caseback canvas is set up so 291 = the rim, y down). A tap (`release(e)` with
`moved<5`, back facing within cos 0.8 and |omega| < 0.6) ray-casts onto `backMesh`, converts
the hit's uv to design units (`x=(u-.5)*582, y=(.5-v)*582`) and runs the region's action,
which clicks or changes the hidden plate's own control, so wiring and persistence are untouched,
then repaints. Because the controls are in the texture they follow the watch exactly under the
gyroscope orbit, which the overlay never could. Two long-standing artwork bugs surfaced with
it: `BACK_Z` had the textured disc 0.006 inside the solid caseback (now −0.175, proud of the
flat at −0.168), and `arcText` rotated bottom glyphs by a half turn (now upright). Both fixed
in `tools/watch3d.html`, the source.

### 6. The caseback plate is remembered (v0.0.30)

The plate on the back of the watch (finish, movement toggles, counter at six) used to live only
in the page, so it reset with every new page — that was the user's "the colour is not kept". The
page now calls `miniwatch.put("plate", json)` (a `@JavascriptInterface` object added in
`Watch3D.view`) on any click or change inside `#plate`, and `Watch3D.restorePlate` pushes
`__lock.setState(json)` on load, which drives the plate's own controls (clicks the swatch,
dispatches `change` on the checkboxes) so the existing wiring applies it. Prefs key `plate`.
Verified across a sleep and wake. The app's own Ambient/Sweep toggles still only affect the old
flat dial; the 3D watch reads the plate. The "Set as screensaver" button on the plate is gone.

### 7. Battery (measuring first; decision deferred by the user)

Not minimal as designed. In order of cost: `LockScreenActivity` sets `FLAG_KEEP_SCREEN_ON`, so
the display never times out while the watch shows; the scene renders every frame with soft
shadows, MSAA and a lit environment at 2x CSS resolution whether or not anything moves (no
rest mode); the Factory design (sweep on, ambient off) repaints the 1024x1024 dial texture
every 33 ms (`every=(state.sweep&&!state.ambient)?33:1000` in the loop), Custom with ambient on
once a second; `TiltBridge` runs `GAME_ROTATION_VECTOR` at `SENSOR_DELAY_GAME` (50 Hz) and
crosses into the page on every changed sample; staging the lock screen on SCREEN_OFF costs a
page load each time the screen goes dark, then pauses. `tools/battery-report.py` prints the
phone's own accounting since the last charge (screen-on time, estimated mAh per uid, Miniwatch's
CPU/sensor/foreground time; nothing is reset) and `--sample 600` measures the live drain from
the charge counter over ten minutes (unplugged, watch on screen). The user chose to measure
for a couple of days before deciding on fixes; the candidates are a screen timeout on the lock
screen, a rest mode for the renderer, a cap on the sweep repaint, and a lower gyro rate at rest.

### 8. "Edges cut off" (root cause found; framing never exercised)

Root cause is item 1. After the reset, check the framing on the preview: the whole case with
the bow should fit with a margin. If it does not, the fit constants `NEED_W`/`NEED_H` in the
generator are the knob, and they have never actually been exercised on a device.

### 5. Real battery on the réserve counter (v0.0.18 — VERIFIED, working)
`BatteryBridge` reads `ACTION_BATTERY_CHANGED` (sticky, no permission) and pushes to
`__lock.setBattery`. The gotcha, already handled: the sticky broadcast arrives before the page
loads, so `Watch3D` re-pushes via a page-ready callback. This is the one recent feature
confirmed on-device (dial matched the phone's percent).

---

## Architecture cheat-sheet

| File | Role |
|---|---|
| `MainActivity.java` | The app screen. Hosts the watch WebView (`hero`), then the background slider, the "Reset to default" button and the Preview button, then the settings list: a Design dropdown (Factory = the page's own gold, dial and studio, with only size, position and the text switch applied; Custom = saved caseback and background as well; `Prefs.setBackground`/`setPlate` flip it back to custom), the switches (Ambient, Sweep, Stand-in lock screen, Text under the watch, Screensaver, Display over other apps — the last two are `mirror()` switches that show a system state and open its page) and the two gesture dropdowns, wrapped in `ZoomScrollView` > `ZoomLayout`. |
| `Watch3D.java` | Builds the WebView, loads `lock.html`, wires pinch (native `ScaleGestureDetector`), two-finger placement, card toggle, battery, immersive mode. **`immersive()` must be called AFTER `setContentView`** — calling `getInsetsController()` before returns null and crashes (this bit twice). |
| `TiltBridge.java` | Gyroscope → `__lock.setQuat`. `GAME_ROTATION_VECTOR` (no magnetometer). |
| `BatteryBridge.java` | Battery → `__lock.setBattery`. |
| `ZoomLayout.java` | Pinch-zooms the whole app page by scaling the canvas. Sits **inside** the ScrollView. Claims DOWN (the v0.0.26 fix). Has the touch HUD hook (`setDebug`). |
| `ZoomScrollView.java` | ScrollView subclass that does not intercept pinches or, when zoomed, sideways drags. |
| `LockScreenActivity.java` | Stand-in lock screen. `showWhenLocked`, swipe-up to dismiss. |
| `LockService.java` | Foreground service; stages the lock screen on `ACTION_SCREEN_OFF` (fallback on `SCREEN_ON` when none is alive) so it never launches over the camera gesture. Needs `SYSTEM_ALERT_WINDOW`. |
| `Gestures.java` | The lock screen's flick actions: the option list shared with the app's dropdowns, `perform()`, and a torch helper that tracks the real flash state. |
| `WatchView.java` | The OLD flat 2D Canvas dial. Used by screensaver + wallpaper only. |
| `tools/watch3d.html` | The 3D watch source (also a standalone browser artifact). |
| `tools/make-lock-asset.py` | Transforms `watch3d.html` → `app/src/main/assets/lock.html`: bundles three.js, adds the viewport meta, strips page chrome, wraps the render loop with `applyCamera()` (the inner function must not be named `loop`, see item 1), adds the `window.__lock` bridge (`setQuat/setZoom/nudge/setCard/setBattery/setPlacement/testTurn/setDebug`). **Edit the watch here, then regenerate — never hand-edit `lock.html`.** |
| `tools/page-eval.py` | Evaluates a JavaScript expression in the live WebView pages over DevTools. Finds the phone by mDNS and checks its build id (or takes `MINIWATCH_DEVICE`). The verification channel that did not exist before v0.0.28. |
| `tools/probe-carry.js` | Synthetic-pointer test of the ring carry, for `page-eval.py`. Template for testing any one-finger gesture without the screen. |
| `tools/prefs-set.py` | Set or delete Miniwatch preferences on the phone safely (`key=value`, `key=-`). The only sanctioned way to edit them from adb. |
| `tools/battery-report.py` | The phone's own battery accounting since the last charge with Miniwatch's line isolated; `--sample N` measures live drain from the charge counter. |

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
