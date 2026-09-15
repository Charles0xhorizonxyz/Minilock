package com.miniscreen.miniwatch;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * Puts the phone's own next event, alerts and alarm under the watch, where the prototype
 * showed demo text. Nothing leaves the phone: the calendar is read locally, the alerts come
 * from {@link AlertsListener}, and the alarm from AlarmManager, which needs no permission.
 *
 * The page gets a small JSON through __lock.setInfo: {event:{time,text}, alerts:{n,senders},
 * alarm:"06:45"}, each null when there is nothing to say or no permission to look.
 */
final class InfoBridge {

    private static final long EVERY = 60_000L;      // the minute is the finest thing shown

    private final Context context;
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refresh;
    private final Runnable onAlerts = () -> handler.post(this::refresh);
    private boolean running;

    InfoBridge(Context context, WebView web) {
        this.context = context.getApplicationContext();
        this.web = web;
    }

    void start() {
        if (running) return;
        running = true;
        AlertsListener.listen(onAlerts);
        refresh();
    }

    void stop() {
        running = false;
        AlertsListener.unlisten(onAlerts);
        handler.removeCallbacks(tick);
    }

    /** Build and push now, then again every minute while running. Safe to call any time. */
    void refresh() {
        handler.removeCallbacks(tick);
        if (web != null) {
            web.evaluateJavascript("window.__lock&&__lock.setInfo("
                    + JSONObject.quote(build().toString()) + ")", null);
        }
        if (running) handler.postDelayed(tick, EVERY);
    }

    private JSONObject build() {
        JSONObject o = new JSONObject();
        try {
            o.put("event", event());
            o.put("alerts", alerts());
            o.put("alarm", alarm());
        } catch (org.json.JSONException ignored) { }
        return o;
    }

    /** The first calendar instance still running or starting within a day, or null. */
    private JSONObject event() throws org.json.JSONException {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return null;
        long now = System.currentTimeMillis();
        String[] projection = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION};
        String title = null, where = null;
        long begin = Long.MAX_VALUE;
        boolean allDay = false;
        try (Cursor c = CalendarContract.Instances.query(context.getContentResolver(), projection,
                now, now + 24 * 3600_000L)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                long b = c.getLong(1), e = c.getLong(2);
                if (e <= now || b >= begin) continue;      // over already, or later than what we have
                begin = b;
                title = c.getString(0);
                where = c.getString(4);
                allDay = c.getInt(3) != 0;
            }
        } catch (RuntimeException e) {
            return null;
        }
        if (title == null && begin == Long.MAX_VALUE) return null;
        String text = (title == null || title.trim().isEmpty()) ? "(no title)" : title.trim();
        if (where != null && !where.trim().isEmpty()) text += " · " + where.trim();
        JSONObject ev = new JSONObject();
        ev.put("time", allDay ? dayWord(begin, now, true) : when(begin, now));
        ev.put("text", text);
        return ev;
    }

    /** The alerts as the listener sees them, or null when it is not allowed to see them. */
    private JSONObject alerts() throws org.json.JSONException {
        AlertsListener.Snapshot s = AlertsListener.snapshot();
        if (s == null) return null;
        JSONObject a = new JSONObject();
        a.put("n", s.count);
        JSONArray senders = new JSONArray();
        for (String name : s.senders) senders.put(name);
        a.put("senders", senders);
        return a;
    }

    /** The next alarm clock, as the phone's clock app set it, or null. */
    private String alarm() {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo next = am == null ? null : am.getNextAlarmClock();
        if (next == null) return null;
        return when(next.getTriggerTime(), System.currentTimeMillis());
    }

    /** "09:30" today, in the phone's own clock format; "Thu 09:30" on another day. */
    private String when(long t, long now) {
        String time = DateFormat.getTimeFormat(context).format(new java.util.Date(t));
        return sameDay(t, now) ? time : dayWord(t, now, false) + " " + time;
    }

    private String dayWord(long t, long now, boolean allDay) {
        if (sameDay(t, now)) return allDay ? "Today" : "";
        if (sameDay(t, now + 24 * 3600_000L)) return "Tomorrow";
        return DateFormat.format("EEE", t).toString();
    }

    private static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR)
                && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    /** For the settings screen: is the alerts listener switched on in the phone's settings? */
    static boolean alertsAllowed(Context context) {
        android.app.NotificationManager nm = context.getSystemService(android.app.NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(
                new android.content.ComponentName(context, AlertsListener.class));
    }

}
