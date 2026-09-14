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
import android.webkit.ValueCallback;

/**
 * The 3D pocket watch: the WebGL scene from tools/watch3d.html, running unchanged in a WebView
 * from assets. three.js is bundled, so nothing is fetched and the app holds no INTERNET
 * permission.
 *
 * Pinch sizes it, a finger on the ring carries it anywhere, a finger on the dial turns it over.
 */
final class Watch3D {

    private Watch3D() { }

    static WebView view(Context context, Runnable onReady) { return view(context, onReady, null); }

    /** onGesture: receives "left1" or "right2" from the page; only the lock screen passes one. */
    static WebView view(Context context, Runnable onReady,
                        java.util.function.Consumer<String> onGesture) {
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
        // The page saves the caseback plate (finish, movement, counter) through this. Only
        // annotated methods are reachable, and the page is a bundled asset that loads nothing.
        web.addJavascriptInterface(new PlateStore(context, onGesture), "minilock");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                // Factory design: the page's own gold, dial and studio, which is the look that
                // used to flash for an instant before the saved choices landed on top of it.
                if (!Prefs.factory(context)) {
                    applyBackground(v);
                    restorePlate(v);
                }
                applyCard(v);                 // the text under the watch follows its own switch
                applyFlat(v);                 // 3D or 2D: the same watch, with or without depth
                restorePlacement(v);          // size and position are not part of the design
                if (onReady != null) onReady.run();      // state pushed before this was lost
            }
        });
        web.loadUrl("file:///android_asset/lock.html");
        return web;
    }

    /** What the page hands over when the plate changes; called off the main thread. */
    static final class PlateStore {
        private final Context context;
        private final java.util.function.Consumer<String> onGesture;
        PlateStore(Context context, java.util.function.Consumer<String> onGesture) {
            this.context = context.getApplicationContext();
            this.onGesture = onGesture;
        }
        @android.webkit.JavascriptInterface
        public void put(String key, String value) {
            if ("plate".equals(key) && value != null && value.length() < 2000) {
                Prefs.setPlate(context, value);
            }
        }
        /** The page recognised a flick of the dial ("left1" or "right2"); the app acts on it. */
        @android.webkit.JavascriptInterface
        public void gesture(String name) {
            if (onGesture != null && name != null && name.length() < 20) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> onGesture.accept(name));
            }
        }
    }

    /** Put the caseback plate back the way it was left: finish, movement toggles, counter. */
    static void restorePlate(WebView web) {
        String saved = Prefs.plate(web.getContext());
        if (saved.isEmpty()) return;
        web.evaluateJavascript("window.__lock&&__lock.setState("
                + org.json.JSONObject.quote(saved) + ")", null);
    }

    /** Put the watch back where it was left. */
    static void restorePlacement(WebView web) {
        String saved = Prefs.placement(web.getContext());
        if (saved.isEmpty()) return;
        String[] parts = saved.split(",");
        if (parts.length != 3) return;
        web.evaluateJavascript("window.__lock&&__lock.setPlacement("
                + parts[0] + "," + parts[1] + "," + parts[2] + ")", null);
    }

    /** Remember where it was parked, once the gesture ends. */
    static void savePlacement(final WebView web) {
        web.evaluateJavascript("window.__lock?__lock.placement():''", new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                if (value == null) return;
                String v = value.replace("\"", "").trim();   // comes back JSON-quoted
                if (v.isEmpty() || v.equals("null") || v.split(",").length != 3) return;
                Prefs.setPlacement(web.getContext(), v);
            }
        });
    }

    /** 3D or 2D: the same watch, in perspective with its shadow and orbit, or flat and head-on. */
    static void applyFlat(WebView web) {
        if (web == null) return;
        web.evaluateJavascript("window.__lock&&__lock.setFlat(" + !Prefs.threeD(web.getContext()) + ")", null);
    }

    /** Paint the studio behind the watch the grey the user chose. */
    static void applyBackground(WebView web) {
        if (web == null) return;
        web.evaluateJavascript("window.__lock&&__lock.setBackground("
                + (Prefs.background(web.getContext()) / 100f) + ")", null);
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
        final float[] lastFocusX = {0f}, lastFocusY = {0f};
        final boolean[] panning = {false};
        web.setOnTouchListener((v, e) -> {
            // Only claim the gesture once a second finger is down, so a one-finger drag can
            // still scroll or turn the watch.
            if (e.getPointerCount() > 1) {
                ViewParent parent = v.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                float fx = (e.getX(0) + e.getX(1)) / 2f;
                float fy = (e.getY(0) + e.getY(1)) / 2f;
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE && panning[0]) {
                    float dx = fx - lastFocusX[0], dy = fy - lastFocusY[0];
                    if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                        web.evaluateJavascript(
                                "window.__lock&&__lock.nudge(" + dx + "," + dy + ")", null);
                    }
                }
                lastFocusX[0] = fx;
                lastFocusY[0] = fy;
                panning[0] = true;
            } else {
                // Gesture over: remember the placement. Two fingers may have moved it, and so
                // may one finger carrying it by the ring, which only the page can tell.
                if (panning[0] || e.getActionMasked() == MotionEvent.ACTION_UP) savePlacement(web);
                panning[0] = false;
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
