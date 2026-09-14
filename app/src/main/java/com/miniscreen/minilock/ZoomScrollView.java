package com.miniscreen.minilock;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * A ScrollView that knows when a gesture is not a scroll.
 *
 * A plain ScrollView claims any drag the instant a finger moves vertically, which is why the
 * pinch only worked over some parts of the screen: wherever the scroller decided first, the
 * ZoomLayout below got a CANCEL and never saw the second finger. It is also why a zoomed page
 * could not be moved sideways.
 *
 * Two fingers are never a scroll, and while zoomed a sideways drag belongs to the pan.
 */
public class ZoomScrollView extends ScrollView {

    private ZoomLayout zoom;
    private float downX, downY;

    public ZoomScrollView(Context context) { super(context); }

    public void setZoomLayout(ZoomLayout layout) { this.zoom = layout; }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getPointerCount() > 1) return false;              // a pinch is never a scroll

        if (zoom != null && zoom.isZoomed()) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(ev.getX() - downX) > Math.abs(ev.getY() - downY)) {
                        return false;                            // sideways belongs to the pan
                    }
                    break;
                default:
                    break;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }
}
