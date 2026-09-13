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
const CAM_Y=-0.15;
const NEED_W=2.30, NEED_H=3.20;     // world units: the case is 2.0 across, the bow adds height
let camFit=9.2, zoom=1;
function fitCamera(aspect){
  const t=Math.tan(camera.fov*Math.PI/360);
  camFit=Math.max((NEED_H/2)/t, (NEED_W/2)/(t*Math.max(0.2,aspect)));
}

/* ---- gyroscope: the phone moves, the watch and the room do not ---- */
let tiltX=0, tiltY=0, wantX=0, wantY=0;
window.__lock={
  setTilt(px,py){                     // radians, already relative to the baseline
    wantX=Math.max(-0.42,Math.min(0.42,px));
    wantY=Math.max(-0.58,Math.min(0.58,py));
  },
  recentre(){ wantX=wantY=0; }
};
function applyCamera(){
  // Orbit the CAMERA rather than rotating the scene: the watch and the studio stay fixed in
  // world space, so tilting sweeps the environment map across the gold the way it would across
  // real metal. Rotating the scene would carry the lights along and kill the effect.
  tiltX+=(wantX-tiltX)*0.12;          // damped enough to feel like glass rather than jelly
  tiltY+=(wantY-tiltY)*0.12;
  const d=camFit*zoom;
  camera.position.set(
    Math.sin(tiltY)*Math.cos(tiltX)*d,
    CAM_Y+Math.sin(tiltX)*d,
    Math.cos(tiltY)*Math.cos(tiltX)*d
  );
  camera.lookAt(0,CAM_Y,0);
}
const _rawLoop=loop;
loop=function(now){ applyCamera(); _rawLoop(now); };

/* ---- the host picks its chrome: #bare drops the card for the in-app hero ---- */
if((location.hash||"").indexOf("bare")>=0){
  const card=document.querySelector(".cardwrap");
  if(card) card.style.display="none";
}

/* ---- pinch to zoom, double tap to reset ---- */
(function(){
  const el=document.getElementById("gl");
  let pinching=false, startGap=0, startZoom=1, lastTap=0;
  const gap=t=>Math.hypot(t[0].clientX-t[1].clientX, t[0].clientY-t[1].clientY);
  el.addEventListener("touchstart",e=>{
    if(e.touches.length===2){
      pinching=true; dragging=false;          // a pinch must not also spin the watch
      startGap=gap(e.touches); startZoom=zoom;
    }else if(e.touches.length===1){
      const now=Date.now();
      if(now-lastTap<300){ zoom=1; }          // double tap returns to the fitted framing
      lastTap=now;
    }
  },{passive:true});
  el.addEventListener("touchmove",e=>{
    if(pinching&&e.touches.length===2){
      const g=gap(e.touches);
      if(g>0) zoom=Math.max(0.42,Math.min(2.4,startZoom*(startGap/g)));
    }
  },{passive:true});
  const end=e=>{ if(e.touches.length<2) pinching=false; };
  el.addEventListener("touchend",end,{passive:true});
  el.addEventListener("touchcancel",end,{passive:true});
})();

requestAnimationFrame(loop);

/* ---- wiring ---- */""")

io.open("app/src/main/assets/lock.html", "w", encoding="utf-8").write(s)
print("assets/lock.html:", os.path.getsize("app/src/main/assets/lock.html"), "bytes")
