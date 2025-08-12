package com.example.aifloatingball

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
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
import android.widget.TextView
import androidx.core.content.ContextCompat

class FloatingBackButtonService : Service() {

    companion object {
        private const val TAG = "FloatingBackButtonService"
        private const val PREFS_NAME = "floating_back_button"
        private const val KEY_IS_RIGHT_HAND = "is_right_hand"
        private const val KEY_POSITION_X = "position_x"
        private const val KEY_POSITION_Y = "position_y"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isShowing = false
    private lateinit var prefs: SharedPreferences
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
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

            val backButton = floatingView?.findViewById<View>(R.id.floating_back_button)
            val backIcon = floatingView?.findViewById<android.widget.ImageView>(R.id.floating_back_icon)
            val backText = floatingView?.findViewById<TextView>(R.id.floating_back_text)

            // 设置点击事件
            backButton?.setOnClickListener {
                // 添加点击动画
                animateClick(floatingView!!) {
                    // 返回到SimpleModeActivity
                    val intent = Intent(this, SimpleModeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)

                    // 隐藏悬浮按钮
                    hideFloatingButton()
                }
            }

            // 长按切换左右手模式
            backButton?.setOnLongClickListener {
                toggleHandMode()
                true
            }
            
            // 设置悬浮窗参数
            val isRightHand = prefs.getBoolean(KEY_IS_RIGHT_HAND, false)
            val savedX = prefs.getInt(KEY_POSITION_X, if (isRightHand) getScreenWidth() - 150 else 20)
            val savedY = prefs.getInt(KEY_POSITION_Y, 200)

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
                x = savedX
                y = savedY
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
                MotionEvent.ACTION_UP -> {
                    // 保存位置
                    prefs.edit()
                        .putInt(KEY_POSITION_X, layoutParams.x)
                        .putInt(KEY_POSITION_Y, layoutParams.y)
                        .apply()
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
    
    /**
     * 点击动画
     */
    private fun animateClick(view: View, onComplete: () -> Unit) {
        val scaleDown = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.95f)
        val scaleDownY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.95f)
        val scaleUp = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1.0f)
        val scaleUpY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1.0f)

        val downSet = android.animation.AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY)
            duration = 100
        }

        val upSet = android.animation.AnimatorSet().apply {
            playTogether(scaleUp, scaleUpY)
            duration = 100
        }

        downSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                upSet.start()
            }
        })

        upSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })

        downSet.start()
    }

    /**
     * 切换左右手模式
     */
    private fun toggleHandMode() {
        val isRightHand = !prefs.getBoolean(KEY_IS_RIGHT_HAND, false)
        prefs.edit().putBoolean(KEY_IS_RIGHT_HAND, isRightHand).apply()

        // 移动到对应位置
        val layoutParams = floatingView?.layoutParams as? WindowManager.LayoutParams
        layoutParams?.let { params ->
            params.x = if (isRightHand) getScreenWidth() - 150 else 20
            windowManager?.updateViewLayout(floatingView, params)

            // 保存位置
            prefs.edit().putInt(KEY_POSITION_X, params.x).apply()
        }

        android.widget.Toast.makeText(this, if (isRightHand) "已切换到右手模式" else "已切换到左手模式", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 获取屏幕宽度
     */
    private fun getScreenWidth(): Int {
        val displayMetrics = resources.displayMetrics
        return displayMetrics.widthPixels
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingButton()
        Log.d(TAG, "FloatingBackButtonService destroyed")
    }
}
