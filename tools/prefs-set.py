"""Set, or delete, entries in Minilock's preferences on the phone, keeping the file well-formed.

    python tools/prefs-set.py gyro=false threeD=false     # booleans
    python tools/prefs-set.py lock_stay=15 lock_fade=3     # ints
    python tools/prefs-set.py design=factory               # strings
    python tools/prefs-set.py gyro=- threeD=-              # delete (back to the default)
    python tools/prefs-set.py                              # just print the file

Force-stops the app first (SharedPreferences are cached in the process), pulls the file, edits
it, pushes it back through /data/local/tmp and run-as, and prints the result. Types are
inferred: true/false -> boolean, digits -> int, anything else -> string. Finds the phone the
same way page-eval.py does. Never edit the file with sed over adb shell: a lost closing tag
went unnoticed for hours.
"""
import os
import re
import subprocess
import sys

ADB = os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe")
PKG = "com.miniscreen.minilock"
FILE = "shared_prefs/minilock.xml"


def find_device():
    fixed = os.environ.get("MINILOCK_DEVICE")
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
    return subprocess.run([ADB, "-s", DEVICE, *args], capture_output=True, text=True)


def element(key, value):
    if value in ("true", "false"):
        return '    <boolean name="%s" value="%s" />' % (key, value)
    if re.fullmatch(r"-?\d+", value):
        return '    <int name="%s" value="%s" />' % (key, value)
    return '    <string name="%s">%s</string>' % (key, value.replace("&", "&amp;").replace('"', "&quot;"))


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    changes = [a.split("=", 1) for a in sys.argv[1:] if "=" in a]
    raw = sh("shell", "run-as", PKG, "cat", FILE).stdout.replace("\r\n", "\n")
    lines = [l for l in raw.split("\n") if l.strip() and l.strip() not in ("<map>", "</map>")
             and not l.strip().startswith("<?xml")]
    if changes:
        sh("shell", "am", "force-stop", PKG)
        for key, value in changes:
            lines = [l for l in lines if 'name="%s"' % key not in l]
            if value != "-":
                lines.append(element(key, value))
        body = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + "\n".join(lines) + "\n</map>\n"
        local = os.path.join(os.environ.get("TEMP", "."), "minilock-prefs.xml")
        with open(local, "w", encoding="utf-8", newline="\n") as f:
            f.write(body)
        sh("push", local, "/data/local/tmp/minilock-prefs.xml")
        r = sh("shell", "run-as", PKG, "cp", "/data/local/tmp/minilock-prefs.xml", FILE)
        if r.returncode != 0:
            sys.exit("push failed: " + r.stderr)
        sh("shell", "rm", "/data/local/tmp/minilock-prefs.xml")
    for l in sorted(lines):
        print(l.strip())


if __name__ == "__main__":
    main()
