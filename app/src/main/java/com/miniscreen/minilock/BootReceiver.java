package com.miniscreen.minilock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Brings the lock service back after a reboot, but only if it was wanted and still permitted. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (Prefs.lock(context) && Settings.canDrawOverlays(context)) LockService.start(context);
    }
}
