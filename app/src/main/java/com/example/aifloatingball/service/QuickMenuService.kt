package com.example.aifloatingball.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.FloatingWindowService

class QuickMenuService : Service() {
    private var windowManager: WindowManager? = null
    private var quickMenuView: View? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "QuickMenuService onCreate")
        createQuickMenu()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createQuickMenu() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            quickMenuView = inflater.inflate(R.layout.quick_menu_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            // 设置按钮点击事件
            quickMenuView?.findViewById<TextView>(R.id.settingsButton)?.setOnClickListener {
                val intent = Intent(this, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                stopSelf()
            }

            // 关闭按钮点击事件
            quickMenuView?.findViewById<TextView>(R.id.closeButton)?.setOnClickListener {
                // 停止悬浮球服务
                stopService(Intent(this, FloatingWindowService::class.java))
                // 停止自己
                stopSelf()
            }

            windowManager?.addView(quickMenuView, params)

            // 点击菜单外部区域关闭菜单
            quickMenuView?.setOnClickListener {
                stopSelf()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating quick menu", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (windowManager != null && quickMenuView != null) {
            try {
                windowManager?.removeView(quickMenuView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing quick menu view", e)
            }
        }
    }

    companion object {
        private const val TAG = "QuickMenuService"
    }
} 