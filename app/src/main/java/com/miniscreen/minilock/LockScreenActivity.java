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

    private WebView web;
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
        web = Watch3D.view(this, () -> { if (battery != null) battery.refresh(); });
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
    }

    @Override protected void onPause() {
        super.onPause();
        if (tilt != null) tilt.stop();
        if (battery != null) battery.stop();
        if (web != null) web.onPause();
    }

    @Override protected void onDestroy() {
        alive = false;
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    /** A decisive upward swipe dismisses; anything else falls through to the watch. */
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
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
