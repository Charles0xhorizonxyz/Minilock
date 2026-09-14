package com.miniscreen.minilock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Keeps the stand-in lock screen alive. ACTION_SCREEN_OFF/ON cannot be received from the
 * manifest since Oreo, so they are registered at runtime and the service stays resident.
 *
 * The watch is staged while the screen is OFF, so it is already in place when the screen
 * wakes. Launching it on SCREEN_ON raced the system's double-tap-power camera gesture: the
 * first press woke the phone and started the watch, the second started the camera, and
 * whichever came up last covered the other. Nothing launches after the camera now.
 *
 * Starting an activity from the background is blocked on Android 10+; the exemption comes from
 * SYSTEM_ALERT_WINDOW ("display over other apps"), which is why that permission is required.
 */
public class LockService extends Service {

    private static final String CHANNEL = "lock";
    private static final int NOTE_ID = 42;
    private BroadcastReceiver screen;

    // Wake on pickup. The phone's pick-up gesture is a low-power, one-shot, wake-up sensor (the
    // same one the system's lift-to-wake uses); it is armed whenever the screen goes off and,
    // when it fires, a short wake lock turns the screen on. The staged watch is already there.
    private SensorManager sensors;
    private Sensor pickup;
    private boolean pickupArmed;
    private final TriggerEventListener onPickup = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent event) {
            pickupArmed = false;
            if (Prefs.wakeOnPickup(LockService.this) && Prefs.lock(LockService.this)) wakeScreen();
            armPickup();
        }
    };

    private void armPickup() {
        if (pickupArmed || sensors == null || !Prefs.wakeOnPickup(this) || !Prefs.lock(this)) return;
        if (pickup == null) {
            pickup = sensors.getDefaultSensor(25);    // TYPE_PICK_UP_GESTURE, hidden in the SDK
            if (pickup == null) pickup = sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        }
        if (pickup != null && sensors.requestTriggerSensor(onPickup, pickup)) pickupArmed = true;
    }

    private void disarmPickup() {
        if (pickupArmed && sensors != null && pickup != null) sensors.cancelTriggerSensor(onPickup, pickup);
        pickupArmed = false;
    }

    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null || pm.isInteractive()) return;
        PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "minilock:pickup");
        lock.acquire(1500);                       // long enough for the display to come up
    }

    @Override public void onCreate() {
        super.onCreate();
        startForeground(NOTE_ID, notification());
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        screen = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!Prefs.lock(context)) return;
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) armPickup();
                // SCREEN_ON is only a fallback for when nothing was staged (the service came
                // up while the screen was already off). If the watch is there, leave it.
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())
                        && LockScreenActivity.alive) return;
                Intent lock = new Intent(context, LockScreenActivity.class);
                lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                context.startActivity(lock);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screen, filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) armPickup();   // started with the screen already off
        return START_STICKY;
    }

    @Override public void onDestroy() {
        disarmPickup();
        if (screen != null) { unregisterReceiver(screen); screen = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager != null
                && manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Lock screen", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("Miniscreen lock screen")
                .setContentText("Showing the dial when the screen wakes")
                .setSmallIcon(R.drawable.ic_minilock)
                .setOngoing(true)
                .build();
    }

    static void start(Context context) {
        Intent intent = new Intent(context, LockService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, LockService.class));
    }
}
