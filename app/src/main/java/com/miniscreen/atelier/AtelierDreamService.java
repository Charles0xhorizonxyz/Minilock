package com.miniscreen.atelier;

import android.service.dreams.DreamService;

public class AtelierDreamService extends DreamService {
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
