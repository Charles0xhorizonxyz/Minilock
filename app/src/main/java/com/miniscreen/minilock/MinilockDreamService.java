package com.miniscreen.minilock;

import android.service.dreams.DreamService;

public class MinilockDreamService extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(!Prefs.ambient(this));
        WatchView face = new WatchView(this);
        face.setExhibition(true);
        setContentView(face);
    }
}
