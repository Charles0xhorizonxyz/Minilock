package com.miniscreen.miniwatch;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
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
    private InfoBridge info;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        alive = true;
        // No turnScreenOn: this is staged while the screen is off and must not wake it.
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        torch = new Gestures.Torch(this);
        web = Watch3D.view(this, () -> {
                    if (battery != null) battery.refresh();
                    if (info != null) info.refresh();
                    if (tilt != null) tilt.refresh();
                },
                gesture -> Gestures.perform(this, gesture, this::unlock, torch));
        setContentView(web);
        Watch3D.immersive(getWindow());   // after setContentView, or getInsetsController() is null
        tilt = new TiltBridge(this, web);
        Watch3D.enablePinch(web);
        battery = new BatteryBridge(this, web);
        info = new InfoBridge(this, web);
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) { web.onResume(); Watch3D.sync(web); }   // settings may have changed meanwhile
        if (tilt != null && Prefs.gyro(this)) tilt.start();
        if (battery != null) battery.start();
        if (info != null) info.start();
        wake();                                   // also arms the clock
    }

    @Override protected void onPause() {
        super.onPause();
        timer.removeCallbacks(startFade);
        timer.removeCallbacks(goDark);
        if (tilt != null) tilt.stop();
        if (battery != null) battery.stop();
        if (info != null) info.stop();
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
        if (darkened && tilt != null && Prefs.gyro(this)) tilt.start();
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

    /** Leave: only a dial gesture set to Unlock lands here. There is no swipe. */
    private void unlock() {
        if (isFinishing()) return;
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    /**
     * Touch only wakes the watch or restarts its clock; nothing here dismisses. A swipe up used
     * to, which meant a brush of the glass opened the phone; now only a dial gesture set to
     * Unlock does, and the app keeps one of the two gestures on Unlock. What an app cannot stop
     * is the system's own navigation: the bars are hidden and a swipe from the bottom edge only
     * reveals them at first, but a second one still goes Home.
     */
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
        return super.dispatchTouchEvent(e);
    }

    /** Back must not dismiss a lock screen. */
    @Override public void onBackPressed() { }
}
