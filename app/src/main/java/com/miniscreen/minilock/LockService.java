package com.miniscreen.minilock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

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

    @Override public void onCreate() {
        super.onCreate();
        startForeground(NOTE_ID, notification());
        screen = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!Prefs.lock(context)) return;
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
        return START_STICKY;
    }

    @Override public void onDestroy() {
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
