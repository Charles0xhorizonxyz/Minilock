"""Generate app/src/main/assets/lock.html from tools/watch3d.html.

The Android app runs the WebGL scene unchanged; this only strips the page chrome, points
three.js at the bundled copy, and adds what the phone needs: a camera that fits the watch to
the screen, pinch zoom, and a hook the native gyroscope can drive.

    python tools/make-lock-asset.py
"""
import io
import os

os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
s = io.open("tools/watch3d.html", encoding="utf-8").read()


def sub(old, new):
    global s
    assert s.count(old) == 1, (s.count(old), old[:80])
    s = s.replace(old, new)


# No INTERNET permission, so nothing may be fetched. three.js ships in assets; the web fonts
# go away and we fall back to the faces WebView already has (Roboto, Noto Serif).
sub('<script src="https://cdn.jsdelivr.net/npm/three@0.155.0/build/three.min.js"></script>',
    '<script src="three.min.js"></script>')
i = s.index('<link rel="stylesheet" href="https://fonts.googleapis.com')
s = s[:i] + s[s.index('>', i) + 1:]

# Without this, WebView lays the page out at a 980px viewport and scales it down -- which is
# why every label rendered tiny and soft on the phone. The artifact host injects this tag; a
# local asset has to carry its own.
VIEWPORT = '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">'
s = VIEWPORT + "\n" + s

# Strip the page down to the watch: this is a lock screen, not a document.
sub("</style>", """
/* --- lock screen shell --- */
html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden;
  -webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent}
header,.panel,.notes,.stage>p,.readout{display:none!important}
.work{display:block;padding:0;gap:0;max-width:none;grid-template-columns:none}
.stage{margin:0}
.screen{aspect-ratio:auto;width:100vw;height:100vh;border:0;border-radius:0;touch-action:none}
.cardwrap{bottom:6%}
</style>""")

# A tap must not turn the watch over: it collides with double-tap-to-zoom, and the handler is
# registered by value so it cannot be wrapped afterwards. Drag still turns it.
sub("  if(moved<5) windOver();                    // a tap winds it, a drag keeps its speed",
    "  // a tap deliberately does nothing here: it collided with double-tap-to-zoom")

# make loop reassignable so the camera pass can wrap it
sub("function loop(now){", "var loop = function loop(now){")

# The camera was at a fixed distance, so on a 9:20 phone the visible width was 1.91 world
# units while the case is 2.0 wide — the edges were always going to clip. Fit it to whichever
# axis is tighter, then let pinch scale that.
sub("""  camera.aspect=r.width/r.height; camera.updateProjectionMatrix();
  setSens(r.width);
  return r;""",
"""  camera.aspect=r.width/r.height; camera.updateProjectionMatrix();
  fitCamera(camera.aspect);
  setSens(r.width);
  return r;""")

sub("""  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);

/* ---- wiring ---- */""",
"""  requestAnimationFrame(loop);
};

/* ---- framing: fit the whole object, then let the user pinch ---- */
// World units the object needs: the case is 2.0 across, and from the look-at point it reaches
// 1.79 up to the top of the bow and 0.85 down. Rather than guess a constant for the card, we
// measure it: it reserves its real share of the viewport, and the watch is fitted into, and
// centred in, whatever is left. On a tall phone the fit stays width-limited so nothing shrinks.
const NEED_W=2.62, NEED_H=4.15;     // includes a margin: tilting swings the silhouette about
let camFit=9.2, zoom=1, cardOn=true, cardFrac=0, userX=0, userY=0;
function fitCamera(aspect){
  const t=Math.tan(camera.fov*Math.PI/360);
  const card=document.querySelector(".cardwrap");
  const vh=window.innerHeight||1;
  cardFrac=(cardOn && card && card.offsetHeight)
      ? Math.min(0.45,(card.offsetHeight + vh*0.07)/vh) : 0;
  const need=NEED_H/Math.max(0.5,1-cardFrac);
  camFit=Math.max((need/2)/t,(NEED_W/2)/(t*Math.max(0.2,aspect)));
}

/* ---- gyroscope: the phone moves, the watch and the room do not ---- */
const qDevice=new THREE.Quaternion(), qBase=new THREE.Quaternion(),
      qWanted=new THREE.Quaternion(), qSmooth=new THREE.Quaternion(),
      camBack=new THREE.Vector3();
let haveBase=false, lastSpin=0;
window.__lock={
  setQuat(w,x,y,z){
    // Euler angles were the wrong tool: a phone held upright to look at the screen sits right
    // on the gimbal-lock singularity, where azimuth goes degenerate and collapses back. A
    // quaternion has no singularity, so the orbit is genuinely free in every direction.
    // Android's sensor frame is X east, Y north, Z up; three.js is X right, Y up, Z toward the
    // viewer -- hence the axis swap.
    qDevice.set(x, z, -y, w);
    if(!haveBase){ qBase.copy(qDevice).invert(); haveBase=true; qSmooth.copy(qDevice); }
    qWanted.copy(qDevice).premultiply(qBase);
    const spin = 2*Math.acos(Math.min(1,Math.abs(qWanted.w)));
    vphi += -Math.max(-0.5,Math.min(0.5,(spin-lastSpin)))*2.2;   // motion rocks it on its bow
    lastSpin = spin;
  },
  setCard(on){                        // the line of text under the watch is a user setting
    const card=document.querySelector(".cardwrap");
    if(card) card.style.display = on ? "" : "none";
    cardOn=!!on;
    fitCamera(camera.aspect);         // reserve room for it, or take the room back
  },
  setZoom(z){                         // driven natively by ScaleGestureDetector
    zoom=Math.max(0.42,Math.min(2.4,z));
    dragging=false;                   // a pinch must not also spin the watch
  },
  nudge(dxPixels,dyPixels){           // two-finger drag places the watch anywhere
    const t=Math.tan(camera.fov*Math.PI/360), d=camFit*zoom;
    const worldPerPx=(2*d*t)/(window.innerHeight||1);
    userX=Math.max(-6,Math.min(6,userX-dxPixels*worldPerPx));
    userY=Math.max(-6,Math.min(6,userY+dyPixels*worldPerPx));
  },
  placement(){ return zoom+","+userX+","+userY; },
  setPlacement(z,x,y){ zoom=z; userX=x; userY=y; },
  setBattery(pct,charging){           // the real charge, from BatteryManager
    state.batt=Math.max(0,Math.min(100,Math.round(pct)));
    state.charging=!!charging;
  },
};
function applyCamera(){
  // Orbit the CAMERA rather than rotating the scene: the watch and the studio stay fixed in
  // world space, so tilting sweeps the environment map across the gold the way it would across
  // real metal. Rotating the scene would carry the lights along and kill the effect.
  qSmooth.slerp(qWanted, 0.22);       // damped enough to feel like glass rather than jelly
  const d=camFit*zoom;
  // The offset that makes room for the card must be derived from the CURRENT distance. Taking
  // it from the fitted distance meant zooming in kept a far-view offset and threw the watch
  // clean off the top of the frame.
  const t=Math.tan(camera.fov*Math.PI/360);
  const lookY=-0.15-(2*d*t)*cardFrac/2+userY;
  // The watch is a fixed object and the phone IS the camera: give the camera the device's
  // orientation, then stand it off by d along its own backward axis. The watch therefore stays
  // dead centre while you walk right around it, with no pole to tip over.
  camera.quaternion.copy(qSmooth);
  camBack.set(0,0,1).applyQuaternion(qSmooth).multiplyScalar(d);
  camera.position.set(userX,lookY,0).add(camBack);
}
const _rawLoop=loop;
loop=function(now){ applyCamera(); _rawLoop(now); };

/* The page used to run its own pinch handler here. It fought the native ScaleGestureDetector --
   both wrote `zoom` on the same gesture, so they cancelled each other out. The native detector
   is now the only thing that drives zoom and placement, on every surface. */

requestAnimationFrame(loop);

/* ---- wiring ---- */""")

io.open("app/src/main/assets/lock.html", "w", encoding="utf-8").write(s)
print("assets/lock.html:", os.path.getsize("app/src/main/assets/lock.html"), "bytes")
