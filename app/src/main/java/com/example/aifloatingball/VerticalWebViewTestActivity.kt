package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.webview.VerticalWebViewManager
import android.widget.FrameLayout

/**
 * 纵向WebView测试Activity
 * 用于演示和测试纵向WebView组合功能
 */
class VerticalWebViewTestActivity : AppCompatActivity() {
    
    private lateinit var verticalWebViewManager: VerticalWebViewManager
    private lateinit var container: FrameLayout
    private lateinit var addButton: Button
    private lateinit var removeButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vertical_webview_test)
        
        initViews()
        setupVerticalWebViewManager()
        setupButtons()
    }
    
    private fun initViews() {
        container = findViewById(R.id.vertical_webview_container)
        addButton = findViewById(R.id.btn_add_webview)
        removeButton = findViewById(R.id.btn_remove_webview)
    }
    
    private fun setupVerticalWebViewManager() {
        verticalWebViewManager = VerticalWebViewManager(this, container)
        
        // 设置监听器
        verticalWebViewManager.setOnWebViewChangeListener(object : VerticalWebViewManager.OnWebViewChangeListener {
            override fun onWebViewAdded(webViewData: VerticalWebViewManager.VerticalWebViewData, position: Int) {
                Toast.makeText(this@VerticalWebViewTestActivity, "已添加WebView: ${webViewData.title}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onWebViewRemoved(webViewData: VerticalWebViewManager.VerticalWebViewData, position: Int) {
                Toast.makeText(this@VerticalWebViewTestActivity, "已移除WebView: ${webViewData.title}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onWebViewSwitched(webViewData: VerticalWebViewManager.VerticalWebViewData, position: Int) {
                Toast.makeText(this@VerticalWebViewTestActivity, "切换到WebView: ${webViewData.title}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onWebViewPositionChanged(fromPosition: Int, toPosition: Int) {
                Toast.makeText(this@VerticalWebViewTestActivity, "WebView位置变化: $fromPosition -> $toPosition", Toast.LENGTH_SHORT).show()
            }
        })
        
        // 添加初始WebView
        verticalWebViewManager.addWebView("https://www.baidu.com")
    }
    
    private fun setupButtons() {
        addButton.setOnClickListener {
            val testUrls = listOf(
                "https://www.google.com",
                "https://www.github.com",
                "https://www.stackoverflow.com",
                "https://www.android.com"
            )
            val randomUrl = testUrls.random()
            verticalWebViewManager.addWebView(randomUrl)
        }
        
        removeButton.setOnClickListener {
            val allWebViews = verticalWebViewManager.getAllWebViews()
            if (allWebViews.isNotEmpty()) {
                val currentWebView = verticalWebViewManager.getCurrentWebView()
                currentWebView?.let { webViewData ->
                    val position = allWebViews.indexOfFirst { it.id == webViewData.id }
                    verticalWebViewManager.removeWebView(position)
                }
            } else {
                Toast.makeText(this, "没有WebView可以移除", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        verticalWebViewManager.cleanup()
    }
}
