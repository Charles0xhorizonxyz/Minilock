package com.miniscreen.miniwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.webkit.WebView;

/**
 * Puts the phone's real charge on the power-reserve counter.
 *
 * ACTION_BATTERY_CHANGED is sticky, so registering returns the current state immediately and
 * there is no empty first frame. Needs no permission.
 */
final class BatteryBridge extends BroadcastReceiver {

    private final Context context;
    private final WebView web;
    private boolean registered;
    private int lastLevel = -1;
    private boolean lastCharging;

    BatteryBridge(Context context, WebView web) {
        this.context = context.getApplicationContext();
        this.web = web;
    }

    void start() {
        if (registered) return;
        // the sticky broadcast comes straight back, so the dial is correct on the first frame
        Intent now = context.registerReceiver(this, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registered = true;
        if (now != null) push(now);
    }

    void stop() {
        if (!registered) return;
        try { context.unregisterReceiver(this); } catch (IllegalArgumentException ignored) { }
        registered = false;
    }

    /**
     * Push again, unconditionally.
     *
     * The sticky broadcast arrives the instant we register, which beats the WebView finishing
     * its page load -- so the first value is sent into a page that has no __lock yet and is
     * silently lost. The host calls this once the page is ready.
     */
    void refresh() {
        Intent now = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (now == null) return;
        lastLevel = -1;                                  // defeat the de-duplication
        push(now);
    }

    @Override public void onReceive(Context c, Intent intent) { push(intent); }

    private void push(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return;
        int percent = Math.round(level * 100f / scale);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        if (percent == lastLevel && charging == lastCharging) return;
        lastLevel = percent;
        lastCharging = charging;
        if (web != null) {
            web.evaluateJavascript(
                    "window.__lock&&__lock.setBattery(" + percent + "," + charging + ")", null);
        }
    }
}
