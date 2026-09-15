package com.miniscreen.miniwatch;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Counts the phone's notifications for the guichet on the dial and the alerts row under the
 * watch. The user switches it on under the phone's notification-access settings; until then
 * {@link #snapshot()} is null and nothing is shown. Nothing is read beyond the posting app's
 * name: no titles, no text.
 *
 * Only clearable notifications count: the ongoing ones (a running node, a player, a service)
 * are furniture, not news. Group summaries are skipped so a conversation counts once.
 */
public final class AlertsListener extends NotificationListenerService {

    /** What the dial shows: how many, and who, most recent first, at most three names. */
    static final class Snapshot {
        final int count;
        final List<String> senders;
        Snapshot(int count, List<String> senders) { this.count = count; this.senders = senders; }
    }

    private static volatile Snapshot current;
    private static final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();

    static Snapshot snapshot() { return current; }
    static void listen(Runnable r) { listeners.add(r); }
    static void unlisten(Runnable r) { listeners.remove(r); }

    @Override public void onListenerConnected() { update(); }

    @Override public void onListenerDisconnected() {
        current = null;
        for (Runnable r : listeners) r.run();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { update(); }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) { update(); }

    private void update() {
        StatusBarNotification[] all;
        try { all = getActiveNotifications(); } catch (RuntimeException e) { return; }
        if (all == null) all = new StatusBarNotification[0];
        List<StatusBarNotification> live = new ArrayList<>();
        for (StatusBarNotification n : all) {
            if (n == null || !n.isClearable()) continue;
            if ((n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            if (getPackageName().equals(n.getPackageName())) continue;
            live.add(n);
        }
        Collections.sort(live, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        List<String> senders = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (StatusBarNotification n : live) {
            String name;
            try {
                name = pm.getApplicationLabel(pm.getApplicationInfo(n.getPackageName(), 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                name = n.getPackageName();
            }
            if (!senders.contains(name)) senders.add(name);
            if (senders.size() == 3) break;
        }
        current = new Snapshot(live.size(), Collections.unmodifiableList(senders));
        for (Runnable r : listeners) r.run();
    }

    /** Kept for tests from adb: the names as one line. */
    static String describe() {
        Snapshot s = current;
        return s == null ? "off" : s.count + " " + Arrays.toString(s.senders.toArray());
    }
}
