package com.miniscreen.minilock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Brings the lock service back after a reboot or an update of this app, but only if it was
 * wanted and still permitted. Without the update case, nothing was staged after an install
 * until the app was opened once.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (Prefs.lock(context) && Settings.canDrawOverlays(context)) LockService.start(context);
    }
}
