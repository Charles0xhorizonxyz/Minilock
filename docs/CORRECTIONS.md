# Corrections and where they stand

Every correction and request since the first version, with an honest status. Updated 2026-09-14, app at **v0.0.58**.

Status key: **Done** · **Prototype only** — built in `tools/`, not in the Android app · **Partial** · **Open** · **Check** — I believe it is fixed but you have not confirmed it.

---

## The three that are actually open

| # | What you asked | Status |
|---|---|---|
| 28 | Watch edges cut off | **Check** — the real cause was found in v0.0.28: the fitted camera never applied. See below. |
| 29 | Pinch zoom with two fingers (the app screen) | **Check** — v0.0.26 claims every DOWN; unverified, and one gap remains. See 29. |
| 17 | A real customisable lock screen (custom GrapheneOS) | **Open** — planned and documented, nothing built |

### The camera pass that never ran (found in v0.0.28)

Since v0.0.05 the generator has wrapped the scene's render loop so a camera pass could run before
every frame. It wrote `var loop = function loop(now){...}`. In a named function expression the
name is bound to the function itself, so the loop's own `requestAnimationFrame(loop)` kept
re-scheduling the raw loop, and the wrapper — the only caller of `applyCamera()` — ran exactly
once per page load, before the first sensor sample and before the camera fit was computed.

Everything `applyCamera` does was therefore dead on every build from v0.0.05 to v0.0.27: the
fitted camera distance (#28), pinch zoom on the watch (#29b, #37, #44), placement (#42), the
card offset (#32), and the gyroscope orbit (#22, #30, #41, #46). The "moves a bit and goes back
to centre" you saw was the bow spring, which lives in `setQuat` and did run; the orbit never did.
The screenshots the earlier sessions used as evidence showed the un-fitted camera the whole time.
Fixed in v0.0.28 by naming the inner function `rawLoop`.

### 28 — Watch edges cut off

You raised this first, and you were right that it kept not being fixed:

1. **Original cause:** the camera sat at a fixed distance. On a 9:20 screen that gives 1.91 world units of visible width, and the case is 2.0 across, so the edges *always* clipped. Fixed in v0.0.06 by fitting the camera to whichever axis is tighter.
2. **Broken again in v0.0.10.** I added a look-at offset so the watch would sit above the card, but computed it from the *fitted* distance. Zooming in kept a far-view offset and threw the watch clean off the top of the frame — which is what you saw.
3. **Fixed in v0.0.13:** the offset is now derived from the current camera distance, so it stays correct at any zoom.
4. **None of that ever rendered.** See the section above: the camera stayed at the original fixed 9.2, which is exactly the always-clipping case from item 1. v0.0.28 is the first build in which the fit applies. **Check** it on the preview after tapping "Reset watch size and position" (see #48).

### 29 — Zoom

I solved the wrong problem for several versions. You meant **the app screen** — what opens when you tap Minilock — and I kept making the *watch's 3D camera* zoom instead.

**v0.0.15** wraps the whole screen in a `ZoomLayout`: two fingers scale and pan everything (text, toggles, watch), one finger passes straight through so switches and scrolling behave. **v0.0.16** turns off `ScaleGestureDetector`'s quick-scale, which is enabled by default and let a one-finger double-tap-drag zoom by accident.

Verified on device: the zoom transform fires and renders, and a single tap still flips a toggle. Not verified: an actual two-finger pinch, which adb cannot simulate.

### 29b — Pinch on the watch (earlier attempt)

Added in v0.0.06 in the page's own touch handlers, and I reported it as working. It was not. Inside a `ScrollView` the parent claims a two-finger gesture as a scroll before the page ever sees it. **v0.0.14** reads the pinch natively with `ScaleGestureDetector` and pushes the result into the scene, so the camera moves and the watch re-renders sharp rather than the page being scaled and blurred.

---

## Everything else, in order

| # | What you asked | Status |
|---|---|---|
| 1 | Continue the watch screensaver work | Done |
| 2 | Show it in a browser before writing Java | Done — `tools/dial-bench.html` |
| 3 | Phone info as complications: weather, battery, alarms, notifications | **Prototype only** — `tools/complications.html` |
| 4 | Drop ATELIER, keep MINISCREEN | Done |
| 5 | Make it 3D, flippable, settings on the back | Done |
| 6 | A pocket watch with the crown on top | Done |
| 7 | The rotation transition was bad | Done — rebuilt in WebGL, turn driven by angular velocity |
| 8 | Transparent from the side; make it solid gold | Done — the lathe was an open shell, now a closed solid |
| 9 | Give it a realistic feel, especially from the side | Done — polished bezel, satin band, polished back, case seam |
| 10 | 24k, Rose and Purple gold | Done |
| 11 | Would Blender help? | Answered — yes for baked maps; not installed here |
| 12 | Push to the phone as V0.0.01 | Done |
| 13 | Keep a copy of every build | Done — `artifacts/` + `BUILDS.md`, nothing overwritten |
| 14 | Install the latest and activate it | Done |
| 15 | Only the GrapheneOS phone, never the emulator | Done — every adb call is pinned with `-s` |
| 16 | Install the 3D watch as the lock screen | **Partial** — stand-in lock screen only; Android has no real replacement API |
| 18 | Off / dimmed / always-on choice | **Prototype only** — `tools/keyguard.html` |
| 19 | Would CalyxOS allow it? | Answered — no, same restriction |
| 20 | Rent a Vultr box | **Open** — brief written, nothing provisioned |
| 21 | Public repo `Minilock` | Done |
| 22 | Gyroscope, watch holds still as the phone moves | Done |
| 23 | Hours needed and billing risk | Answered — 5–8 h first build, destroy ≠ power off |
| 24 | Fake lock screen with the 3D watch | Done — v0.0.03 |
| 25 | "Minilock keeps stopping" | Done — `getInsetsController()` before `setContentView` returns null |
| 26 | Wrong watch — wanted the 3D one | Done — all three surfaces now |
| 27 | Fully drop Atelier | Done — package, classes, label, prefs, wordmark |
| 30 | Watch should move as the phone moves | Done |
| 31 | Remove "Swipe up to unlock" | Done |
| 32 | Make the text under the watch optional | Done — a toggle |
| 33 | Make the app zoomable | Done — v0.0.14 |
| 34 | Remove the scrollbar | Done — v0.0.12 |
| 35 | "I see v11 but nothing changed" | Done — version now shown in the masthead |
| 36 | Real battery level on a counter | **Done and verified** — dial reads 84%, phone reads 84% |
| 37 | Lock screen watch zoomable, movable up/down | Done — pinch, plus two-finger drag to position. **Unverified**: adb cannot do multi-touch |
| 38 | Gyroscope should give it weight, not just parallax | Done — lateral motion drives the bow spring, so it rocks and settles. **Unverified**: needs real movement |
| 39 | Zoomed app cannot pan left/right | Done — v0.0.19 inverted the nesting; ScrollView outside, zoom inside |
| 40 | Zoomed app could not reach the bottom, and scrolled slowly | Done — same nesting fix. Scrolling was being divided by the zoom factor |
| 41 | Gyroscope only nudges and re-centres; wanted full 360 | Done — v0.0.20. Clamp and re-baseline removed, azimuth unwrapped and accumulated. **Unverified**: needs real movement |
| 42 | Size and place the watch freely rather than auto-fit | Done — v0.0.20/21. Pinch sizes, two-finger drag places, and it is remembered. **Unverified** |
| 43 | Zooming in the app was lost | **Done and confirmed by you** — v0.0.22 |
| 44 | Same zoom behaviour on the lock screen | Done — v0.0.23. Two pinch handlers were fighting; the native one is now the only controller. **Unverified** |
| 45 | Zoom only worked over parts of the app; no sideways pan | Done — v0.0.25. The ScrollView was claiming the gesture wherever it decided first |
| 46 | Gyroscope still minimal and recentres | **Superseded** — v0.0.25 blamed gimbal lock. The orbit code had never run at all (see the camera pass above), and the quaternion axis map it introduced turned yaw into roll. |
| 47 | Try the full gyroscope | **Check** — v0.0.28. Two bugs fixed: the camera pass never ran, and the axis map `(x,z,-y)` conjugated the rotation so a turn about the screen's vertical axis became a roll about the viewing axis. Verified from adb with pretend turns (`__lock.testTurn`): yaw shows the case from the side, pitch goes over the top, roll spins it flat. **Unverified:** that the real sensor's sign convention matches — needs your hands. A readout in the fullscreen preview shows what the sensor delivers. |
| 48 | Watch stuck in the bottom-right corner (v0.0.28) | **Cleared by you** — a placement saved by accident while nothing rendered applied once the camera pass ran. The stored placement has changed several times since, by your own pinches, so it is yours now. |
| 49 | Background colours on a scale from white to black | **Done and verified** — v0.0.29. A slider in the app, white on the left, black on the right; it repaints the studio behind the watch on every surface and is remembered. Verified on the phone: the slider wrote the value, the app page and the stand-in lock screen took the light grey, and dragging it back restored the dark studio. The text under the watch switches to dark ink on light backgrounds (not checked: your card is off). |
| 50 | Grab it by the ring and move it anywhere on the screen | **Done, verified in the page** — v0.0.29. One finger on the ring carries the watch; one finger on the dial still turns it over; the placement is saved when the finger lifts. Verified with synthetic pointer events in the live page: a 100 px drag from the ring moved the placement by exactly the expected amount, a drag on the dial did not. Not verified with a real finger, and not on the in-app hero, where a vertical drag may still be taken by the page scroll. Placement is now a screen offset, so a parked watch stays put while the phone turns. |
| 51 | Remove "Set as screensaver" from the back of the watch | **Done and verified** — v0.0.30. Markup and wiring both gone; the app keeps the real one. Seen on the phone with the watch turned over. |
| 52 | The watch colour and the background reset on unlock | **Done and verified for the colour; could not reproduce for the background** — v0.0.30. The colour (and the movement toggles and the counter) are chosen on the plate on the caseback, and nothing had ever saved that plate: it lived only in the page, so every new lock screen page started over. The page now hands the plate to the app through a `minilock` JavaScript interface on every change, and the app restores it on load. Verified: rose gold chosen in the app, sleep, wake, and the fresh lock screen page came back rose. The background was already restored from prefs on every load, and it also survived the same sleep-and-wake at a light grey. If it still resets for you, tell me the exact steps. |
| 53 | Background from white to black, through a rainbow | **Done and verified** — v0.0.31. The slider's track shows white, red, yellow, green, cyan, blue, violet, black, and the page maps the same value to the same colour, painting the studio backdrop in it with the usual vignette. Seen on the phone: the track, a mint studio at the cyan stop, a pale pink one near the white end; green, violet and yellow read back from the page as exactly the predicted colours. Left at black. |
| 54 | Mechanical controls on the back, following the watch perfectly | **Done and verified** — v0.0.32–v0.0.34. The HTML plate was a flat overlay scaled and skewed over the canvas, so it could only approximate the caseback and drifted as soon as the camera orbited. The controls are now painted into the caseback's own texture in the dial's language: three turned alloy medallions with the chosen one ringed, six knurled slide levers in cut slots, a three-detent selector for the counter at six, engraved captions. They are part of the metal, so they follow every turn and tilt exactly. A tap is ray-cast onto the caseback in 3D and mapped to the control under it. Verified on the phone: taps flipped AMBIENT, chose rose gold and MOON, the caseback repainted each time, and each change was saved. Two things this uncovered: the engraved caseback had been buried 0.006 inside the polished back since the 3D watch was built (v0.0.33 moves it proud), and the lower engraving on the band was drawn upside down (v0.0.34). |
| 55 | Put the background slider and the reset right under the watch in the app | **Done and verified** — v0.0.35. Both now sit directly below the watch, above EDITION 01 and the toggles. |
| 56 | Double-tap power should still open the camera | **Done and verified** — v0.0.36 (shipped in v0.0.38). The stand-in lock screen was launched on SCREEN_ON, which raced the system gesture: the first press woke the phone and started the watch, the second started the camera, and whichever came up last covered the other. The watch is now staged on SCREEN_OFF, so it is already there when the screen wakes and nothing launches after the camera; `turnScreenOn` is gone so staging cannot wake the phone. Verified on the phone: the watch exists while the phone dozes and the phone stays asleep; on wake it is in front at once; a double-press brings the GrapheneOS camera to the front both from the watch and from sleep; back returns to the watch. |
| 57 | Make "Set as screensaver" a toggle like the others, under "Text under the watch" | **Done and verified** — v0.0.37. An app cannot set Android's screensaver itself, so the switch shows the real state (read from the system's screensaver settings: enabled, and Minilock chosen) and opens the phone's screensaver page to change it; it refreshes when you come back. Verified: it reads ON, matching the phone, and tapping it opened the system page. |
| 58 | Remove the text about the screensaver needing 3D / a WebView | **Done** — v0.0.37. |
| 59 | Remove "EDITION 01" and "Pinch to size it…" | **Done** — v0.0.37. |
| 60 | Replace the reset text with a plain "Reset to default" button | **Done and verified** — v0.0.38. An outlined button under the slider. It clears the placement and puts the background back to the dark studio, moving the slider with it, then reloads the watch. Verified: prefs cleared and background 0 after a tap. |
| 61 | Put the Preview button above Ambient mode | **Done and verified** — v0.0.38. |
| 62 | Make the whole Screensaver row open the phone's screensaver settings | **Done and verified** — v0.0.38. Tapping the label opened Android's screensaver page. |
| 63 | The background colours look artificial; make them natural | **Done and verified** — v0.0.39. The pure spectrum hues are replaced by eight seamless-paper tones in the same order (ivory, rose clay, ochre, sage, teal grey, slate blue, plum, charcoal), interpolated on the page and painted on the slider's track alike. Seen on the phone at sage, rose clay and ivory. v0.0.40 also makes the studio wall follow the camera, so a tilt no longer shows its edge cutting across the corner, which the light tones had made obvious. |
| 64 | Make "Allow display over other apps" a toggle like the others | **Done and verified** — v0.0.41. The switch mirrors the real permission state and the whole row opens the phone's permission page, the same way the Screensaver row does; both refresh when you come back. Verified: reads ON, matching the phone, and tapping the row opened the system page. |
| 65 | "Reset to default" should reset only the watch position, not the colours | **Done and verified** — v0.0.42. The button clears the saved size and position and reloads the watch; the background is left alone. Verified on the phone: placement cleared, colour untouched. |
| 66 | Unlock by spinning the watch right to left; settings only left to right | **Done and verified** — v0.0.42, made configurable in v0.0.44 (see 67). A decisive right-to-left flick of the dial from the front unlocks; left-to-right turns the watch over to the settings. Never when carrying by the ring, and on the caseback a leftward flick only turns it back. Verified on the phone, with the page reporting which face was showing before each flick. Swipe-up still works too. |
| 67 | Two turns left to right open the camera; make both gestures the user's choice, with a dropdown of everything available | **Done and verified** — v0.0.44, corrected in v0.0.45. Two rows in the app under "Stand-in lock screen": "Flick right to left" (default Unlock) and "Spin left to right, two turns" (default Open the camera), each a dropdown of Nothing, Unlock, Open the camera, Torch on or off, Open Minilock, Open the alarms. v0.0.44 read the second gesture as two separate flicks; v0.0.45 makes it one action: a hard flick that carries the watch through two full turns (it fires once the spin crosses the barrier before the second full turn, and disarms if the watch comes to rest short of it). Verified on the phone: a gentle flick only turns the watch over, a hard one opened the GrapheneOS camera. Torch, Open Minilock and Open the alarms are wired the same way but were not exercised. How hard a flick has to be is set by the watch physics; say if two turns are hard to reach with a real finger. |
| 68 | (found while testing) Nothing was staged after an app update until the app was opened once | **Done and verified** — v0.0.44. The boot receiver also handles the package-replaced broadcast and restarts the lock service. Verified: the watch was staged straight after an install with no app launch. |
| 69 | The design that flashes briefly after "Reset to default" is the one I prefer; make it an option | **Done and verified** — v0.0.46. The flash was the page's own factory look, shown for the instant between the watch loading and the saved choices landing on top of it: 24k gold, the bright dial with its seconds hand and all complications, the dark studio, and the text under the watch. There is now a "Design" dropdown at the top of the settings list: Factory shows the watch as designed on every surface; Custom is the saved caseback, background and text choices. Changing any of those makes it Custom again; the custom settings are kept while Factory is showing. Size and position are separate from the design. Verified on the phone: picking Factory reloaded the watch in the factory look. Left on Factory, since that is the one preferred. Note the text lines under the watch (next event, alerts, alarm) are still the prototype's demo data, not the phone's. |
| 70 | Factory came with the text underneath; it should follow the text switch | **Done and verified** — v0.0.47. The text under the watch follows its own switch in both designs; Factory is only the gold, the dial and the studio, and the text switch no longer flips the design to Custom. Verified on the phone: Factory with the switch off shows no text. |
| 71 | Align all dropdown menus to the right | **Done and verified** — v0.0.48/49. The selected text ends flush with the switches' right edge, and since v0.0.49 the list opens as exactly the dropdown's own box (v0.0.48 had it pushed to the screen edge and clipped). Verified: the dropdown, the open list and the switches all end at the same pixel. |
| 72 | Text size with a + and - on a ten-step ladder | **Done and verified** — v0.0.50. A "Text size" row at the top of the settings list: A- and A+ either side of a ten-rung ladder, 7.5 percent per step from 0.7 to 1.375 of the design size, applied live to every title, description, button and dropdown on the app screen, and remembered. Read as the app's own text; say if the text under the watch was meant instead. Verified on the phone: the step changes with each tap and the whole screen re-sizes live. The A- and A+ labels grow with the text too. |
| 73 | Battery: is it minimal? Measure for now, decide in a couple of days | **In progress** — it is not minimal as designed: the stand-in lock screen keeps the screen on indefinitely; the 3D scene renders at full frame rate with shadows whether or not anything moves; the Factory design repaints the dial texture 30 times a second (Custom with ambient on, once a second); the gyroscope streams at 50 Hz while a watch is on screen. `tools/battery-report.py` reads the phone's own accounting since the last charge and, with `--sample N`, measures the live drain from the charge counter over N seconds. First numbers from the phone's own accounting since its last full charge: Minilock 1657 mAh of 6130 drained in that period (27%); 1028 mAh over 7h31m with the watch on screen, about 137 mA on top of the display; 232 mAh over 12h26m of the resident service with nothing on screen, about 19 mA, which is the staging page load on every screen-off plus the resident WebView. A controlled ten-minute sample was not taken because the phone was in use. The stay/fade clock (#74), the flat dial and the gyroscope switch (#75) are now the tools for cutting both; the user chose to observe for a couple of days before deciding on a renderer rest mode or a sweep cap. |
| 74 | A dropdown for how long the watch shows before the lock screen goes dark, a fade to black first, and a dropdown for how long the fade takes | **Done and verified** — v0.0.51. Two dropdowns under "Stand-in lock screen": "Watch stays on for" (15 s to 10 min, or Always; default 1 minute) and "Fade to black over" (1 to 30 s; default 5 s). The watch shows in full until the time is up, then a black veil fades in over the chosen time; after that the page stops rendering, the gyroscope stops, the backlight drops to its minimum and the keep-screen-on hold is released, so the phone's own screen timeout turns the display off from a black screen. An app cannot switch the display off itself without device-admin rights; say if you want that (one system prompt to grant). Any touch lifts the veil and restarts the clock, and that touch does nothing else. Verified on the phone with 15 s and 3 s: the window manager's keep-screen-on hold was present at 2 s and gone at 20 s, the veil was black, a tap lifted it and re-took the hold. The display itself stayed on because the phone's own screen timeout is longer; set that shorter in Android's Display settings if the screen should go off soon after the fade. |
| 75 | A toggle for 3D or 2D, and one for the gyroscope | **Done and verified** — v0.0.52, corrected three times as the meaning of 2D was pinned down: not the old flat dial (v0.0.52), not a frozen head-on camera (v0.0.53), not a clamped orbit against the wall (v0.0.54). Since v0.0.56 "3D watch" off means: the camera never moves, and the watch hangs from its ring and swings like a pendulum toward gravity as the phone tilts, in the plane of the screen, with any motion giving it a push; 3D keeps the full orbit. The bow's small rock spring becomes a softer, wider pendulum spring toward a target read from the absolute orientation, so a phone lying flat has no preferred direction and an upright one swings the watch to where down is. Live on every surface. "Gyroscope" off never starts the sensor on any surface. Verified on the phone: stood up and rolled 50 degrees each way, the watch swung out to either side of its ring with the camera and wall still; in 3D the same roll turns the whole view. At large tilts the swing carries the body out of the frame; say if it should be damped. |
| 77 | The 2D pendulum swings the wrong way: it rises where it should fall | **Done** — v0.0.57. The sign of the gravity target was inverted; the body now swings toward gravity. Verified from adb only as a mirror of the previous behaviour (stood up and rolled the same way, the swing now goes to the other side); the direction against real gravity is yours to confirm. |
| 78 | Wake the screen on movement, no power button | **Done, arming verified** — v0.0.57. A "Wake on pickup" switch under "Stand-in lock screen" (default on). The lock service arms the phone's wake-up pick-up gesture sensor, the same low-power gesture the system's lift-to-wake uses, every time the screen goes off, and when it fires it turns the screen on with a short wake lock; the staged watch is already there. Verified on the phone that the sensor is registered by the service the moment the screen goes off; the pickup itself cannot be faked from adb, so the wake is yours to confirm. Android's own "Lift to check phone" gesture (Settings, System, Gestures) does the same through the system and can be used instead or as well. |
| 79 | 2D works in the preview but not so well on the lock screen | **Done; fresh-page path confirmed by you, stale-page path by code** — v0.0.58. The preview opens a fresh page every time and so gets the current settings; the lock screen is staged in the background, stays alive across wakes, and Home leaves it alive, so a mode changed in the app never reached its page until it was recreated, which is why a screen-off after an unlock made it work. Every surface now re-applies the current settings (design, background, plate, text, 3D or 2D) each time it comes to the front, not only when its page loads. The stale-instance run could not be verified from adb because the phone was being used at the same time. |
| 76 | (found while testing) A freshly loaded watch had no gyroscope baseline until the phone moved | **Done** — v0.0.55. The first sensor sample usually arrived before the page existed and was dropped, and with the phone perfectly still nothing followed. The latest sample is now re-sent once the page is ready. |

---

## The gap worth naming

**Most of the design work is not in the Android app.** The complications, the off/dimmed/always-on modes and the seven craft refinements all live in `tools/*.html` and were never ported. The app shows the 3D pocket watch and four settings; that is all.

Two more things that are true and easy to lose track of:

- **The screensaver and live wallpaper still draw the old flat Canvas dial.** A `DreamService` and a `WallpaperService` cannot host a WebView, so they cannot show the 3D watch without an OpenGL ES or Filament port.
- **The stand-in lock screen is not a lock screen.** With your real lock disabled the phone is not locked, and Home escapes it. Only a custom OS build makes the watch the actual keyguard clock.

## Where I went wrong

Worth recording so it does not repeat:

1. **I reported fixes as working without being able to verify them.** Pinch zoom is the clearest case: I could not test a two-finger gesture over adb, said it was added, and it was not functioning. I should have said "added but unverified".
2. **I fixed the framing, then broke it again** with the card offset, and did not re-check the case I had originally fixed.
3. **Renaming the package made the app look unchanged** — new name, new icon, dead home-screen shortcut — and I gave you no way to tell which build was running until v0.0.11.
4. **I cleared the app's data** while diagnosing, which reset your toggles without warning you first.
5. **Nine versions of camera work were verified against a camera pass that never ran.** The loop wrapper was written once in v0.0.05 and never checked again; every later "fixed" for framing, zoom, placement and the gyroscope was built on it. When a fix does not change what you see, doubt the plumbing before the parameter.
