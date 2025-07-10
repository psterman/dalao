package com.example.aifloatingball

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.webkit.URLUtil
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.SimpleModeService
import com.google.android.material.button.MaterialButton

class ClipboardDialogActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置主题
        setTheme(R.style.Theme_ClipboardDialog)
        
        // 设置窗口属性
        window.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            
            // 设置背景半透明
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            
            // 设置窗口大小
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        
        setContentView(R.layout.activity_clipboard_dialog)
        
        settingsManager = SettingsManager.getInstance(this)
        
        // 获取剪贴板内容
        val clipboardContent = intent.getStringExtra("content") ?: return finish()
        
        // 设置UI
        setupUI(clipboardContent)
        
        // 8秒后自动关闭
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                finishWithAnimation()
            }
        }, 8000)
    }

    override fun onBackPressed() {
        finishWithAnimation()
    }

    private fun setupUI(content: String) {
        // 设置标题和内容
        findViewById<TextView>(R.id.dialog_title).text = when {
            URLUtil.isValidUrl(content) -> "检测到网址"
            else -> "检测到文本"
        }
        
        findViewById<TextView>(R.id.dialog_message).text = content
        
        // 搜索按钮
        findViewById<MaterialButton>(R.id.btn_search).setOnClickListener {
            when (settingsManager.getDisplayMode()) {
                "floating_ball" -> startFloatingSearch(content)
                "dynamic_island" -> startIslandSearch(content)
                else -> startSimpleMode(content)
            }
            finishWithAnimation()
        }
        
        // 跳过按钮
        findViewById<MaterialButton>(R.id.btn_skip).setOnClickListener {
            finishWithAnimation()
        }
        
        // 设置"不再提示"选项
        findViewById<CheckBox>(R.id.cb_dont_show_again).apply {
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setClipboardListenerEnabled(!isChecked)
            }
        }
    }

    private fun finishWithAnimation() {
        finish()
        overridePendingTransition(0, R.anim.dialog_fade_out)
    }

    private fun startFloatingSearch(content: String) {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra("search_content", content)
        }
        startService(intent)
    }

    private fun startIslandSearch(content: String) {
        val intent = Intent(this, DynamicIslandService::class.java).apply {
            putExtra("search_content", content)
        }
        startService(intent)
    }

    private fun startSimpleMode(content: String) {
        val intent = Intent(this, SimpleModeActivity::class.java).apply {
            putExtra("search_content", content)
            putExtra("mode", "clipboard")
        }
        startActivity(intent)
        finish() // 关闭剪贴板对话框
    }

    companion object {
        fun show(context: Context, content: String) {
            val intent = Intent(context, ClipboardDialogActivity::class.java).apply {
                putExtra("content", content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }
    }
} 