package com.miniscreen.minilock;

import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * The 3D pocket watch: the WebGL scene from tools/watch3d.html, running unchanged in a WebView
 * from assets. three.js is bundled, so nothing is fetched and the app holds no INTERNET
 * permission.
 *
 * Pinch zooms, double tap returns to the fitted framing, horizontal drag turns the watch over.
 */
final class Watch3D {

    private Watch3D() { }

    static WebView view(Context context) {
        WebView web = new WebView(context);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);                 // only ever file:///android_asset
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDomStorageEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        // the asset URL never changes between versions, so WebView will happily serve a
        // stale copy of a page we just rebuilt
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(0xFF000000);
        web.setHorizontalScrollBarEnabled(false);
        web.setVerticalScrollBarEnabled(false);
        // The page cannot read preferences, so apply them once it exists.
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { applyCard(v); }
        });
        web.loadUrl("file:///android_asset/lock.html");
        return web;
    }

    /** Show or hide the line of text under the watch, following the user's setting. */
    static void applyCard(WebView web) {
        if (web == null) return;
        boolean on = Prefs.card(web.getContext());
        web.evaluateJavascript("window.__lock&&__lock.setCard(" + on + ")", null);
    }

    /**
     * Pinch to zoom, handled natively.
     *
     * The page has its own touch handlers, but inside a ScrollView they never reliably see a
     * two-finger gesture: the parent claims it as a scroll first. ScaleGestureDetector reads it
     * here and pushes the result into the scene, which also means the camera zooms and
     * re-renders sharp rather than the page being scaled up and blurred.
     */
    static void enablePinch(WebView web) {
        final float[] zoom = {1f};
        final ScaleGestureDetector detector = new ScaleGestureDetector(web.getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        // zoom is a camera DISTANCE multiplier, so fingers apart means divide
                        zoom[0] = Math.max(0.42f, Math.min(2.4f, zoom[0] / d.getScaleFactor()));
                        web.evaluateJavascript(
                                "window.__lock&&__lock.setZoom(" + zoom[0] + ")", null);
                        return true;
                    }
                });
        web.setOnTouchListener((v, e) -> {
            // Only claim the gesture once a second finger is down, so a one-finger drag can
            // still scroll the page it sits in.
            if (e.getPointerCount() > 1) {
                ViewParent parent = v.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            }
            detector.onTouchEvent(e);
            return false;                                   // the page still sees every event
        });
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
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
