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
#gyrohud{position:fixed;left:0;right:0;top:max(14px,env(safe-area-inset-top));text-align:center;
  font:11px/1.4 monospace;color:#8a93a0;pointer-events:none;z-index:9;white-space:pre}
</style>""")

# A tap must not turn the watch over: it collides with double-tap-to-zoom, and the handler is
# registered by value so it cannot be wrapped afterwards. Drag still turns it.
sub("  if(moved<5) windOver();                    // a tap winds it, a drag keeps its speed",
    "  // a tap deliberately does nothing here: it collided with double-tap-to-zoom")

# The plate on the caseback had a "Set as screensaver" button that only ever changed its own
# label; the app has the real one. Gone, markup and wiring both.
sub("""        <button type="button" class="act" id="setDream">Set as screensaver</button>
""", "")
sub("""document.getElementById("setDream").addEventListener("click",function(){
  this.textContent="Opens Android settings"; this.disabled=true;
  setTimeout(()=>{this.textContent="Set as screensaver";this.disabled=false;},2200);
});
""", "")

# Make loop reassignable so the camera pass can wrap it. The inner function must NOT be named
# `loop`: in a named function expression the name is bound to the function itself, so the
# `requestAnimationFrame(loop)` inside the body re-scheduled the raw loop, and the wrapper --
# with the camera fit, zoom, placement and gyroscope in it -- ran exactly once per page load.
# That was the case from v0.0.05 to v0.0.27.
sub("function loop(now){", "var loop = function rawLoop(now){")

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

# The studio backdrop takes a whiteness. 0 keeps the dark blue-grey exactly as designed; anything
# else is a neutral grey with the same vignette, darkening the corners a little on light
# backgrounds so they still read as a backdrop and not as nothing.
sub("""function backdropTex(){
  const c=document.createElement("canvas"); c.width=c.height=512;
  const g=c.getContext("2d");
  g.fillStyle="#040507"; g.fillRect(0,0,512,512);
  const rg=g.createRadialGradient(250,196,10,256,250,320);
  rg.addColorStop(0,"#2B313A"); rg.addColorStop(.38,"#171B21");
  rg.addColorStop(.72,"#0B0D11"); rg.addColorStop(1,"#040507");""",
"""function backdropTex(white){
  const c=document.createElement("canvas"); c.width=c.height=512;
  const g=c.getContext("2d");
  const w=Math.max(0,Math.min(1,white||0));
  const grey=v=>{ const h=Math.round(Math.max(0,Math.min(1,v))*255).toString(16).padStart(2,"0"); return "#"+h+h+h; };
  const stop=(lift,sink)=> w ? grey(w+(1-w)*lift-w*w*sink) : null;
  const c0=stop(.16,0)||"#2B313A", c1=stop(.08,.02)||"#171B21", c2=stop(.03,.05)||"#0B0D11", c3=stop(0,.08)||"#040507";
  g.fillStyle=c3; g.fillRect(0,0,512,512);
  const rg=g.createRadialGradient(250,196,10,256,250,320);
  rg.addColorStop(0,c0); rg.addColorStop(.38,c1);
  rg.addColorStop(.72,c2); rg.addColorStop(1,c3);""")

# The text under the watch is light grey on the dark studio; on a light background it needs ink.
sub("""txt(g,val&&text?val+"   "+text:(val||text),12,y,12.5,"#C6CED6",true,"left");""",
    """txt(g,val&&text?val+"   "+text:(val||text),12,y,12.5,bgWhite>0.5?"#2A2F36":"#C6CED6",true,"left");""")
sub("""0,y+6,9,"#6C7883",false,"center",.16);""",
    """0,y+6,9,bgWhite>0.5?"#6E7680":"#6C7883",false,"center",.16);""")

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
      camBack=new THREE.Vector3(), camRight=new THREE.Vector3(), camUp=new THREE.Vector3();
var bgWhite=0;                        // 0 = the dark studio, 1 = white; a user setting
/* ---- the caseback plate is remembered ---- */
// The page cannot write preferences, so it hands the plate's state to the app through the
// `minilock` interface on every change, and the app gives it back with setState on load.
let restoring=false;
function persist(){
  if(!window.minilock) return;        // the desktop artifact has no app behind it
  minilock.put("plate", JSON.stringify({finish:state.finish,ambient:state.ambient,sweep:state.sweep,
    bottom:state.bottom,weather:state.weather,alerts:state.alerts,alarm:state.alarm,event:state.event}));
}
{ const plate=document.getElementById("plate");
  const later=()=>{ const r=restoring; setTimeout(()=>{ if(!r) persist(); },0); };
  plate.addEventListener("click",later,true);
  plate.addEventListener("change",later,true); }
let haveBase=false, lastSpin=0;
let hud=null, hudAt=0, samples=0, testOffset=null;
function retarget(){                  // where the camera should be, relative to where it started
  qWanted.copy(qDevice);
  if(testOffset) qWanted.multiply(testOffset);   // a local turn, as if the hand had made it
  qWanted.premultiply(qBase);
}
const eul=new THREE.Euler();
const deg=r=>String(Math.round(r*180/Math.PI)).padStart(4)+"°";
window.__lock={
  testTurn(yawDeg,pitchDeg,rollDeg){  // from adb: pretend the phone turned this much about
    if(yawDeg===null||yawDeg===undefined) testOffset=null;   // its own axes. null clears it.
    else { eul.set(pitchDeg*Math.PI/180, yawDeg*Math.PI/180, rollDeg*Math.PI/180, "YXZ");
           testOffset=new THREE.Quaternion().setFromEuler(eul); }
    if(haveBase) retarget();
  },
  setDebug(on){                       // what the gyroscope delivers, in words; preview only
    if(on && !hud){ hud=document.createElement("div"); hud.id="gyrohud"; document.body.appendChild(hud); }
    if(hud) hud.style.display = on ? "block" : "none";
  },
  setQuat(w,x,y,z){
    // Euler angles were the wrong tool: a phone held upright to look at the screen sits right
    // on the gimbal-lock singularity, where azimuth goes degenerate and collapses back. A
    // quaternion has no singularity, so the orbit is genuinely free in every direction.
    // No axis swap. The sensor quaternion takes DEVICE axes (X right, Y up the screen, Z out
    // of the screen) to the world, and a three.js camera's local axes are exactly those. The
    // baseline product below is device-now -> device-at-start, so the world frame cancels
    // out entirely. Relabelling the components as (x,z,-y) conjugated the rotation instead:
    // a yaw about the screen's vertical axis became a roll about the viewing axis, so walking
    // round the watch spun it flat in the screen and never showed its side.
    qDevice.set(x, y, z, w);
    samples++;
    const first=!haveBase;
    if(first){ qBase.copy(qDevice).invert(); haveBase=true; }
    retarget();
    if(first) qSmooth.copy(qWanted);   // start from rest, not from a swing out of the raw frame
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
  setState(json){                     // the caseback plate, the way it was left last time
    let s; try{ s=JSON.parse(json); }catch(e){ return; }
    if(!s||typeof s!=="object") return;
    restoring=true;
    try{
      if(s.finish!==undefined){ const b=document.querySelector('#finish button[data-i="'+s.finish+'"]'); if(b) b.click(); }
      if(s.bottom!==undefined){ const b=document.querySelector('#bottom button[data-v="'+s.bottom+'"]'); if(b) b.click(); }
      const flags={ambient:"mAmbient",sweep:"mSweep",weather:"mWeather",alerts:"mAlerts",alarm:"mAlarm",event:"mEvent"};
      for(const k in flags){
        if(s[k]===undefined) continue;
        const el=document.getElementById(flags[k]);
        if(el && el.checked!==!!s[k]){ el.checked=!!s[k]; el.dispatchEvent(new Event("change",{bubbles:true})); }
      }
    } finally { restoring=false; }
  },
  setBackground(white){               // the scale from white to black, behind the watch
    bgWhite=Math.max(0,Math.min(1,+white||0));
    const m=backdrop.material; if(m.map) m.map.dispose();
    m.map=backdropTex(bgWhite); m.needsUpdate=true;
    const v=Math.round((bgWhite-bgWhite*bgWhite*0.08)*255);   // the backdrop's edge grey
    const css=bgWhite?"rgb("+v+","+v+","+v+")":"#000";
    document.body.style.background=css;
    const screen=document.getElementById("screen"); if(screen) screen.style.background=css;
    cardAt=-1e9;                      // repaint the text under the watch in the right ink
  },
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
  if(hud && performance.now()-hudAt>120){
    hudAt=performance.now();
    eul.setFromQuaternion(qWanted,"YXZ");
    hud.textContent="gyro #"+samples+"   yaw "+deg(eul.y)+"   pitch "+deg(eul.x)
      +"   roll "+deg(eul.z)+(haveBase?"":"   (no baseline yet)")+(testOffset?"   TEST":"");
  }
  const d=camFit*zoom;
  // The offset that makes room for the card must be derived from the CURRENT distance. Taking
  // it from the fitted distance meant zooming in kept a far-view offset and threw the watch
  // clean off the top of the frame.
  const t=Math.tan(camera.fov*Math.PI/360);
  const cardLift=(2*d*t)*cardFrac/2;
  // The watch is a fixed object and the phone IS the camera: give the camera the device's
  // orientation, then stand it off by d along its own backward axis. The watch therefore stays
  // dead centre while you walk right around it, with no pole to tip over.
  camera.quaternion.copy(qSmooth);
  camBack.set(0,0,1).applyQuaternion(qSmooth).multiplyScalar(d);
  // Placement is a SCREEN offset: the user parks the watch somewhere on the glass and it must
  // stay there while the phone turns. So offset along the camera's own right and up, not
  // along world X and Y.
  camRight.set(1,0,0).applyQuaternion(qSmooth);
  camUp.set(0,1,0).applyQuaternion(qSmooth);
  camera.position.set(0,-0.15,0).add(camBack)
      .addScaledVector(camRight,userX).addScaledVector(camUp,userY-cardLift);
}
/* ---- carry: take the watch by its ring and put it anywhere on the screen ---- */
// One finger on the bow moves the watch; one finger anywhere else still turns it over. These
// run in the capture phase, before the turn handlers on the same canvas, and stop the event
// there when the ring was hit, so the turn never starts.
const bowPos=new THREE.Vector3();
let carrying=false, carryX=0, carryY=0;
function overBow(e){
  bowPos.set(0,1.452,0).applyMatrix4(spin.matrixWorld).project(camera);
  const r=glCanvas.getBoundingClientRect();
  const sx=r.left+(bowPos.x*.5+.5)*r.width, sy=r.top+(-bowPos.y*.5+.5)*r.height;
  const reach=Math.max(30,r.width*0.10);   // a fingertip, not a cursor
  return Math.hypot(e.clientX-sx,e.clientY-sy)<reach;
}
glCanvas.addEventListener("pointerdown",e=>{
  if(!e.isPrimary){ carrying=false; return; }   // a second finger hands over to placement
  if(!overBow(e)) return;
  carrying=true; carryX=e.clientX; carryY=e.clientY; dragging=false;
  glCanvas.setPointerCapture(e.pointerId);
  e.stopImmediatePropagation();
},{capture:true});
glCanvas.addEventListener("pointermove",e=>{
  if(!carrying||!e.isPrimary) return;
  window.__lock.nudge(e.clientX-carryX,e.clientY-carryY);
  carryX=e.clientX; carryY=e.clientY;
  e.stopImmediatePropagation();
},{capture:true});
function putDown(e){ if(!carrying) return; carrying=false; e.stopImmediatePropagation(); }
glCanvas.addEventListener("pointerup",putDown,{capture:true});
glCanvas.addEventListener("pointercancel",putDown,{capture:true});

const _rawLoop=loop;
loop=function(now){ applyCamera(); _rawLoop(now); };

/* The page used to run its own pinch handler here. It fought the native ScaleGestureDetector --
   both wrote `zoom` on the same gesture, so they cancelled each other out. The native detector
   is now the only thing that drives zoom and placement, on every surface. */

requestAnimationFrame(loop);

/* ---- wiring ---- */""")

io.open("app/src/main/assets/lock.html", "w", encoding="utf-8").write(s)
print("assets/lock.html:", os.path.getsize("app/src/main/assets/lock.html"), "bytes")
