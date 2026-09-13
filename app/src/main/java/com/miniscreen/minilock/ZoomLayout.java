package com.miniscreen.minilock;

import android.content.Context;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Makes everything inside it pinch-zoomable, the way a web page is.
 *
 * Two fingers scale and pan; one finger passes straight through, so switches and scrolling
 * behave normally. While zoomed, touch coordinates are mapped back through the inverse
 * transform, otherwise taps would land wherever the untransformed layout thinks they are.
 */
public class ZoomLayout extends FrameLayout {

    private static final float MIN = 1f, MAX = 4f;

    private final ScaleGestureDetector detector;
    private final Matrix inverse = new Matrix();
    private float scale = 1f, panX = 0f, panY = 0f;
    private float lastFocusX, lastFocusY;
    private boolean gesture;

    public ZoomLayout(Context context) {
        super(context);
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float previous = scale;
                scale = clamp(scale * d.getScaleFactor(), MIN, MAX);
                // keep the point between the fingers pinned while scaling
                float k = scale / previous;
                panX = d.getFocusX() - (d.getFocusX() - panX) * k;
                panY = d.getFocusY() - (d.getFocusY() - panY) * k;
                apply();
                return true;
            }
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                lastFocusX = d.getFocusX();
                lastFocusY = d.getFocusY();
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) { }
        });
        // Quick-scale is on by default: a double-tap-and-drag would zoom with one finger,
        // which fires by accident and makes single taps feel haunted. Zoom is two fingers only.
        detector.setQuickScaleEnabled(false);
        detector.setStylusScaleEnabled(false);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Never let the content be dragged away from the screen. */
    private void apply() {
        float maxX = getWidth() * (scale - 1f);
        float maxY = getHeight() * (scale - 1f);
        panX = clamp(panX, -maxX, 0f);
        panY = clamp(panY, -maxY, 0f);
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null) return;
        child.setPivotX(0f);
        child.setPivotY(0f);
        child.setScaleX(scale);
        child.setScaleY(scale);
        child.setTranslationX(panX);
        child.setTranslationY(panY);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);

        if (event.getPointerCount() >= 2) {
            // two fingers belong to the zoom, not to whatever is underneath
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float fx = (event.getX(0) + event.getX(1)) / 2f;
                float fy = (event.getY(0) + event.getY(1)) / 2f;
                if (gesture) { panX += fx - lastFocusX; panY += fy - lastFocusY; apply(); }
                lastFocusX = fx;
                lastFocusY = fy;
                gesture = true;
            }
            return true;
        }
        gesture = false;

        if (scale == 1f && panX == 0f && panY == 0f) return super.dispatchTouchEvent(event);

        // map the touch back into the child's untransformed coordinates
        inverse.reset();
        inverse.postTranslate(-panX, -panY);
        inverse.postScale(1f / scale, 1f / scale);
        MotionEvent mapped = MotionEvent.obtain(event);
        mapped.transform(inverse);
        boolean handled = super.dispatchTouchEvent(mapped);
        mapped.recycle();
        return handled;
    }

    /** Back to 1:1. */
    public void reset() {
        scale = 1f;
        panX = panY = 0f;
        apply();
    }

    public boolean isZoomed() { return scale != 1f; }
}
