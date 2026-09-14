package com.miniscreen.minilock;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.WebView;

/** Fullscreen preview of the 3D pocket watch — the same scene the lock screen shows. */
public class PreviewActivity extends Activity {

    private WebView web;
    private TiltBridge tilt;
    private BatteryBridge battery;
    private float downX, downY;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!Prefs.threeD(this)) {                // the flat dial: no WebGL, no gyroscope, no bridges
            WatchView flat = new WatchView(this);
            flat.setExhibition(true);
            setContentView(flat);
            Watch3D.immersive(getWindow());
            return;
        }
        web = Watch3D.view(this, () -> {
            if (battery != null) battery.refresh();
            // Gyroscope readout. Nothing on this side can move the phone, so the page reports
            // what the sensor delivers and the user reads it back. Temporary, preview only.
            web.evaluateJavascript("window.__lock&&__lock.setDebug(true)", null);
        });
        web.setContentDescription("Fullscreen 3D pocket watch. Swipe up to return.");
        setContentView(web);
        Watch3D.immersive(getWindow());     // after setContentView, or the decor view is null
        tilt = new TiltBridge(this, web);
        Watch3D.enablePinch(web);
        battery = new BatteryBridge(this, web);
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        if (tilt != null && Prefs.gyro(this)) tilt.start();
        if (battery != null) battery.start();
    }

    @Override protected void onPause() {
        super.onPause();
        if (tilt != null) tilt.stop();
        if (battery != null) battery.stop();
        if (web != null) web.onPause();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    /** Swipe up to leave; horizontal drags still turn the watch over. */
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getPointerCount() > 1) return super.dispatchTouchEvent(e);   // leave pinches alone
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX(); downY = e.getY();
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (dy < -getResources().getDisplayMetrics().heightPixels * 0.16f
                    && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                finish();
                return true;
            }
        }
        return super.dispatchTouchEvent(e);
    }
}
