package com.miniscreen.atelier;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.view.View;

/**
 * The dial as a live wallpaper, so it renders as the lock screen and home screen background.
 * This is the only supported way to put custom art on the Android lock screen: the keyguard
 * itself belongs to SystemUI and cannot be replaced by an app.
 *
 * Reuses WatchView rather than duplicating the drawing: the view is never attached to a
 * window, so it is measured and laid out by hand and drawn straight onto the surface canvas.
 * Because it is unattached, isShown() is false and WatchView will not self-schedule, which
 * leaves this engine in sole control of the frame clock.
 */
public class MiniscreenWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine() { return new DialEngine(); }

    private class DialEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable tick = this::draw;
        private WatchView face;
        private boolean visible;

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            face = new WatchView(MiniscreenWallpaper.this);
            face.setExhibition(true);
            setOffsetNotificationsEnabled(false);
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            face.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                         View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            face.layout(0, 0, width, height);
            draw();
        }
        @Override public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (isVisible) draw(); else handler.removeCallbacks(tick);
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(tick);
        }
        @Override public void onDestroy() {
            super.onDestroy();
            visible = false;
            handler.removeCallbacks(tick);
        }
        /** Never let a frame run while hidden: a wallpaper that keeps drawing is a battery leak. */
        private void draw() {
            if (!visible || face == null) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) { canvas.drawColor(Color.BLACK); face.draw(canvas); }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            handler.removeCallbacks(tick);
            boolean dim = Prefs.ambient(MiniscreenWallpaper.this);
            handler.postDelayed(tick, dim || !Prefs.sweep(MiniscreenWallpaper.this) ? 1000 : 33);
        }
    }
}
