package com.example.aifloatingball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.Toast
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.manager.SearchEngineManager

class DualFloatingWebViewService : Service() {
    companion object {
        private const val TAG = "DualFloatingWebView"
    }

    private lateinit var rootView: View
    private lateinit var leftWebView: WebView
    private lateinit var rightWebView: WebView
    private lateinit var saveButton: ImageButton
    private var isSaving = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun initializeViews() {
        rootView = LayoutInflater.from(this).inflate(R.layout.floating_webview_dual, null)
        leftWebView = rootView.findViewById(R.id.left_webview)
        rightWebView = rootView.findViewById(R.id.right_webview)
        saveButton = rootView.findViewById(R.id.save_button)
        setupSaveButton()
    }

    private fun setupSaveButton() {
        saveButton.visibility = View.VISIBLE
        saveButton.setOnClickListener {
            if (!isSaving) {
                saveCurrentSearchEngines()
            }
        }
    }

    private fun saveCurrentSearchEngines() {
        isSaving = true
        try {
            // 获取当前所有窗口的搜索引擎
            val searchEngines = mutableListOf<SearchEngine>()
            
            // 获取左侧窗口的搜索引擎
            leftWebView.let { webView ->
                val leftEngine = SearchEngine(
                    name = webView.title ?: "未知搜索引擎",
                    url = webView.url ?: "",
                    iconResId = R.drawable.ic_search,
                    description = "从浏览器保存的搜索引擎"
                )
                searchEngines.add(leftEngine)
            }
            
            // 获取右侧窗口的搜索引擎
            rightWebView.let { webView ->
                val rightEngine = SearchEngine(
                    name = webView.title ?: "未知搜索引擎",
                    url = webView.url ?: "",
                    iconResId = R.drawable.ic_search,
                    description = "从浏览器保存的搜索引擎"
                )
                searchEngines.add(rightEngine)
            }
            
            // 保存搜索引擎
            val searchEngineManager = SearchEngineManager.getInstance(this)
            searchEngines.forEach { engine ->
                searchEngineManager.saveSearchEngine(engine)
            }
            
            // 显示成功提示
            showSaveSuccessAnimation(saveButton)
            Toast.makeText(this, "搜索引擎保存成功", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "保存搜索引擎失败", e)
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isSaving = false
        }
    }

    private fun showSaveSuccessAnimation(view: View) {
        view.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun extractSearchUrl(url: String): String {
        // 从当前URL中提取搜索URL模板
        return try {
            val uri = Uri.parse(url)
            val query = uri.getQueryParameter("q") ?: uri.getQueryParameter("query")
            if (query != null) {
                url.replace(query, "{searchTerms}")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取搜索URL失败", e)
            ""
        }
    }
} 