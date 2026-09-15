package com.miniscreen.miniwatch;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final int ink = 0xFF090C10, gold = 0xFFC9AA7C, muted = 0xFF88919C;
    private LinearLayout content;
    private WebView hero;
    private ZoomLayout zoom;
    private BatteryBridge battery;
    private InfoBridge info;
    private TiltBridge tilt;
    private SeekBar scale;                // the background slider, so a reset can move it
    private Switch dream, overlay;        // mirror system settings the app cannot change itself
    private Switch calendar, alerts;      // the two permissions behind the text under the watch
    private Spinner design;               // Factory or Custom; flips to Custom when anything is changed
    private Spinner flick, spinTwo;       // the two lock-screen gestures; one of them is always Unlock
    private int textStep;                 // 0..9 on the ladder; 4 is the design size
    private float textScale = 1f;
    private final java.util.List<TextView> sized = new java.util.ArrayList<>();
    private final java.util.List<ArrayAdapter<String>> adapters = new java.util.ArrayList<>();
    private final View[] rungs = new View[10];
    private boolean refreshing;           // so a programmatic refresh is not taken as a tap

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    /** Step 4 is the design size; each step is 7.5 percent, from 0.7 to 1.375. */
    private static float scaleFor(int step) { return 1f + (step - 4) * 0.075f; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        textStep = Math.max(0, Math.min(9, Prefs.textStep(this)));
        textScale = scaleFor(textStep);
        ZoomScrollView scroll = new ZoomScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);      // no scrollbar over the watch
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(ink);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(18), dp(24), dp(24));
        // ScrollView on the OUTSIDE, zoom inside it: the other way round, scaling put the
        // bottom of the content out of reach and divided every scroll by the zoom factor.
        zoom = new ZoomLayout(this);
        zoom.addView(content, new FrameLayout.LayoutParams(-1, -2));
        scroll.addView(zoom);
        scroll.setZoomLayout(zoom);   // so it knows not to claim pinches or sideways drags
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars =
                        insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });

        // Show the version in the app, so "is this actually the new build?" is never a guess.
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        TextView masthead = text("M I N I S C R E E N   ·   v" + version, 11, gold);
        masthead.setGravity(Gravity.CENTER);
        add(masthead, 28);
        // Touch readout. adb cannot fake a pinch on this device, so the phone reports what it
        // sees and you can read it back to me. Temporary.
        TextView touch = text("touch —", 10, 0xFF6C7883);
        touch.setGravity(Gravity.CENTER);
        touch.setTypeface(Typeface.MONOSPACE);
        add(touch, 18);
        zoom.setDebug(line -> touch.post(() -> touch.setText(line)));
        TextView title = text("The art of passing time.", 28, 0xFFF1EDE5);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        add(title, 43);
        TextView subtitle = text("A quiet moment. An extraordinary dial.", 13, muted);
        subtitle.setGravity(Gravity.CENTER);
        add(subtitle, 26);

        // The watch itself, in 3D. Tilt the phone and the light moves across the gold.
        hero = Watch3D.view(this, () -> {
            if (battery != null) battery.refresh();
            if (info != null) info.refresh();
            if (tilt != null) tilt.refresh();              // the page's baseline, at once
        });
        int width = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        add(hero, Math.min(560, (int) (width * 1.45f)));   // tall enough for the dial to be legible
        hero.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)));
        tilt = new TiltBridge(this, hero);
        battery = new BatteryBridge(this, hero);
        info = new InfoBridge(this, hero);

        // Right under the watch: the background scale and the reset, so a wrong colour or a
        // wrong placement is put right where you can see it change.
        background();
        TextView reset = text("Recenter only", 14, gold);
        reset.setGravity(Gravity.CENTER);
        reset.setBackground(background(0x00000000, gold));   // outlined: the quieter button
        margin(reset, 18, 0);
        reset.getLayoutParams().height = dp(48);
        reset.setOnClickListener(v -> Watch3D.recenter(hero));   // size and position, nothing else

        TextView preview = text("Preview fullscreen   ↗", 14, ink);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(background(gold, gold));
        margin(preview, 12, 10);
        preview.getLayoutParams().height = dp(52);
        preview.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)));

        textSize();
        String[] motionKeys = {"orbit", "hang3d", "hang2d"};
        String[] motionNames = {"Floating", "Held by the ring, 3D", "Held by the ring, 2D"};
        dropdown("Watch motion", "Floating free with the phone going round it, or hanging from the ring: "
                + "swinging in 3D and showing its sides, or swinging flat",
                motionNames, motionKeys, Gestures.indexOf(motionKeys, Prefs.motion(this)), key -> {
                    Prefs.setMotion(this, key);
                    Watch3D.applyFlat(hero);                   // live, on every surface from now on
                });
        Switch gyro = row("Gyroscope", "The watch holds still in the world as the phone moves");
        gyro.setChecked(Prefs.gyro(this));
        gyro.setOnCheckedChangeListener((v, on) -> {
            Prefs.get(this).edit().putBoolean("gyro", on).apply();
            if (tilt == null) return;
            if (on) tilt.start();
            else {
                tilt.stop();
                if (hero != null) hero.evaluateJavascript("window.__lock&&__lock.gyroReset()", null);
            }
        });
        design = dropdown("Design",
                "Factory: the watch as designed. Custom: your caseback choices. The background is yours under either",
                new String[] {"Factory", "Custom"}, new String[] {"factory", "custom"},
                Prefs.factory(this) ? 0 : 1, key -> {
                    Prefs.setFactory(this, "factory".equals(key));
                    if (hero != null) hero.reload();
                });
        toggle("Ambient mode", "Dimmed dial · seconds hidden · gentle drift", "ambient",
                Prefs.ambient(this));
        toggle("Sweeping seconds", "A fluid, mechanical rhythm", "sweep", Prefs.sweep(this));
        toggle("Stand-in lock screen", "The 3D watch when the screen wakes", "lock",
                Prefs.lock(this));
        Switch pickup = row("Wake on pickup", "Picking the phone up wakes the screen, no power button needed");
        pickup.setChecked(Prefs.wakeOnPickup(this));
        pickup.setOnCheckedChangeListener((v, on) -> {
            Prefs.get(this).edit().putBoolean("wake_pickup", on).apply();
            if (Prefs.lock(this) && Settings.canDrawOverlays(this)) LockService.start(this);   // re-arm now
        });
        String[] stayKeys = {"15", "30", "60", "120", "300", "600", "-1"};
        String[] stayNames = {"15 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes",
                              "10 minutes", "Always"};
        dropdown("Watch stays on for", "Then it fades to black on the lock screen; any touch brings it back",
                stayNames, stayKeys, Gestures.indexOf(stayKeys, String.valueOf(Prefs.lockStay(this))),
                key -> Prefs.setLockStay(this, Integer.parseInt(key)));
        String[] fadeKeys = {"1", "3", "5", "10", "20", "30"};
        String[] fadeNames = {"1 second", "3 seconds", "5 seconds", "10 seconds", "20 seconds", "30 seconds"};
        dropdown("Fade to black over", "How long the watch takes to go dark",
                fadeNames, fadeKeys, Gestures.indexOf(fadeKeys, String.valueOf(Prefs.lockFade(this))),
                key -> Prefs.setLockFade(this, Integer.parseInt(key)));
        flick = choice("Flick right to left", "One turn of the dial on the lock screen · one of these two is always Unlock", Gestures.LEFT1);
        spinTwo = choice("Spin left to right, two turns", "One hard flick that turns the dial twice on the lock screen", Gestures.RIGHT2);
        toggle("Text under the watch", "Date, next event, alerts and alarm", "card",
                Prefs.card(this));
        // The event and the alerts are the phone's own, and each needs a permission the user
        // grants once. The alarm needs none. Nothing leaves the phone.
        calendar = mirror("Calendar",
                "The next event under the watch \u00b7 tap to allow reading the calendar",
                this::calendarAllowed, this::askCalendar);
        alerts = mirror("Notifications",
                "The alerts under the watch and on the dial \u00b7 tap to open the phone's notification access page",
                () -> InfoBridge.alertsAllowed(this),
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        screensaver();

        overlay = mirror("Display over other apps",
                "Needed for the stand-in lock screen · tap to open the phone's permission page",
                () -> Settings.canDrawOverlays(this),
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        TextView caution = text("Requires your real screen lock set to None. The phone is then not "
                + "actually locked — Home escapes this, and no app can stop that. A stopgap "
                + "until the custom build.", 12, 0xFFC98A8A);
        caution.setLineSpacing(dp(3), 1);
        caution.setPadding(0, dp(16), 0, dp(28));
        add(caution, -2);
    }

    /**
     * Whether Android's screensaver is Miniwatch. An app cannot set the screensaver itself, so
     * the switch shows the real state and opens the system page to change it.
     */
    private boolean isScreensaver() {
        try {
            String on = Settings.Secure.getString(getContentResolver(), "screensaver_enabled");
            String which = Settings.Secure.getString(getContentResolver(), "screensaver_components");
            return "1".equals(on) && which != null && which.contains(getPackageName() + "/");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void screensaver() {
        dream = mirror("Screensaver",
                "Miniwatch as the Android screensaver · tap to open the phone's screensaver settings",
                this::isScreensaver, this::openScreensaverSettings);
    }

    /**
     * A switch that mirrors a system setting the app cannot change itself. The switch shows
     * the real state, and the whole row opens the system page where it is changed.
     */
    private Switch mirror(String title, String desc, java.util.function.BooleanSupplier state,
                          Runnable open) {
        Switch control = row(title, desc);
        control.setChecked(state.getAsBoolean());
        control.setOnCheckedChangeListener((v, on) -> {
            if (refreshing) return;
            refreshing = true;
            v.setChecked(state.getAsBoolean());   // the system decides; show its state, not the tap
            refreshing = false;
            open.run();
        });
        View line = (View) control.getParent();   // the whole row is the way in
        line.setClickable(true);
        line.setOnClickListener(v -> open.run());
        return control;
    }

    private boolean calendarAllowed() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Ask once; if the phone will no longer ask, open the app's own permission page instead. */
    private void askCalendar() {
        if (calendarAllowed()) return;
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) || !askedCalendar) {
            askedCalendar = true;
            requestPermissions(new String[] {Manifest.permission.READ_CALENDAR}, 7);
        } else {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }
    private boolean askedCalendar;

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (calendar != null) { refreshing = true; calendar.setChecked(calendarAllowed()); refreshing = false; }
        if (info != null) info.refresh();
    }

    private void openScreensaverSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (android.content.ActivityNotFoundException e) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage("This device does not expose Android screensaver settings.")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void toggle(String title, String desc, String key, boolean checked) {
        Switch control = row(title, desc);
        control.setChecked(checked);
        control.setOnCheckedChangeListener((v, on) -> {
            Prefs.get(this).edit().putBoolean(key, on).apply();
            if ("card".equals(key)) Watch3D.applyCard(hero);   // no need to wait for a reload
        });
    }

    /** The Design row follows a change made elsewhere on this screen. */
    private void markCustom() {
        if (design == null) return;
        refreshing = true;
        design.setSelection(1, false);
        refreshing = false;
    }

    /**
     * A settings row with a dropdown of lock-screen actions on the right. One of the two
     * gestures is always Unlock: choosing anything else here moves the other row to it.
     */
    private Spinner choice(String title, String desc, String prefKey) {
        return dropdown(title, desc, Gestures.NAMES, Gestures.KEYS,
                Gestures.indexOf(Gestures.actionFor(this, prefKey)),
                key -> {
                    String moved = Gestures.choose(this, prefKey, key);
                    if (moved == null) return;
                    Spinner other = Gestures.LEFT1.equals(moved) ? flick : spinTwo;
                    if (other == null) return;
                    refreshing = true;
                    other.setSelection(Gestures.indexOf("unlock"), false);
                    refreshing = false;
                });
    }

    /** A settings row with a dropdown on the right; onPick gets the chosen key, user taps only. */
    private Spinner dropdown(String title, String desc, String[] names, String[] keys,
                             int selected, java.util.function.Consumer<String> onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(15), 0, dp(10));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 16, 0xFFE7E4DF));
        TextView description = text(desc, 12, muted);
        description.setPadding(0, dp(5), 0, 0);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        Spinner pick = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names) {
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, convert, parent);
                t.setText(names[pos] + "  \u25BE");
                t.setTextColor(gold);
                t.setTextSize(14 * textScale);
                t.setGravity(Gravity.END);
                t.setPadding(dp(8), dp(4), 0, dp(4));       // flush with the switches' edge
                return t;
            }
            @Override public View getDropDownView(int pos, View convert, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(pos, convert, parent);
                t.setTextColor(0xFFE7E4DF);
                t.setTextSize(15 * textScale);
                t.setPadding(dp(18), dp(14), dp(18), dp(14));
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapters.add(adapter);
        pick.setAdapter(adapter);
        pick.setBackground(null);                          // the text carries its own arrow
        pick.setPadding(0, 0, 0, 0);                       // the old background's padding stays otherwise
        pick.setGravity(Gravity.END);
        pick.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF131921));
        // The list opens as exactly the spinner's own box, so it ends where the text ends.
        pick.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        pick.setSelection(selected, false);
        pick.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private int last = selected;
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (refreshing) { last = pos; return; }    // a refresh, not a tap
                if (pos == last) return;                   // the initial selection
                last = pos;
                onPick.accept(keys[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        row.addView(pick, new LinearLayout.LayoutParams(dp(176), -2));   // wide enough for the list
        add(row, -2);
        View line = new View(this);
        line.setBackgroundColor(0xFF252A30);
        content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        return pick;
    }

    /** A settings row with a switch on the right; the caller decides what the switch does. */
    private Switch row(String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(15), 0, dp(10));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 16, 0xFFE7E4DF));
        TextView description = text(desc, 12, muted);
        description.setPadding(0, dp(5), 0, 0);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(this);
        control.setContentDescription(title);
        control.setThumbTintList(android.content.res.ColorStateList.valueOf(gold));
        row.addView(control, new LinearLayout.LayoutParams(dp(52), dp(48)));
        add(row, -2);
        View line = new View(this);
        line.setBackgroundColor(0xFF252A30);
        content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        return control;
    }

    /** Ivory, through paper tones, to charcoal: the studio behind the watch, on every surface. */
    private void background() {
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(0, dp(15), 0, dp(4));
        labels.addView(text("Background", 16, 0xFFE7E4DF));
        TextView description = text("Ivory to charcoal through natural tones, behind the watch everywhere", 12, muted);
        description.setPadding(0, dp(5), 0, 0);
        labels.addView(description);
        add(labels, -2);

        scale = new SeekBar(this);
        scale.setMax(100);
        // Eight even stops of seamless-paper tones; the page maps the value to the same colours
        // (PAPERS in the generator), so the track shows exactly what the backdrop will be.
        GradientDrawable track = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xFFEEE8DC, 0xFFC48B80, 0xFFC4A86A, 0xFF8FA48A,
                           0xFF6F9A9C, 0xFF5E7394, 0xFF6B5A7A, 0xFF1E2024});
        track.setCornerRadius(dp(4));
        track.setSize(dp(200), dp(8));
        track.setStroke(dp(1), 0xFF3A424B);            // so the black end still has an edge
        scale.setProgressDrawable(track);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            scale.setMinHeight(dp(8));
            scale.setMaxHeight(dp(8));
        }
        scale.setThumbTintList(android.content.res.ColorStateList.valueOf(gold));
        scale.setPadding(dp(14), dp(12), dp(14), dp(12));   // room for the thumb at both ends
        scale.setProgress(100 - Prefs.background(this));     // white on the left, black on the right
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                Prefs.setBackground(MainActivity.this, 100 - p);
                Watch3D.applyBackground(hero);              // live, so you see it as you slide
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        add(scale, 44);
        View line = new View(this);
        line.setBackgroundColor(0xFF252A30);
        content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size * textScale);
        t.setTag(size);                            // the design size, so a new scale can be applied
        t.setTextColor(color);
        sized.add(t);
        return t;
    }

    /** A ten-step ladder with A- and A+ for every title, description and button on this screen. */
    private void textSize() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(15), 0, dp(10));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Text size", 16, 0xFFE7E4DF));
        TextView description = text("Ten steps, for everything on this screen", 12, muted);
        description.setPadding(0, dp(5), 0, 0);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout ladder = new LinearLayout(this);
        ladder.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = text("A\u2212", 16, gold);
        minus.setPadding(dp(10), dp(8), dp(10), dp(8));
        minus.setOnClickListener(v -> stepText(-1));
        ladder.addView(minus);
        LinearLayout bars = new LinearLayout(this);
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 10; i++) {
            View rung = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(5), dp(6 + i));
            lp.setMargins(dp(1), 0, dp(1), 0);
            bars.addView(rung, lp);
            rungs[i] = rung;
        }
        ladder.addView(bars);
        TextView plus = text("A+", 16, gold);
        plus.setPadding(dp(10), dp(8), dp(10), dp(8));
        plus.setOnClickListener(v -> stepText(1));
        ladder.addView(plus);
        row.addView(ladder, new LinearLayout.LayoutParams(-2, -2));
        add(row, -2);
        View line = new View(this);
        line.setBackgroundColor(0xFF252A30);
        content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        paintLadder();
    }

    private void paintLadder() {
        for (int i = 0; i < 10; i++) rungs[i].setBackgroundColor(i <= textStep ? gold : 0xFF3A424B);
    }

    private void stepText(int by) {
        int next = Math.max(0, Math.min(9, textStep + by));
        if (next == textStep) return;
        textStep = next;
        textScale = scaleFor(next);
        Prefs.setTextStep(this, next);
        for (TextView t : sized) {
            Object base = t.getTag();
            if (base instanceof Integer) t.setTextSize((Integer) base * textScale);
        }
        for (ArrayAdapter<String> a : adapters) a.notifyDataSetChanged();   // dropdowns re-read the scale
        paintLadder();
    }

    private GradientDrawable background(int color, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(9));
        bg.setStroke(dp(1), stroke);
        return bg;
    }

    private void add(View v, int height) {
        content.addView(v, new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(height)));
    }

    private void margin(View v, int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        lp.bottomMargin = dp(bottom);
        content.addView(v, lp);
    }

    @Override public void onBackPressed() {
        if (zoom != null && zoom.isZoomed()) { zoom.reset(); return; }   // back un-zooms first
        super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshing = true;                        // the system may have been changed meanwhile
        if (dream != null) dream.setChecked(isScreensaver());
        if (overlay != null) overlay.setChecked(Settings.canDrawOverlays(this));
        if (calendar != null) calendar.setChecked(calendarAllowed());
        if (alerts != null) alerts.setChecked(InfoBridge.alertsAllowed(this));
        if (design != null) design.setSelection(Prefs.factory(this) ? 0 : 1, false);
        refreshing = false;
        if (hero != null) hero.onResume();
        if (tilt != null && Prefs.gyro(this)) tilt.start();
        if (battery != null) battery.start();
        if (info != null) info.start();
        // only run the watcher while it is both wanted and permitted
        if (Prefs.lock(this) && Settings.canDrawOverlays(this)) LockService.start(this);
        else if (!Prefs.lock(this)) LockService.stop(this);
    }

    @Override protected void onPause() {
        super.onPause();
        if (tilt != null) tilt.stop();
        if (battery != null) battery.stop();
        if (info != null) info.stop();
        if (hero != null) hero.onPause();   // no WebGL rendering behind other apps
    }

    @Override protected void onDestroy() {
        if (hero != null) { hero.destroy(); hero = null; }
        super.onDestroy();
    }
}
