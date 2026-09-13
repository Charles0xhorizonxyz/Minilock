package com.miniscreen.minilock;

import android.content.Context;
import android.os.Build;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * The 3D pocket watch: the WebGL scene from tools/watch3d.html, running unchanged in a WebView
 * from assets. three.js is bundled, so nothing is fetched and the app holds no INTERNET
 * permission.
 */
final class Watch3D {

    private Watch3D() { }

    /** @param chrome "" for the lock screen, "bare" for a hero, "preview" fullscreen. */
    static WebView view(Context context, String chrome) {
        WebView web = new WebView(context);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);                 // only ever file:///android_asset
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDomStorageEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        web.setBackgroundColor(0xFF000000);
        web.setHorizontalScrollBarEnabled(false);
        web.setVerticalScrollBarEnabled(false);
        web.loadUrl("file:///android_asset/lock.html"
                + (chrome.isEmpty() ? "" : "#" + chrome));
        return web;
    }

    /**
     * Hide the system bars.
     *
     * Must be called AFTER setContentView. Before that the decor view does not exist and
     * getInsetsController() returns null, which is exactly what was crashing PreviewActivity
     * on launch.
     */
    static void immersive(Window window) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(5894);
        }
    }
}
