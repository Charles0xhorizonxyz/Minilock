"""What Miniwatch has cost the battery, from the phone's own accounting.

    python tools/battery-report.py            # since the last full charge
    python tools/battery-report.py --sample 600   # also: charge-counter drain over N seconds, now

Reads `dumpsys battery` (level, charge counter, plugged) and `dumpsys batterystats` (screen-on
time since the last charge, the estimated power use per app, and Miniwatch's CPU, sensor and
foreground time). Nothing is reset, so the phone's own Battery settings page stays intact.
With --sample it also measures the live drain: charge counter now, wait, charge counter again,
which is exact and independent of the estimates. Do that unplugged, with the watch on screen.
Finds the phone the same way page-eval.py does.
"""
import os
import re
import subprocess
import sys
import time

ADB = os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe")
PKG = "com.miniscreen.miniwatch"
CAPACITY_MAH = 4355          # Pixel 7


def find_device():
    fixed = os.environ.get("MINIWATCH_DEVICE")
    if fixed:
        return fixed
    out = subprocess.run([ADB, "mdns", "services"], capture_output=True, text=True, timeout=20).stdout
    for line in out.splitlines():
        if "_adb-tls-connect._tcp" in line:
            addr = line.split()[-1]
            subprocess.run([ADB, "connect", addr], capture_output=True, text=True, timeout=25)
            ident = subprocess.run([ADB, "-s", addr, "shell", "getprop", "ro.build.display.id"],
                                   capture_output=True, text=True).stdout.strip()
            if ident == "2026091001":
                return addr
    sys.exit("the GrapheneOS phone is not advertising wireless debugging on this network")


DEVICE = find_device()


def sh(*args):
    return subprocess.run([ADB, "-s", DEVICE, "shell", *args], capture_output=True, text=True).stdout


def battery():
    out = sh("dumpsys", "battery")
    get = lambda k: re.search(r"%s: (-?\d+)" % k, out)
    level = int(get("level").group(1))
    counter = int(get("Charge counter").group(1)) if get("Charge counter") else None   # microamp-hours
    plugged = any(re.search(r"%s powered: true" % s, out) for s in ("AC", "USB", "Wireless", "Dock"))
    return level, counter, plugged


def since_charge():
    m = re.search(r"uid:(\d+)", sh("cmd", "package", "list", "packages", "-U", PKG))
    uid = m.group(1) if m else "?"
    tag = "u0a%d" % (int(uid) - 10000) if uid != "?" else "?"
    stats = sh("dumpsys", "batterystats", "--charged")
    lines = stats.splitlines()
    screen = next((l.strip() for l in lines if l.strip().startswith("Screen on:")), "Screen on: ?")
    use = []
    grab = False
    for l in lines:
        if "Estimated power use" in l:
            grab = True
            continue
        if grab:
            t = l.strip()
            if t.startswith(("Capacity:", "screen:", "GPU:", "wakelock:", "mobile_radio:", "wifi:")) and len(use) < 8:
                use.append("   " + t)                       # the global picture
            if t.startswith("UID " + tag + ":"):
                use.append("   MINIWATCH  " + t)             # our line: total, fg (activity on screen), fgs (service)
            if t.startswith("All partial wake locks") or "Per-app mobile" in t:
                break
    app = sh("dumpsys", "batterystats", "--charged", PKG)
    keep = [l.strip() for l in app.splitlines()
            if re.search(r"Total cpu time|Foreground activities|Foreground services|Sensor|Wake lock|"
                         r"Total running:|Total foreground", l)]
    return uid, screen, use, keep[:14]


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    level, counter, plugged = battery()
    print(f"phone {DEVICE}   battery {level}%   charge counter {counter} uAh   plugged: {plugged}")
    uid, screen, use, keep = since_charge()
    print(f"\nSince the last charge   ({screen})")
    print(f"  Estimated power use in mAh. 'fg' is with the watch on screen, 'fgs' the resident service:")
    for l in use:
        print("   " + l)
    print("  Miniwatch detail:")
    for l in keep:
        print("   " + l)
    if "--sample" in sys.argv:
        secs = int(sys.argv[sys.argv.index("--sample") + 1])
        if plugged:
            print("\nUnplug the phone for a drain sample; the counter goes up while charging.")
            return
        if counter is None:
            print("\nThis phone does not report a charge counter; only the level is available.")
            return
        print(f"\nDrain sample: {secs} s from now. Keep the watch on screen and the phone unplugged.")
        t0 = time.time()
        time.sleep(secs)
        level2, counter2, _ = battery()
        elapsed = time.time() - t0
        used_mah = (counter - counter2) / 1000.0
        ma = used_mah / (elapsed / 3600.0)
        hours = CAPACITY_MAH / ma if ma > 0 else float("inf")
        print(f"  {used_mah:.1f} mAh in {elapsed/60:.1f} min  =  {ma:.0f} mA average"
              f"  =  {100*ma/CAPACITY_MAH:.1f}% of the battery per hour"
              f"  =  a full battery in {hours:.1f} h at this rate   (level {level}% -> {level2}%)")


if __name__ == "__main__":
    main()
