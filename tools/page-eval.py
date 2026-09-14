"""Evaluate a JavaScript expression inside every WebView page of the running app.

    python tools/page-eval.py "window.__lock.placement()"
    python tools/page-eval.py "window.__lock.testTurn(60,0,0)"
    python tools/page-eval.py "(document.getElementById('gyrohud')||{}).textContent"

The debug build exposes WebView DevTools on an abstract socket named after the app's pid.
This finds it, forwards it to :9222 and evaluates the expression in each open page. The in-app
hero and the fullscreen preview both load lock.html -- tell them apart by `vh` (innerHeight).
Needs `pip install websocket-client`. Always pins the GrapheneOS phone; never the emulator.
"""
import json
import os
import re
import subprocess
import sys
import urllib.request

import websocket

ADB = os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe")
PORT = 9222


def find_device():
    """The phone's wireless-debugging address changes; take MINILOCK_DEVICE, else ask mDNS."""
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
            if ident == "2026091001":          # the GrapheneOS phone, never the emulator
                return addr
    sys.exit("the GrapheneOS phone is not advertising wireless debugging on this network")


DEVICE = find_device()


def adb(*args):
    return subprocess.run([ADB, "-s", DEVICE, *args], capture_output=True, text=True).stdout


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    expr = sys.argv[1]
    socks = sorted(set(re.findall(r"webview_devtools_remote_\d+",
                                  adb("shell", "cat", "/proc/net/unix"))))
    if not socks:
        sys.exit("no WebView DevTools socket: is Minilock running on the phone?")
    subprocess.run([ADB, "-s", DEVICE, "forward", "--remove", f"tcp:{PORT}"], capture_output=True)
    adb("forward", f"tcp:{PORT}", f"localabstract:{socks[0]}")
    pages = [p for p in json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json"))
             if p.get("type") == "page"]
    wrapped = ("(function(){var r=(" + expr + ");"
               "return JSON.stringify({vh:innerHeight,vw:innerWidth,value:r===undefined?null:r});})()")
    for p in pages:
        ws = websocket.create_connection(p["webSocketDebuggerUrl"], timeout=8, suppress_origin=True)
        ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate",
                            "params": {"expression": wrapped, "returnByValue": True}}))
        while True:
            m = json.loads(ws.recv())
            if m.get("id") == 1:
                break
        ws.close()
        result = m.get("result", {})
        if "exceptionDetails" in result:
            details = result["exceptionDetails"]
            print(p["id"][:8], "EXCEPTION", details.get("text"),
                  details.get("exception", {}).get("description", ""))
        else:
            print(p["id"][:8], result.get("result", {}).get("value"))


if __name__ == "__main__":
    main()
