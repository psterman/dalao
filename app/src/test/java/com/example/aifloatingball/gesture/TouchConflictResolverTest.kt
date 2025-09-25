package com.example.aifloatingball.gesture

import android.content.Context
import android.view.MotionEvent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

/**
 * TouchConflictResolver的单元测试
 */
@RunWith(MockitoJUnitRunner::class)
class TouchConflictResolverTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var motionEvent: MotionEvent

    private lateinit var resolver: TouchConflictResolver

    @Before
    fun setup() {
        resolver = TouchConflictResolver(context)
    }

    @Test
    fun testVerticalSwipeDetection() {
        // 模拟垂直滑动
        whenever(motionEvent.action).thenReturn(MotionEvent.ACTION_DOWN)
        whenever(motionEvent.x).thenReturn(100f)
        whenever(motionEvent.y).thenReturn(100f)
        
        val downResult = resolver.analyzeTouchEvent(motionEvent)
        assertEquals(TouchConflictResolver.SwipeDirection.NONE, downResult.direction)
        
        // 模拟向上滑动
        whenever(motionEvent.action).thenReturn(MotionEvent.ACTION_MOVE)
        whenever(motionEvent.x).thenReturn(105f) // 轻微水平移动
        whenever(motionEvent.y).thenReturn(50f)  // 明显垂直移动
        
        val moveResult = resolver.analyzeTouchEvent(motionEvent)
        assertEquals(TouchConflictResolver.SwipeDirection.VERTICAL, moveResult.direction)
        assertEquals(true, moveResult.allowWebViewScroll)
        assertEquals(false, moveResult.shouldIntercept)
    }

    @Test
    fun testHorizontalSwipeDetection() {
        // 模拟水平滑动
        whenever(motionEvent.action).thenReturn(MotionEvent.ACTION_DOWN)
        whenever(motionEvent.x).thenReturn(100f)
        whenever(motionEvent.y).thenReturn(100f)
        
        resolver.analyzeTouchEvent(motionEvent)
        
        // 模拟向右滑动
        whenever(motionEvent.action).thenReturn(MotionEvent.ACTION_MOVE)
        whenever(motionEvent.x).thenReturn(150f) // 明显水平移动
        whenever(motionEvent.y).thenReturn(105f) // 轻微垂直移动
        
        val moveResult = resolver.analyzeTouchEvent(motionEvent)
        assertEquals(TouchConflictResolver.SwipeDirection.HORIZONTAL, moveResult.direction)
        assertEquals(false, moveResult.allowWebViewScroll)
        assertEquals(true, moveResult.shouldIntercept)
    }
}
