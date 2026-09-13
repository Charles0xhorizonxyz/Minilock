package com.miniscreen.minilock;

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

    /** Flip either if the tilt reads inverted on a given device. */
    private static final float PITCH_SIGN = -1f, ROLL_SIGN = -1f;
    /** Radians of change before it is worth crossing into JavaScript. */
    private static final float EPSILON = 0.004f;

    private final SensorManager sensors;
    private final Sensor rotation;
    private final WebView web;
    private final float[] matrix = new float[9], orientation = new float[3];
    private boolean haveBaseline;
    private float basePitch, baseRoll, sentPitch = 9f, sentRoll = 9f;

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

    /** Whatever angle the phone is held at when this starts becomes level. */
    void start() {
        haveBaseline = false;
        sentPitch = sentRoll = 9f;
        if (sensors != null && rotation != null) {
            sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /** Never hold the sensor while the watch is not on screen. */
    void stop() {
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (web == null) return;
        SensorManager.getRotationMatrixFromVector(matrix, e.values);
        SensorManager.getOrientation(matrix, orientation);
        float pitch = orientation[1], roll = orientation[2];
        if (!haveBaseline) { basePitch = pitch; baseRoll = roll; haveBaseline = true; }
        float dp = PITCH_SIGN * (pitch - basePitch);
        float dr = ROLL_SIGN * (roll - baseRoll);
        if (Math.abs(dp - sentPitch) < EPSILON && Math.abs(dr - sentRoll) < EPSILON) return;
        sentPitch = dp;
        sentRoll = dr;
        web.evaluateJavascript("window.__lock&&__lock.setTilt(" + dp + "," + dr + ")", null);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
