package com.example.aifloatingball.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.preference.PreferenceManager
import android.view.WindowManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.content.Context
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.preference.SearchEngineListPreference
import com.example.aifloatingball.HomeActivity
import kotlin.math.abs

class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastClickTime: Long = 0
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "FloatingBallChannel"
    private val DOUBLE_CLICK_TIME = 300L
    private val SNAP_DISTANCE = 50
    private val screenWidth by lazy { windowManager?.defaultDisplay?.width ?: 0 }
    private val screenHeight by lazy { windowManager?.defaultDisplay?.height ?: 0 }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeWindowManager()
        createFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Ball Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating ball service running"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("AI Floating Ball")
        .setContentText("Tap to open settings")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        ))
        .build()

    private fun initializeWindowManager() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val size = prefs.getInt("ball_size", 100)
        val alphaValue = prefs.getFloat("ball_alpha", 0.8f)
        
        params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("last_x", 0)
            y = prefs.getInt("last_y", 100)
            this.alpha = alphaValue
        }
    }

    private fun createFloatingWindow() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball_layout, null)
        
        longPressRunnable = Runnable {
            // Handle long press
            openSettings()
        }
        
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    // Handle double click
                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME) {
                        onDoubleClick()
                        return@setOnTouchListener true
                    }
                    lastClickTime = clickTime
                    
                    // Start long press detection
                    handler.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (abs(deltaX) > ViewConfiguration.get(this).scaledTouchSlop ||
                        abs(deltaY) > ViewConfiguration.get(this).scaledTouchSlop) {
                        handler.removeCallbacks(longPressRunnable!!)
                    }
                    
                    params?.x = initialX + deltaX
                    params?.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable!!)
                    
                    val moved = abs(event.rawX - initialTouchX) > ViewConfiguration.get(this).scaledTouchSlop ||
                               abs(event.rawY - initialTouchY) > ViewConfiguration.get(this).scaledTouchSlop
                    
                    if (!moved) {
                        openSearchEngine()
                    } else {
                        snapToEdge()
                        savePosition()
                    }
                }
            }
            true
        }
        windowManager?.addView(floatingView, params)
    }

    private fun snapToEdge() {
        params?.let { p ->
            when {
                p.x < SNAP_DISTANCE -> p.x = 0
                p.x > screenWidth - p.width - SNAP_DISTANCE -> p.x = screenWidth - p.width
            }
            windowManager?.updateViewLayout(floatingView, params)
        }
    }

    private fun savePosition() {
        params?.let { p ->
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt("last_x", p.x)
                .putInt("last_y", p.y)
                .apply()
        }
    }

    private fun onDoubleClick() {
        // Toggle visibility
        floatingView?.let { view ->
            view.alpha = if (view.alpha > 0.5f) 0.3f else 1.0f
        }
    }

    private fun openSettings() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openSearchEngine() {
        val url = getSearchEngineUrl()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun getSearchEngineUrl(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val searchEngine = prefs.getString("search_engine", "baidu") ?: "baidu"
        return SearchEngineListPreference.getSearchEngineUrl(this, searchEngine)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
    }
}