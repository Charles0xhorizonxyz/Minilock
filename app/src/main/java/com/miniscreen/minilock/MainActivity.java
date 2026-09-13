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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final int ink = 0xFF090C10, gold = 0xFFC9AA7C, muted = 0xFF88919C;
    private LinearLayout content;
    private WebView hero;
    private TiltBridge tilt;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ink);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(18), dp(24), dp(24));
        scroll.addView(content);
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
        TextView title = text("The art of passing time.", 28, 0xFFF1EDE5);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        add(title, 43);
        TextView subtitle = text("A quiet moment. An extraordinary dial.", 12, muted);
        subtitle.setGravity(Gravity.CENTER);
        add(subtitle, 26);

        // The watch itself, in 3D. Tilt the phone and the light moves across the gold.
        hero = Watch3D.view(this);
        Watch3D.keepGestures(hero);   // or the ScrollView eats the pinch
        int width = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        add(hero, Math.min(430, width + 40));
        hero.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)));
        tilt = new TiltBridge(this, hero);

        TextView edition = text("EDITION 01", 10, gold);
        edition.setLetterSpacing(.15f);
        edition.setGravity(Gravity.CENTER);
        add(edition, 26);
        TextView turn = text("Drag to turn it over · pinch to zoom · double tap to reset", 11, muted);
        turn.setGravity(Gravity.CENTER);
        add(turn, 24);

        toggle("Ambient mode", "Dimmed dial · seconds hidden · gentle drift", "ambient",
                Prefs.ambient(this));
        toggle("Sweeping seconds", "A fluid, mechanical rhythm", "sweep", Prefs.sweep(this));
        toggle("Stand-in lock screen", "The 3D watch when the screen wakes", "lock",
                Prefs.lock(this));
        toggle("Text under the watch", "Date, next event, alerts and alarm", "card",
                Prefs.card(this));

        TextView overlay = text("Allow display over other apps   ↗", 12, gold);
        overlay.setPadding(0, dp(14), 0, 0);
        overlay.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        add(overlay, -2);
        TextView caution = text("Requires your real screen lock set to None. The phone is then not "
                + "actually locked — Home escapes this, and no app can stop that. A stopgap "
                + "until the custom build.", 11, 0xFFC98A8A);
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
                + "the 3D watch needs a WebView, which those surfaces cannot host.", 11, muted);
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
        labels.addView(text(title, 14, 0xFFE7E4DF));
        TextView description = text(desc, 10, muted);
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

    @Override protected void onResume() {
        super.onResume();
        if (hero != null) hero.onResume();
        if (tilt != null) tilt.start();
        // only run the watcher while it is both wanted and permitted
        if (Prefs.lock(this) && Settings.canDrawOverlays(this)) LockService.start(this);
        else if (!Prefs.lock(this)) LockService.stop(this);
    }

    @Override protected void onPause() {
        super.onPause();
        if (tilt != null) tilt.stop();
        if (hero != null) hero.onPause();   // no WebGL rendering behind other apps
    }

    @Override protected void onDestroy() {
        if (hero != null) { hero.destroy(); hero = null; }
        super.onDestroy();
    }
}
