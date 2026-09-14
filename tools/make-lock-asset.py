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
.plate{display:none!important}
#nightfall{position:fixed;inset:0;background:#000;opacity:0;pointer-events:none;z-index:20}
#gyrohud{position:fixed;left:0;right:0;top:max(14px,env(safe-area-inset-top));text-align:center;
  font:11px/1.4 monospace;color:#8a93a0;pointer-events:none;z-index:9;white-space:pre}
</style>""")

# A tap must not turn the watch over: it collides with double-tap-to-zoom, and the handler is
# registered by value so it cannot be wrapped afterwards. Drag still turns it.
sub("  if(moved<5) windOver();                    // a tap winds it, a drag keeps its speed",
    "  if(moved<5 && e && e.type===\"pointerup\") tapBack(e);   // a tap on the caseback works its controls\n"
    "  else if(e && e.type===\"pointerup\") maybeGesture(e);        // a flick of the dial is a gesture")
sub("function release(){", "function release(e){")

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
"""function backdropTex(tint){         // tint: nothing for the studio, else [r,g,b] in 0..1
  const c=document.createElement("canvas"); c.width=c.height=512;
  const g=c.getContext("2d");
  const hex=v=>"#"+v.map(x=>Math.round(Math.max(0,Math.min(1,x))*255).toString(16).padStart(2,"0")).join("");
  const toward=(col,to,k)=>col.map((x,i)=>x+(to[i]-x)*k);
  const stop=(lift,sink)=> tint ? hex(toward(toward(tint,[1,1,1],lift),[0,0,0],sink)) : null;
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
var bgWhite=0;                        // luminance of the chosen background; 0 for the dark studio
// Seamless-paper tones, the way a studio backdrop actually comes: ivory, rose clay, ochre,
// sage, teal grey, slate blue, plum, charcoal. Same order as a spectrum, none of its neon.
// The slider's track in the app carries these same eight stops, evenly spaced.
const PAPERS=[[.933,.910,.863],[.769,.545,.502],[.769,.659,.416],[.561,.643,.541],
              [.435,.604,.612],[.369,.451,.580],[.420,.353,.478],[.118,.125,.141]];
function bgColour(v){                 // the slider: 1 is ivory, 0 the dark studio, paper between
  if(v<=0) return null;
  const t=(1-v)*(PAPERS.length-1), i=Math.min(PAPERS.length-2,Math.floor(t)), k=t-i;
  return PAPERS[i].map((x,c)=>x+(PAPERS[i+1][c]-x)*k);
}
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
let hud=null, hudAt=0, samples=0, testOffset=null, dark=false, darkTimer=0;
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
  face(){ return Math.cos(theta); },  // 1 dial toward you, -1 caseback; for tests from adb
  gyroReset(){                        // gyroscope switched off: back to head-on, re-baseline when it returns
    haveBase=false; qWanted.identity(); qSmooth.identity();
  },
  fade(seconds){                      // the lock screen's night: the watch dims to black, then rests
    let veil=document.getElementById("nightfall");
    if(!veil){ veil=document.createElement("div"); veil.id="nightfall"; document.body.appendChild(veil); }
    veil.style.transition="none"; veil.style.opacity="0"; void veil.offsetHeight;   // restart cleanly
    veil.style.transition="opacity "+Math.max(0.2,+seconds||1)+"s linear"; veil.style.opacity="1";
    clearTimeout(darkTimer); darkTimer=setTimeout(()=>{ dark=true; }, Math.max(0.2,+seconds||1)*1000+100);
  },
  wake(){                             // a touch, or the screen coming back: lift the veil, render again
    clearTimeout(darkTimer); dark=false;
    const veil=document.getElementById("nightfall");
    if(veil){ veil.style.transition="opacity .25s linear"; veil.style.opacity="0"; }
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
      paintBack();
    } finally { restoring=false; }
  },
  setBackground(v){                   // the scale: white, through the rainbow, to the dark studio
    const tint=bgColour(Math.max(0,Math.min(1,+v||0)));
    bgWhite=tint?0.2126*tint[0]+0.7152*tint[1]+0.0722*tint[2]:0;   // luminance; the card's ink follows it
    const m=backdrop.material; if(m.map) m.map.dispose();
    m.map=backdropTex(tint); m.needsUpdate=true;
    const css=tint?"rgb("+tint.map(x=>Math.round(x*0.92*255)).join(",")+")":"#000";   // the edge shade
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
  spinCheck();                        // a flick that carries two turns is a gesture
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
  // The studio wall follows the camera: always square-on, six units behind the watch as it
  // was built, so no orbit or tilt ever reaches its edge.
  backdrop.quaternion.copy(qSmooth);
  backdrop.position.copy(camBack).normalize().multiplyScalar(-6);
}
/* ---- carry: take the watch by its ring and put it anywhere on the screen ---- */
// One finger on the bow moves the watch; one finger anywhere else still turns it over. These
// run in the capture phase, before the turn handlers on the same canvas, and stop the event
// there when the ring was hit, so the turn never starts.
const bowPos=new THREE.Vector3();
let carrying=false, carryX=0, carryY=0;
/* ---- the lock screen's gestures: flicks of the dial, named here, acted on by the app ---- */
// A flick is a decisive horizontal drag: more than a fifth of the width, mostly sideways, under
// a second. "left1" is one flick right-to-left from the front (the caseback keeps its taps).
// "right2" is one hard flick left-to-right that carries the watch through two full turns: the
// flick arms it, and the spin is watched frame by frame until it either crosses the barrier
// before its second full turn (then it cannot settle short of two) or comes to rest. Carrying
// by the ring never reaches release, so it never counts. What each does is the user's choice,
// in the app; the page only names them.
let swipeX0=0, swipeY0=0, swipeT0=0, frontAtDown=true, thetaAtDown=0, spinArmed=false, spinFrom=0;
const fireGesture=g=>{ if(window.minilock && minilock.gesture) minilock.gesture(g); };
function maybeGesture(e){
  const dx=e.clientX-swipeX0, dy=e.clientY-swipeY0, now=performance.now();
  if(Math.abs(dx)<innerWidth*0.22 || Math.abs(dx)<1.5*Math.abs(dy) || now-swipeT0>1000) return;
  if(dx<0){ spinArmed=false; if(frontAtDown) fireGesture("left1"); return; }
  spinArmed=true; spinFrom=thetaAtDown;        // rightward: now watch how far it carries
}
function spinCheck(){
  if(!spinArmed) return;
  if(theta-spinFrom >= 3.5*Math.PI){ spinArmed=false; fireGesture("right2"); }
  else if(!dragging && Math.abs(omega)<0.05) spinArmed=false;   // at rest, short of two turns
}
function overBow(e){
  bowPos.set(0,1.452,0).applyMatrix4(spin.matrixWorld).project(camera);
  const r=glCanvas.getBoundingClientRect();
  const sx=r.left+(bowPos.x*.5+.5)*r.width, sy=r.top+(-bowPos.y*.5+.5)*r.height;
  const reach=Math.max(30,r.width*0.10);   // a fingertip, not a cursor
  return Math.hypot(e.clientX-sx,e.clientY-sy)<reach;
}
glCanvas.addEventListener("pointerdown",e=>{
  if(!e.isPrimary){ carrying=false; return; }   // a second finger hands over to placement
  swipeX0=e.clientX; swipeY0=e.clientY; swipeT0=performance.now(); frontAtDown=Math.cos(theta)>0.5; thetaAtDown=theta;
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


/* ---- the caseback controls, painted into the caseback itself ---- */
// The HTML plate is hidden. These are drawn into the same texture as the engraving, in the
// dial's own language -- gold, engraved captions, cut slots, knurled sliders -- so they sit ON
// the metal and follow it through every turn and tilt. A tap is ray-cast onto the caseback.
let backRegions=[];
const LEVERS=[["mAmbient","AMBIENT"],["mSweep","SWEEP"],["mWeather","WEATHER"],
              ["mAlerts","ALERTS"],["mAlarm","ALARM"],["mEvent","AGENDA"]];
const SIX=[["power","RESERVE"],["moon","MOON"],["h24","24 H"]];
const DIM="rgba(201,178,140,.42)";
function engrave(g,s,x,y,size,color,ls,align){
  txt(g,s,x,y+.9,size,"rgba(255,255,255,.13)",false,align||"center",ls);
  txt(g,s,x,y,size,color,false,align||"center",ls);
}
function slot(g,x,y,w,h){                   // a recess cut into the plate
  rrect(g,x,y,w,h,h/2,"#07080A");
  g.beginPath();g.roundRect(x+.6,y+.6,w-1.2,h-1.2,h/2);g.strokeStyle="rgba(0,0,0,.7)";g.lineWidth=1.2;g.stroke();
  g.beginPath();g.roundRect(x,y+h-.4,w,1.1,h/2);g.fillStyle="rgba(255,235,200,.15)";g.fill();   // the lip catching light
}
function knob(g,F,x,y,w,h){                 // a knurled gold slider
  const gr=g.createLinearGradient(0,y,0,y+h);
  gr.addColorStop(0,F.light);gr.addColorStop(.45,F.gold);gr.addColorStop(1,mix(F.gold,"#000000",.55));
  rrect(g,x+1,y+1.6,w,h,3,"rgba(0,0,0,.55)");
  rrect(g,x,y,w,h,3,gr);
  for(let i=1;i<4;i++) line(g,x+i*w/4,y+3,x+i*w/4,y+h-3,"rgba(0,0,0,.35)",.8);
  g.beginPath();g.roundRect(x,y,w,h,3);g.strokeStyle="rgba(0,0,0,.5)";g.lineWidth=.7;g.stroke();
}
function lever(g,F,x,y,on,label){           // x,y: the slot's top-left corner
  const w=44,h=16,kw=18,kh=20;
  slot(g,x,y,w,h);
  knob(g,F,on?x+w-kw-1:x+1,y-2,kw,kh);
  engrave(g,label,x+w+12,y+h/2+3,8.5,on?F.gold:DIM,.2,"left");
}
function medallion(g,F,i,x,y,selected){     // one alloy, turned and polished
  const A=FINISHES[i];
  circle(g,x,y+1.5,27,"rgba(0,0,0,.55)");
  const gr=g.createLinearGradient(x-24,y-24,x+24,y+24);
  gr.addColorStop(0,A.light);gr.addColorStop(.5,A.gold);gr.addColorStop(1,mix(A.gold,"#000000",.5));
  circle(g,x,y,24,gr);
  for(let r=6;r<22;r+=4) ring(g,x,y,r,"rgba(0,0,0,.10)",.7);
  ring(g,x,y,24,"rgba(0,0,0,.6)",1.2);
  if(selected){ ring(g,x,y,30,F.light,2); ring(g,x,y,32.6,"rgba(0,0,0,.5)",1);
    poly(g,[x-5,y-43,x+5,y-43,x,y-36],F.light,null,0); }
}
function backControls(g,F){
  backRegions=[];
  const on=id=>document.getElementById(id).checked;
  engrave(g,"GOLD",0,-166,7.5,F.gold,.34);
  [-74,0,74].forEach((x,i)=>{ medallion(g,F,i,x,-118,state.finish===i);
    backRegions.push({x0:x-36,y0:-160,x1:x+36,y1:-82,act:()=>pick('#finish button[data-i="'+i+'"]')}); });
  engrave(g,"MOVEMENT",0,-56,7.5,F.gold,.34);
  LEVERS.forEach(([id,label],i)=>{ const col=i%2,row=(i-col)/2,x=col?14:-160,y=-34+row*40;
    lever(g,F,x,y,on(id),label);
    backRegions.push({x0:x-6,y0:y-12,x1:x+150,y1:y+28,act:()=>flip(id)}); });
  engrave(g,"COUNTER AT SIX",0,108,7.5,F.gold,.34);
  slot(g,-78,132,156,16);
  SIX.forEach(([v,label],i)=>{ const x=-60+i*60,sel=state.bottom===v;
    if(sel) knob(g,F,x-9,130,18,20); else line(g,x,135,x,145,"rgba(255,235,200,.16)",1);
    engrave(g,label,x,171,7.5,sel?F.light:DIM,.2);
    backRegions.push({x0:x-30,y0:118,x1:x+30,y1:184,act:()=>pick('#bottom button[data-v="'+v+'"]')}); });
}
function pick(sel){ const b=document.querySelector(sel); if(b) b.click(); paintBack(); }
function flip(id){ const el=document.getElementById(id); el.checked=!el.checked;
  el.dispatchEvent(new Event("change",{bubbles:true})); paintBack(); }
const _paintBack=paintBack;
paintBack=function(){ _paintBack(); backControls(bctx,FINISHES[state.finish]); backTex.needsUpdate=true; };
paintBack();

const ray=new THREE.Raycaster(), ndc=new THREE.Vector2();
function tapBack(e){
  if(-Math.cos(theta)<0.8 || Math.abs(omega)>0.6) return;     // only a caseback at rest, facing you
  const r=glCanvas.getBoundingClientRect();
  ndc.set(((e.clientX-r.left)/r.width)*2-1, -((e.clientY-r.top)/r.height)*2+1);
  ray.setFromCamera(ndc,camera);
  const hit=ray.intersectObject(backMesh,false)[0];
  if(!hit||!hit.uv) return;
  const x=(hit.uv.x-.5)*582, y=(.5-hit.uv.y)*582;               // texture -> design units
  for(const q of backRegions) if(x>=q.x0&&x<=q.x1&&y>=q.y0&&y<=q.y1){ q.act(); return; }
}

const _rawLoop=loop;
loop=function(now){
  if(dark){ requestAnimationFrame(loop); return; }   // black: nothing to draw, nothing to spend
  applyCamera(); _rawLoop(now);
};

/* The page used to run its own pinch handler here. It fought the native ScaleGestureDetector --
   both wrote `zoom` on the same gesture, so they cancelled each other out. The native detector
   is now the only thing that drives zoom and placement, on every surface. */

requestAnimationFrame(loop);

/* ---- wiring ---- */""")

io.open("app/src/main/assets/lock.html", "w", encoding="utf-8").write(s)
print("assets/lock.html:", os.path.getsize("app/src/main/assets/lock.html"), "bytes")
