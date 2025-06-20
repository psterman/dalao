package com.example.aifloatingball.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import kotlin.math.abs

/**
 * A custom HorizontalScrollView that intelligently handles touch events to prevent conflicts
 * with vertical scrolling children (like WebViews).
 *
 * This view "locks" the scroll direction. If the user's gesture is primarily vertical,
 * it will not intercept the touch event, allowing the child view to scroll vertically without
 * any horizontal jitter. It will only initiate a horizontal scroll if the gesture is
 * primarily horizontal.
 */
class LockableHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var isScrollingHorizontally = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isScrollingHorizontally = false
                // Let the parent handle the initial down event, but we are watching.
                super.onInterceptTouchEvent(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)

                // If we are already locked into horizontal scrolling, continue to do so.
                if (isScrollingHorizontally) {
                    return true
                }
                
                // The touch slop is a system-defined distance a touch can wander before it is
                // considered a scroll. We use it to avoid making a decision on very small movements.
                if (dx > touchSlop || dy > touchSlop) {
                    if (dx > dy) {
                        // The user is scrolling horizontally. Intercept the event.
                        isScrollingHorizontally = true
                        return true
                    } else {
                        // The user is scrolling vertically. Do not intercept. Let the child handle it.
                        // We return false and the onInterceptTouchEvent of this view will not be called again
                        // for this gesture.
                        return false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrollingHorizontally = false
            }
        }
        // For all other cases, defer to the default implementation.
        return super.onInterceptTouchEvent(ev)
    }
} 