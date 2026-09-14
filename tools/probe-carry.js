/* In-page test of "carry the watch by its ring", run through tools/page-eval.py:
 *
 *     python tools/page-eval.py "$(cat tools/probe-carry.js)"
 *
 * Dispatches synthetic pointer events on the canvas: finds the ring by probing a grid around
 * its computed position, drags 100 px from it, reads the placement, then restores the original
 * placement in the same JavaScript tick, so nothing is ever rendered or saved. A control drag on
 * the dial centre must leave the placement unchanged.
 *
 * Two quirks worth knowing. Synthetic pointers have no id the browser recognises, so
 * setPointerCapture throws inside the listeners; that is why a hit is detected as TWO error
 * events (the carry listener and then the turn listener, which is not stopped) versus one for a
 * miss, and why every pointerup is sent twice, so the turn handler's release always runs and a
 * drag can never stick. The module's own variables (spin, camera, dragging) are not reachable
 * from the evaluator; only window.__lock and elements with ids are.
 */
(function(){
  var errs=0; var h=function(e){errs++; e.preventDefault();}; window.addEventListener("error",h);
  var ev=function(t,x,y){return new PointerEvent(t,{clientX:x,clientY:y,pointerId:7,isPrimary:true,bubbles:true,cancelable:true,pointerType:"touch"});};
  var p0=window.__lock.placement(), parts=p0.split(",").map(Number);
  var z=parts[0], ux=parts[1], uy=parts[2];
  var t=Math.tan(26*Math.PI/360), aspect=innerWidth/innerHeight;
  var camFit=Math.max((4.15/2)/t,(2.62/2)/(t*Math.max(0.2,aspect)));   // assumes the card is off
  var d=camFit*z, pxPerWorld=innerHeight/(2*d*t);
  var ex=innerWidth/2-ux*pxPerWorld, ey=innerHeight/2-(1.452+0.15-uy)*pxPerWorld;
  var hit=null, tried=0;
  outer: for(var dy=-90;dy<=90;dy+=30) for(var dx=-90;dx<=90;dx+=30){
    var x=ex+dx, y=ey+dy;
    tried++; errs=0; glCanvas.dispatchEvent(ev("pointerdown",x,y)); var n=errs;
    glCanvas.dispatchEvent(ev("pointerup",x,y)); glCanvas.dispatchEvent(ev("pointerup",x,y));
    if(n>=2){ hit=[Math.round(x),Math.round(y)]; break outer; }
  }
  var r={estimate:[Math.round(ex),Math.round(ey)],tried:tried,hit:hit,before:p0,pxPerWorld:+pxPerWorld.toFixed(1)};
  if(hit){
    glCanvas.dispatchEvent(ev("pointerdown",hit[0],hit[1]));
    glCanvas.dispatchEvent(ev("pointermove",hit[0]+100,hit[1]+100));
    r.during=window.__lock.placement();
    r.expected=z+","+(ux-100/pxPerWorld)+","+(uy+100/pxPerWorld);
    glCanvas.dispatchEvent(ev("pointerup",hit[0]+100,hit[1]+100));
    glCanvas.dispatchEvent(ev("pointerup",hit[0]+100,hit[1]+100));
    window.__lock.setPlacement(z,ux,uy);
    r.after=window.__lock.placement();
    var cx=innerWidth/2-ux*pxPerWorld, cy=innerHeight/2+uy*pxPerWorld;
    glCanvas.dispatchEvent(ev("pointerdown",cx,cy)); glCanvas.dispatchEvent(ev("pointermove",cx+3,cy+3));
    r.dialControl=window.__lock.placement();
    glCanvas.dispatchEvent(ev("pointerup",cx+3,cy+3)); glCanvas.dispatchEvent(ev("pointerup",cx+3,cy+3));
  }
  window.removeEventListener("error",h);
  return r;
})()
