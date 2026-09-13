import io, os
os.chdir(r"C:\Users\Charles\Documents\Miniscreen")
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
.screen{aspect-ratio:auto;width:100vw;height:100vh;border:0;border-radius:0}
.cardwrap{bottom:9%}
.hint{position:fixed;left:0;right:0;bottom:2.5%;text-align:center;pointer-events:none;
  font-family:"Roboto",system-ui,sans-serif;font-size:3.2vw;letter-spacing:.22em;
  text-transform:uppercase;color:rgba(220,226,232,.42)}
</style>""")

sub("</section>", "</section>\n<div class=\"hint\">Swipe up to unlock</div>")

# make loop reassignable so the tilt pass can wrap it
sub("function loop(now){", "var loop = function loop(now){")

# Orbit the CAMERA, not the scene. The watch and the studio stay fixed in world space, so
# tilting the phone sweeps the environment map across the gold exactly as it would across real
# metal. Rotating the scene would carry the lights with it and kill the whole effect.
sub("""  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);

/* ---- wiring ---- */""",
"""  requestAnimationFrame(loop);
};

/* ---- gyroscope: the phone moves, the watch and the room do not ---- */
const CAM_R=9.2, CAM_Y=-0.15;
let tiltX=0, tiltY=0, wantX=0, wantY=0;
// the host picks its chrome: #bare = watch only, #preview = different wording
(function(){
  const h=location.hash||"", hint=document.querySelector(".hint"),
        card=document.querySelector(".cardwrap");
  if(h.indexOf("bare")>=0){ if(hint)hint.style.display="none"; if(card)card.style.display="none"; }
  else if(h.indexOf("preview")>=0 && hint){ hint.textContent="Swipe up to return"; }
})();
window.__lock={
  setTilt(px,py){                     // radians, already relative to the baseline
    wantX=Math.max(-0.40,Math.min(0.40,px));
    wantY=Math.max(-0.55,Math.min(0.55,py));
  },
  recentre(){ wantX=wantY=0; }
};
function applyTilt(){
  tiltX+=(wantX-tiltX)*0.12;          // damped enough to feel like glass rather than jelly
  tiltY+=(wantY-tiltY)*0.12;
  camera.position.set(
    Math.sin(tiltY)*Math.cos(tiltX)*CAM_R,
    CAM_Y+Math.sin(tiltX)*CAM_R,
    Math.cos(tiltY)*Math.cos(tiltX)*CAM_R
  );
  camera.lookAt(0,CAM_Y,0);
}
const _rawLoop=loop;
loop=function(now){ applyTilt(); _rawLoop(now); };
requestAnimationFrame(loop);

/* ---- wiring ---- */""")

io.open("app/src/main/assets/lock.html", "w", encoding="utf-8").write(s)
print("assets/lock.html:", os.path.getsize("app/src/main/assets/lock.html"), "bytes")
