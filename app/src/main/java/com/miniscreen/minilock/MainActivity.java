package com.miniscreen.minilock;

import android.app.Activity;
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
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final int ink = 0xFF090C10, gold = 0xFFC9AA7C, muted = 0xFF88919C;
    private LinearLayout content;
    private WebView hero;
    private ZoomLayout zoom;
    private BatteryBridge battery;
    private TiltBridge tilt;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        hero = Watch3D.view(this, () -> { if (battery != null) battery.refresh(); });
        int width = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        add(hero, Math.min(560, (int) (width * 1.45f)));   // tall enough for the dial to be legible
        hero.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)));
        tilt = new TiltBridge(this, hero);
        battery = new BatteryBridge(this, hero);

        TextView edition = text("EDITION 01", 11, gold);
        edition.setLetterSpacing(.15f);
        edition.setGravity(Gravity.CENTER);
        add(edition, 26);
        TextView turn = text("Pinch to zoom · two fingers to place the watch · drag it to turn it over", 12, muted);
        turn.setGravity(Gravity.CENTER);
        add(turn, 24);

        toggle("Ambient mode", "Dimmed dial · seconds hidden · gentle drift", "ambient",
                Prefs.ambient(this));
        toggle("Sweeping seconds", "A fluid, mechanical rhythm", "sweep", Prefs.sweep(this));
        toggle("Stand-in lock screen", "The 3D watch when the screen wakes", "lock",
                Prefs.lock(this));
        toggle("Text under the watch", "Date, next event, alerts and alarm", "card",
                Prefs.card(this));

        TextView resetWatch = text("Reset watch size and position", 14, gold);
        resetWatch.setPadding(0, dp(16), 0, 0);
        resetWatch.setOnClickListener(v -> {
            Prefs.setPlacement(this, "");          // a bad placement would otherwise be permanent
            if (hero != null) hero.reload();
        });
        add(resetWatch, -2);

        TextView overlay = text("Allow display over other apps   ↗", 14, gold);
        overlay.setPadding(0, dp(14), 0, 0);
        overlay.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        add(overlay, -2);
        TextView caution = text("Requires your real screen lock set to None. The phone is then not "
                + "actually locked — Home escapes this, and no app can stop that. A stopgap "
                + "until the custom build.", 12, 0xFFC98A8A);
        caution.setLineSpacing(dp(3), 1);
        caution.setPadding(0, dp(8), 0, 0);
        add(caution, -2);

        TextView preview = text("Preview fullscreen   ↗", 14, ink);
        preview.setGravity(Gravity.CENTER);
        preview.setBackground(background(gold, gold));
        margin(preview, 20, 0);
        preview.getLayoutParams().height = dp(52);
        preview.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)));

        TextView activate = text("Set as Android screensaver", 13, gold);
        activate.setGravity(Gravity.CENTER);
        add(activate, 52);
        activate.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            } catch (android.content.ActivityNotFoundException e) {
                new android.app.AlertDialog.Builder(this)
                        .setMessage("This device does not expose Android screensaver settings. "
                                + "You can still use the fullscreen preview.")
                        .setPositiveButton("OK", null).show();
            }
        });
        TextView note = text("The screensaver and live wallpaper draw the flat Canvas dial; "
                + "the 3D watch needs a WebView, which those surfaces cannot host.", 12, muted);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(3), 1);
        add(note, -2);
    }

    private void toggle(String title, String desc, String key, boolean checked) {
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
        control.setChecked(checked);
        control.setContentDescription(title);
        control.setThumbTintList(android.content.res.ColorStateList.valueOf(gold));
        control.setOnCheckedChangeListener((v, on) -> {
            Prefs.get(this).edit().putBoolean(key, on).apply();
            if ("card".equals(key)) Watch3D.applyCard(hero);   // no need to wait for a reload
        });
        row.addView(control, new LinearLayout.LayoutParams(dp(52), dp(48)));
        add(row, -2);
        View line = new View(this);
        line.setBackgroundColor(0xFF252A30);
        content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
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
        if (hero != null) hero.onResume();
        if (tilt != null) tilt.start();
        if (battery != null) battery.start();
        // only run the watcher while it is both wanted and permitted
        if (Prefs.lock(this) && Settings.canDrawOverlays(this)) LockService.start(this);
        else if (!Prefs.lock(this)) LockService.stop(this);
    }

    @Override protected void onPause() {
        super.onPause();
        if (tilt != null) tilt.stop();
        if (battery != null) battery.stop();
        if (hero != null) hero.onPause();   // no WebGL rendering behind other apps
    }

    @Override protected void onDestroy() {
        if (hero != null) { hero.destroy(); hero = null; }
        super.onDestroy();
    }
}
