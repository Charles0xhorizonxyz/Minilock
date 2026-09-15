package com.miniscreen.miniwatch;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.webkit.WebView;

/**
 * Feeds device orientation to the watch so it holds still in the world while the phone moves
 * around it. Shared by every surface that shows the watch.
 *
 * GAME_ROTATION_VECTOR leaves the magnetometer out, so there is no compass drift or yaw
 * correction fighting the tilt.
 */
final class TiltBridge implements SensorEventListener {

    /** Quaternion change before it is worth crossing into JavaScript. */
    private static final float EPSILON = 0.002f;

    private final SensorManager sensors;
    private final Sensor rotation;
    private final WebView web;
    private final float[] quaternion = new float[4];
    private float sentW = 9f, sentX = 9f, sentY = 9f, sentZ = 9f;
    private float lastW, lastX, lastY, lastZ;
    private boolean haveLast;

    TiltBridge(Context context, WebView web) {
        this.web = web;
        sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = null;
        if (sensors != null) {
            s = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (s == null) s = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        rotation = s;
    }

    /** Whatever way the phone is pointing when this starts becomes head-on. */
    void start() {
        sentW = sentX = sentY = sentZ = 9f;
        if (sensors != null && rotation != null) {
            sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /**
     * Send the latest orientation again. The first sample usually arrives before the page has
     * loaded and is lost; with the phone perfectly still no other follows, so the page would
     * sit without a baseline until the first movement. Called once the page is ready.
     */
    void refresh() {
        if (!haveLast || web == null) return;
        sentW = lastW; sentX = lastX; sentY = lastY; sentZ = lastZ;
        web.evaluateJavascript("window.__lock&&__lock.setQuat("
                + lastW + "," + lastX + "," + lastY + "," + lastZ + ")", null);
    }

    /** Never hold the sensor while the watch is not on screen. */
    void stop() {
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (web == null) return;
        // Send the quaternion straight through. Converting to Euler angles here was the bug:
        // a phone held upright sits on the gimbal-lock singularity, so azimuth went degenerate
        // and the orbit kept collapsing back to centre.
        SensorManager.getQuaternionFromVector(quaternion, e.values);   // [w, x, y, z]
        float w = quaternion[0], x = quaternion[1], y = quaternion[2], z = quaternion[3];
        lastW = w; lastX = x; lastY = y; lastZ = z; haveLast = true;
        float moved = Math.abs(w - sentW) + Math.abs(x - sentX)
                + Math.abs(y - sentY) + Math.abs(z - sentZ);
        if (moved < EPSILON) return;
        sentW = w; sentX = x; sentY = y; sentZ = z;
        web.evaluateJavascript("window.__lock&&__lock.setQuat("
                + w + "," + x + "," + y + "," + z + ")", null);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
