package com.miniscreen.atelier;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * A stand-in lock screen. This is NOT a real keyguard — Android does not allow one to be
 * replaced — so it only looks like the lock screen while the real one is switched off. Home
 * still escapes it, which no app can prevent without being Device Owner. It is a stopgap
 * until the dial ships as a ClockProviderPlugin in a custom OS build.
 *
 * The watch is the WebGL scene from tools/watch3d.html, running unchanged in a WebView from
 * assets. Nothing is fetched: three.js is bundled and the app holds no INTERNET permission.
 */
public class LockScreenActivity extends Activity implements SensorEventListener {

    /** Flip either of these if the tilt feels inverted on the device. */
    private static final float PITCH_SIGN = -1f, ROLL_SIGN = -1f;
    /** Radians of change before we bother crossing into JavaScript. */
    private static final float SEND_EPSILON = 0.004f;

    private WebView web;
    private SensorManager sensors;
    private Sensor rotation;
    private final float[] matrix = new float[9], orientation = new float[3];
    private boolean haveBaseline;
    private float basePitch, baseRoll, sentPitch = 9f, sentRoll = 9f;
    private float downX, downY;
    private long downAt;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(5894);
        }

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);                       // only ever loads file:///android_asset
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setDomStorageEnabled(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setBackgroundColor(0xFF000000);
        web.setHorizontalScrollBarEnabled(false);
        web.setVerticalScrollBarEnabled(false);
        web.loadUrl("file:///android_asset/lock.html");
        setContentView(web);

        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors != null) {
            // GAME_ROTATION_VECTOR leaves the magnetometer out, so there is no compass drift
            // or yaw correction fighting the tilt. Fall back only if the device lacks it.
            rotation = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rotation == null) rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        haveBaseline = false;                             // whatever angle it is held at now is level
        if (sensors != null && rotation != null) {
            sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME);
        }
        if (web != null) web.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
        if (sensors != null) sensors.unregisterListener(this);   // never hold the sensor while hidden
        if (web != null) web.onPause();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (web == null) return;
        SensorManager.getRotationMatrixFromVector(matrix, e.values);
        SensorManager.getOrientation(matrix, orientation);
        float pitch = orientation[1], roll = orientation[2];
        if (!haveBaseline) { basePitch = pitch; baseRoll = roll; haveBaseline = true; }
        float dp = PITCH_SIGN * (pitch - basePitch);
        float dr = ROLL_SIGN * (roll - baseRoll);
        if (Math.abs(dp - sentPitch) < SEND_EPSILON && Math.abs(dr - sentRoll) < SEND_EPSILON) return;
        sentPitch = dp; sentRoll = dr;
        web.evaluateJavascript("window.__lock&&__lock.setTilt(" + dp + "," + dr + ")", null);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /** A decisive upward swipe dismisses; anything else falls through to turning the watch. */
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
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
