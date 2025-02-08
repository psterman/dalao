package com.example.aifloatingball.menu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import com.example.aifloatingball.R
import com.example.aifloatingball.utils.SystemSettingsHelper

class QuickMenuManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val systemSettingsHelper: SystemSettingsHelper
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
        view.findViewById<ImageButton>(R.id.btn_wifi).setOnClickListener {
            systemSettingsHelper.toggleWifi()
        }
        
        view.findViewById<ImageButton>(R.id.btn_bluetooth).setOnClickListener {
            systemSettingsHelper.toggleBluetooth()
        }
        
        view.findViewById<ImageButton>(R.id.btn_brightness).setOnClickListener {
            systemSettingsHelper.showBrightnessDialog()
        }
        
        view.findViewById<ImageButton>(R.id.btn_screenshot).setOnClickListener {
            systemSettingsHelper.takeScreenshot()
        }
        
        view.findViewById<ImageButton>(R.id.btn_recent_apps).setOnClickListener {
            systemSettingsHelper.showRecentApps()
        }
        
        view.findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            systemSettingsHelper.openSettings()
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
} 