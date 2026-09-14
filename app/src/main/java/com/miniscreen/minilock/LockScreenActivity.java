package com.miniscreen.minilock;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.WebView;

/**
 * A stand-in lock screen. This is NOT a real keyguard — Android does not allow one to be
 * replaced — so it only looks like the lock screen while the real one is switched off. Home
 * still escapes it, which no app can prevent without being Device Owner. It is a stopgap
 * until the dial ships as a ClockProviderPlugin in a custom OS build.
 */
public class LockScreenActivity extends Activity {

    /** True while an instance exists; the service uses it to avoid staging a second one. */
    static volatile boolean alive;

    // The watch shows for the chosen time, then fades to black over the chosen time, then rests:
    // no rendering, no gyroscope, backlight at its minimum, and the keep-screen-on hold released
    // so the phone's own screen timeout can turn the display off. An app cannot turn it off
    // itself without device-admin rights. Any touch lifts the veil and restarts the clock.
    private final android.os.Handler timer = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable startFade = this::fadeOut;
    private final Runnable goDark = this::dark;
    private boolean fading, darkened, swallow;

    private WebView web;
    private Gestures.Torch torch;
    private TiltBridge tilt;
    private BatteryBridge battery;
    private float downX, downY;
    private long downAt;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        alive = true;
        // No turnScreenOn: this is staged while the screen is off and must not wake it.
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        torch = new Gestures.Torch(this);
        web = Watch3D.view(this, () -> { if (battery != null) battery.refresh(); },
                gesture -> Gestures.perform(this, gesture, this::unlock, torch));
        setContentView(web);
        Watch3D.immersive(getWindow());   // after setContentView, or getInsetsController() is null
        tilt = new TiltBridge(this, web);
        Watch3D.enablePinch(web);
        battery = new BatteryBridge(this, web);
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        if (tilt != null) tilt.start();
        if (battery != null) battery.start();
        wake();                                   // also arms the clock
    }

    @Override protected void onPause() {
        super.onPause();
        timer.removeCallbacks(startFade);
        timer.removeCallbacks(goDark);
        if (tilt != null) tilt.stop();
        if (battery != null) battery.stop();
        if (web != null) web.onPause();
    }

    private void arm() {
        timer.removeCallbacks(startFade);
        timer.removeCallbacks(goDark);
        int stay = Prefs.lockStay(this);
        if (stay > 0) timer.postDelayed(startFade, stay * 1000L);
    }

    private void fadeOut() {
        fading = true;
        int fade = Math.max(1, Prefs.lockFade(this));
        if (web != null) web.evaluateJavascript("window.__lock&&__lock.fade(" + fade + ")", null);
        timer.postDelayed(goDark, fade * 1000L + 200);
    }

    private void dark() {
        darkened = true;
        if (tilt != null) tilt.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;   // the minimum
        getWindow().setAttributes(lp);
    }

    private void wake() {
        timer.removeCallbacks(startFade);
        timer.removeCallbacks(goDark);
        if ((fading || darkened) && web != null) {
            web.evaluateJavascript("window.__lock&&__lock.wake()", null);
        }
        if (darkened && tilt != null) tilt.start();
        fading = darkened = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        arm();
    }

    @Override protected void onDestroy() {
        alive = false;
        if (torch != null) { torch.release(); torch = null; }
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    /** Leave: a gesture set to Unlock lands here; so does the swipe up below. */
    private void unlock() {
        if (isFinishing()) return;
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    /** A decisive upward swipe dismisses; anything else falls through to the watch. */
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (fading || darkened) {
                wake();                           // a touch in the dark only brings the watch back
                swallow = true;
            } else {
                arm();                            // any touch restarts the clock
                swallow = false;
            }
        }
        if (swallow) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) swallow = false;
            return true;
        }
        if (e.getPointerCount() > 1) return super.dispatchTouchEvent(e);   // leave pinches alone
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); downAt = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_UP:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                float need = getResources().getDisplayMetrics().heightPixels * 0.16f;
                if (dy < -need && Math.abs(dy) > Math.abs(dx) * 1.5f
                        && System.currentTimeMillis() - downAt < 1000) {
                    finish();
                    overridePendingTransition(0, android.R.anim.fade_out);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    /** Back must not dismiss a lock screen. */
    @Override public void onBackPressed() { }
}
