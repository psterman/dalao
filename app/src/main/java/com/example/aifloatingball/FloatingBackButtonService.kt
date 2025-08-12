package com.example.aifloatingball

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.content.ContextCompat

class FloatingBackButtonService : Service() {
    
    companion object {
        private const val TAG = "FloatingBackButtonService"
    }
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isShowing = false
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingBackButtonService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isShowing) {
            showFloatingButton()
        }
        return START_STICKY
    }
    
    private fun showFloatingButton() {
        try {
            // 检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w(TAG, "没有悬浮窗权限")
                stopSelf()
                return
            }
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 创建悬浮按钮布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_back_button, null)
            
            val backButton = floatingView?.findViewById<ImageButton>(R.id.floating_back_button)
            backButton?.setOnClickListener {
                // 返回到SimpleModeActivity
                val intent = Intent(this, SimpleModeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
                
                // 隐藏悬浮按钮
                hideFloatingButton()
            }
            
            // 设置悬浮窗参数
            val layoutParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = 50 // 距离左边50px
                y = 200 // 距离顶部200px
            }
            
            // 添加拖拽功能
            setupDragListener(layoutParams)
            
            // 显示悬浮窗
            windowManager?.addView(floatingView, layoutParams)
            isShowing = true
            
            Log.d(TAG, "悬浮返回按钮已显示")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮按钮失败", e)
            stopSelf()
        }
    }
    
    private fun setupDragListener(layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun hideFloatingButton() {
        try {
            if (isShowing && floatingView != null) {
                windowManager?.removeView(floatingView)
                isShowing = false
                Log.d(TAG, "悬浮返回按钮已隐藏")
            }
        } catch (e: Exception) {
            Log.e(TAG, "隐藏悬浮按钮失败", e)
        }
        stopSelf()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideFloatingButton()
        Log.d(TAG, "FloatingBackButtonService destroyed")
    }
}
