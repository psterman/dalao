package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.webview.StackedWebViewManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 堆叠WebView测试Activity
 * 用于演示和测试WebView堆叠功能
 */
class StackedWebViewTestActivity : AppCompatActivity() {
    
    private lateinit var stackedWebViewManager: StackedWebViewManager
    private lateinit var container: FrameLayout
    private lateinit var addButton: Button
    private lateinit var removeButton: Button
    private lateinit var stackInfoText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stacked_webview_test)
        
        initViews()
        setupStackedWebViewManager()
        setupButtons()
    }
    
    private fun initViews() {
        container = findViewById(R.id.stacked_webview_container)
        addButton = findViewById(R.id.btn_add_webview)
        removeButton = findViewById(R.id.btn_remove_webview)
        stackInfoText = findViewById(R.id.stack_info_text)
    }
    
    private fun setupStackedWebViewManager() {
        stackedWebViewManager = StackedWebViewManager(this, container)
        
        // 设置监听器
        stackedWebViewManager.setOnWebViewChangeListener(object : StackedWebViewManager.OnWebViewChangeListener {
            override fun onWebViewAdded(webViewData: StackedWebViewManager.StackedWebViewData, index: Int) {
                updateStackInfo()
                Toast.makeText(this@StackedWebViewTestActivity, "已添加WebView: ${webViewData.title}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onWebViewRemoved(webViewData: StackedWebViewManager.StackedWebViewData, index: Int) {
                updateStackInfo()
                Toast.makeText(this@StackedWebViewTestActivity, "已移除WebView: ${webViewData.title}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onWebViewSwitched(webViewData: StackedWebViewManager.StackedWebViewData, index: Int) {
                updateStackInfo()
                Toast.makeText(this@StackedWebViewTestActivity, "切换到WebView: ${webViewData.title}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onStackChanged(fromIndex: Int, toIndex: Int) {
                updateStackInfo()
                Toast.makeText(this@StackedWebViewTestActivity, "堆栈变化: $fromIndex -> $toIndex", Toast.LENGTH_SHORT).show()
            }
        })
        
        // 添加初始WebView
        stackedWebViewManager.addWebView("https://www.baidu.com")
        updateStackInfo()
    }
    
    private fun setupButtons() {
        addButton.setOnClickListener {
            val testUrls = listOf(
                "https://www.google.com",
                "https://www.github.com",
                "https://www.stackoverflow.com",
                "https://www.android.com",
                "https://www.kotlinlang.org"
            )
            val randomUrl = testUrls.random()
            stackedWebViewManager.addWebView(randomUrl)
        }
        
        removeButton.setOnClickListener {
            val allWebViews = stackedWebViewManager.getAllWebViews()
            if (allWebViews.isNotEmpty()) {
                stackedWebViewManager.removeCurrentWebView()
            } else {
                Toast.makeText(this, "没有WebView可以移除", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateStackInfo() {
        stackInfoText.text = "堆栈信息: ${stackedWebViewManager.getStackInfo()}"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stackedWebViewManager.cleanup()
    }
}


