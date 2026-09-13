package com.miniscreen.atelier;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public class PreviewActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
            getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(5894);
        }
        WatchView face = new WatchView(this);
        face.setExhibition(true);
        face.setContentDescription("Fullscreen watch preview. Tap to return to settings.");
        face.setOnClickListener(v -> finish());
        setContentView(face);
    }
}
