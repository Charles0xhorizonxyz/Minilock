# Miniwatch knowledge base

Read this first in a fresh session. It is the map; the detail lives in the documents it points to.
Written 2026-09-15 at v0.0.63, current at v0.0.66. Keep it current: when something here changes, change it here too.

## What Miniwatch is

An original luxury pocket watch for Android, drawn as a WebGL scene in a WebView, with no network
access and no accounts. It shows as a stand-in lock screen, a fullscreen preview, the hero of the
app, and, as a flat Canvas dial, the screensaver and live wallpaper. The real goal is the dial as
the actual lock-screen clock on a custom GrapheneOS build. Repo folder `Miniscreen`, app
`Miniwatch`, package `com.miniscreen.miniwatch`, the mark on the dial `MINISCREEN`. Old names
Minilock and Atelier are retired; the GitHub repository is still called Minilock.

## Where things stand

- v0.0.66 on the phone: the text under the watch is real (calendar, notifications, alarm) and inked against the chosen background; the weather counter is still the prototype's. The 2D "held by the ring" watch goes the whole way round; only a dial
  gesture set to Unlock leaves the stand-in lock screen, and one of the two gestures is always
  Unlock; the app was renamed from Minilock in v0.0.62 with settings carried over.
- The phone is a GrapheneOS Pixel 7 (`panther`, `ro.build.display.id` 2026091001) on wireless
  adb. Its address moves, even within a day; discover it with `adb mdns services`. When it is not
  advertising, Wireless debugging is off on the phone: ask, do not scan.
- Every build is archived in `artifacts/` and logged in `artifacts/BUILDS.md`; every request and
  its honest status is a row in `docs/CORRECTIONS.md` (85 rows so far).

## Rules of the house

1. Deploy only to the GrapheneOS phone, always `adb -s <address>`; never the emulator.
2. Keep every build: bump `versionName`, build, copy to `artifacts/`, add the row. Never overwrite.
3. Commit and push after each build. Say "unverified" when a thing was not seen working.
4. Edit preferences on the phone only with `tools/prefs-set.py`; drive the live page with
   `tools/page-eval.py`; set `MINIWATCH_DEVICE` to skip discovery; put `MSYS_NO_PATHCONV=1` in
   front of adb commands that carry a device path.
5. `app/src/main/assets/lock.html` is generated from `tools/watch3d.html` by
   `tools/make-lock-asset.py`; edit the generator, never the asset.

## Roadmap, in order

### 1. The proper lock screen (next)

A GrapheneOS build for `panther` with the dial as a clock provider plugin, plus the small
SystemUI change that lets it load. Plan and costs: `docs/vultr-provisioning.md`. Needs from the
user: rent the box with the prompt in that document and hand over the IP; the SSH key it names
is on this PC. First session 5 to 8 hours, about $5 to $8; later sessions restore a snapshot.
Consequences: a full wipe, the phone re-locked to our own signing keys, updates only from our
builds. The plugin draws the flat Canvas dial (`WatchView`), not the WebGL watch. The plugin
code does not exist yet.

### 2. Sharing the app

A release-signed APK on GitHub Releases, updated through Obtainium; later F-Droid or Accrescent;
Google Play is a poor fit. Needs: a licence (none chosen), a release key backed up by the user,
an install page that says plainly the stand-in lock screen means the real lock is off. The
custom-OS lock screen cannot be shared as an app.

### 3. NFT watch collection on Minima (after 1)

Decided 2026-09-15, to build after the proper lock screen. Each watch is a Minima token minted
with `tokencreate name:{...} amount:1 decimals:0 signtoken:<our key> webvalidate:<hosted txt>`,
so `tokenvalidate` proves it is ours. On-chain images are tiny: the high-resolution dial art
ships in the app or at a link keyed by token id; the NFT is the key. Routes for an owner, in the
order to build them:

1. Pick an image: "Use an image as the dial" in Miniwatch through the system picker, painted as
   the dial face with the hands on top, on every surface. No permissions, no verification.
   About a day of work.
2. Ask the node: the owner runs `rpc enable:true password:...` in Minima once and pastes the
   password; Miniwatch asks `balance tokenid:` on 127.0.0.1:9005 for the collection's tokens,
   shows only those, re-checks on each wake. Verified, but it costs the INTERNET permission.
3. A Miniwatch MiniDapp inside Minima for mint or buy; it cannot launch other apps, so loading
   still ends in the picker.

Unverified: the on-chain size limit of a token's name JSON. Mint a test token to measure it
before designing art around it. Sources are the Minima docs on GitHub (`minima-global/docs`):
terminal-commands, tokens, minidapp-mdsjs, minidapp-permissions, run-a-node/android.

## Open decisions for the user

- In 2D at 90 degrees the ring pokes past the frame; fitting it always would shrink the watch
  about 30 percent. Left as is.
- Rename the GitHub repository from Minilock to Miniwatch (a click on github.com).
- Blocking Home on the stand-in lock screen would need Miniwatch as device owner over adb; not
  done, offered.

## The documents

| File | What it is |
|---|---|
| `docs/KB.md` | This map. |
| `docs/HANDOFF.md` | The deep technical handoff: every mechanism, every trap, how to build, deploy and verify. |
| `docs/CORRECTIONS.md` | The ledger of every request and its status. |
| `artifacts/BUILDS.md` | Every build, with size, hash and what changed. |
| `docs/vultr-provisioning.md` | The build-box plan for the custom OS. |
| `tools/` | Prototypes (`*.html`), the asset generator, and the adb tools. |
| `.claude/skills/handshake/SKILL.md` | The handshake: type `/handshake` in a new session. |

## How to resume

Type `/handshake` in a new session. It reads this file and the handoff, checks the phone and the
last build, and reports where things stand before touching anything. `/handshake nft` or
`/handshake lockscreen` focuses the report on that roadmap item.
