package com.example.aifloatingball.menu

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.FloatingWindowService

class QuickMenuManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var menuView: View? = null
    private var isMenuShowing = false
    
    fun showMenu(x: Int, y: Int) {
        if (isMenuShowing) return
        
        menuView = LayoutInflater.from(context).inflate(R.layout.quick_menu_layout, null).apply {
            setupMenuItems(this)
            startShowAnimation(this)
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            this.x = x
            this.y = y
        }
        
        windowManager.addView(menuView, params)
        isMenuShowing = true
    }
    
    fun hideMenu() {
        menuView?.let {
            startHideAnimation(it) {
                windowManager.removeView(it)
                menuView = null
                isMenuShowing = false
            }
        }
    }
    
    private fun setupMenuItems(view: View) {
        // 设置按钮
        view.findViewById<TextView>(R.id.settingsButton)?.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            hideMenu()
        }
        
        // 关闭按钮
        view.findViewById<TextView>(R.id.closeButton)?.setOnClickListener {
            context.stopService(Intent(context, FloatingWindowService::class.java))
            hideMenu()
        }
    }
    
    private fun startShowAnimation(view: View) {
        val animation = AnimationUtils.loadAnimation(context, R.anim.menu_show)
        view.startAnimation(animation)
    }
    
    private fun startHideAnimation(view: View, onEnd: () -> Unit) {
        val animation = AnimationUtils.loadAnimation(context, R.anim.menu_hide)
        animation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                onEnd()
            }
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        view.startAnimation(animation)
    }

    fun showQuickMenu() {
        // 计算屏幕中心位置
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val x = (screenWidth - 300) / 2  // 假设菜单宽度为300像素
        val y = (screenHeight - 400) / 2  // 假设菜单高度为400像素
        
        showMenu(x, y)
    }
    
    fun hideQuickMenu() {
        hideMenu()
    }
} 