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
    private static final float PITCH_SIGN = -1f, YAW_SIGN = -1f;
    /** Radians of change before it is worth crossing into JavaScript. */
    private static final float EPSILON = 0.003f;

    private final SensorManager sensors;
    private final Sensor rotation;
    private final WebView web;
    private final float[] matrix = new float[9], orientation = new float[3];
    private boolean haveBaseline;
    private float basePitch, previousYaw, turned, sentPitch = 9f, sentYaw = 9f;

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
        haveBaseline = false;
        turned = 0f;
        sentPitch = sentYaw = 9f;
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
        float yaw = orientation[0], pitch = orientation[1];
        if (!haveBaseline) { basePitch = pitch; previousYaw = yaw; haveBaseline = true; }

        // Unwrap: azimuth jumps between +pi and -pi, and a raw delta there would snap the
        // camera right round. Accumulating the short way keeps the orbit continuous, so you
        // can keep turning past 360 degrees and it never resets.
        float step = yaw - previousYaw;
        if (step > Math.PI) step -= 2f * (float) Math.PI;
        if (step < -Math.PI) step += 2f * (float) Math.PI;
        turned += step;
        previousYaw = yaw;

        float dp = PITCH_SIGN * (pitch - basePitch);
        float dy = YAW_SIGN * turned;
        if (Math.abs(dp - sentPitch) < EPSILON && Math.abs(dy - sentYaw) < EPSILON) return;
        sentPitch = dp;
        sentYaw = dy;
        web.evaluateJavascript("window.__lock&&__lock.setTilt(" + dp + "," + dy + ")", null);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
