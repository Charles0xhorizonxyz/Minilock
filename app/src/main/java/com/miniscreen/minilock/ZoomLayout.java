package com.miniscreen.minilock;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/**
 * Pinch-zoomable content, designed to sit INSIDE a ScrollView.
 *
 * The nesting matters. With the zoom wrapped around the ScrollView instead, scaling made the
 * scroll view taller than the screen with no way to reach the bottom, and every scroll gesture
 * was divided by the zoom factor before the ScrollView saw it, so scrolling crawled. Inside,
 * this reports a scaled height, the ScrollView scrolls the whole thing at normal speed, and
 * only sideways panning is left for this view to handle.
 *
 * Drawing scales the canvas rather than the view, so text is re-rendered at the zoomed size and
 * stays sharp instead of being a magnified bitmap.
 */
public class ZoomLayout extends FrameLayout {

    private static final float MIN = 1f, MAX = 4f;

    private final ScaleGestureDetector detector;
    private final int slop;
    private float scale = 1f, panX = 0f;
    private float downX, downY, lastX;
    private int oneFinger;                       // 0 undecided, 1 panning sideways, 2 passed on

    public ZoomLayout(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        detector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        float previous = scale;
                        scale = clamp(scale * d.getScaleFactor(), MIN, MAX);
                        if (scale == previous) return true;
                        keepFocalPoint(previous, d.getFocusX(), d.getFocusY());
                        requestLayout();
                        invalidate();
                        return true;
                    }
                });
        // Quick-scale is on by default and lets a one-finger double-tap-drag zoom by accident.
        detector.setQuickScaleEnabled(false);
        detector.setStylusScaleEnabled(false);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private ScrollView scroller() {
        return getParent() instanceof ScrollView ? (ScrollView) getParent() : null;
    }

    /** Hold the point between the fingers still while the scale changes. */
    private void keepFocalPoint(float previous, float focusX, float focusY) {
        panX = focusX - (focusX - panX) * (scale / previous);
        clampPan();
        ScrollView sv = scroller();
        if (sv == null) return;
        float contentY = (sv.getScrollY() + focusY) / previous;
        sv.scrollTo(0, Math.max(0, Math.round(contentY * scale - focusY)));
    }

    private void clampPan() {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null) return;
        float overflow = child.getWidth() * scale - getWidth();
        panX = overflow <= 0 ? 0 : clamp(panX, -overflow, 0f);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null) { super.onMeasure(widthSpec, heightSpec); return; }
        int width = MeasureSpec.getSize(widthSpec);
        // measure the child unscaled, then claim the room the scaled version needs
        child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(width, Math.round(child.getMeasuredHeight() * scale));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null) return;
        child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        clampPan();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(panX, 0);
        canvas.scale(scale, scale);
        super.dispatchDraw(canvas);                  // children redraw sharp at the new size
        canvas.restore();
    }

    private void holdGesture(boolean hold) {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(hold);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // The ScrollView is our parent now, and it will claim a pinch as a scroll the moment
        // one finger drifts vertically -- we would then get a CANCEL and the zoom would never
        // happen. Take the gesture as soon as a second finger lands.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                holdGesture(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                holdGesture(false);
                break;
            default:
                break;
        }

        detector.onTouchEvent(event);

        if (event.getPointerCount() >= 2) return true;   // two fingers belong to the zoom

        if (scale > 1f) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = event.getX();
                    downY = event.getY();
                    oneFinger = 0;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (oneFinger == 0) {
                        float dx = Math.abs(event.getX() - downX);
                        float dy = Math.abs(event.getY() - downY);
                        if (dx > slop || dy > slop) oneFinger = dx > dy ? 1 : 2;
                    }
                    if (oneFinger == 1) {                // sideways: the ScrollView cannot do this
                        holdGesture(true);               // and it must not steal this either
                        panX += event.getX() - lastX;
                        lastX = event.getX();
                        clampPan();
                        invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    boolean consumed = oneFinger == 1;
                    oneFinger = 0;
                    if (consumed) return true;
                    break;
                default:
                    break;
            }
        }

        if (scale == 1f && panX == 0f) return super.dispatchTouchEvent(event);

        MotionEvent mapped = MotionEvent.obtain(event);
        mapped.setLocation((event.getX() - panX) / scale, event.getY() / scale);
        boolean handled = super.dispatchTouchEvent(mapped);
        mapped.recycle();
        return handled;
    }

    public void reset() {
        scale = 1f;
        panX = 0f;
        requestLayout();
        invalidate();
    }

    public boolean isZoomed() { return scale != 1f; }
}
