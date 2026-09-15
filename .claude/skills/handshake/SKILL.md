---
name: handshake
description: Resume the Miniwatch project in a fresh session. Reads the knowledge base and handoff, checks the phone and the last build, and reports where things stand before touching anything. Use when the user types /handshake, says "handshake", "pick up where we left off", or "resume Miniwatch".
argument-hint: [topic, e.g. nft, lockscreen, sharing]
---

# Handshake

You are resuming the Miniwatch project. Do not change anything during the handshake.

## 1. Read, in this order

1. `docs/KB.md` in full. It is the map: what the app is, the rules, the roadmap, the open decisions.
2. The first 40 lines of `docs/HANDOFF.md`, and its "How to build, deploy, verify" section.
3. The last 6 rows of the table in `docs/CORRECTIONS.md` and the last 3 rows of `artifacts/BUILDS.md`.
4. `git log --oneline -8` and `git status --short`.
5. The memory notes the memory index points to for this project.

If `$ARGUMENTS` names a topic (nft, lockscreen, sharing, 2d, gestures), also read the part of
`docs/KB.md` and `docs/HANDOFF.md` about it, and focus the report on it.

## 2. Check the phone

```bash
ADB="$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" mdns services            # the _adb-tls-connect._tcp line is the phone; the emulator is not
"$ADB" connect <address>
"$ADB" -s <address> shell getprop ro.build.display.id    # must be 2026091001
"$ADB" -s <address> shell dumpsys package com.miniscreen.miniwatch | grep -m1 versionName
```

Only the GrapheneOS phone, never the emulator. If nothing is advertising, say so and ask the
user to switch Wireless debugging on; do not scan the network for it.

## 3. Report, under 200 words

- Miniwatch in one line.
- Version in the repo and on the phone, and whether they match.
- The last three things done, from the ledger and the log.
- The roadmap's next item and what it needs from the user.
- The open decisions.
- Anything unverified or broken that a fresh session should know.

Then ask what to do. Keep the rules of the house from `docs/KB.md` for the rest of the session.
