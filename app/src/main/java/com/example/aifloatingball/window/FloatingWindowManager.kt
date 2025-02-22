package com.example.aifloatingball.window

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine

class FloatingWindowManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windows = mutableMapOf<String, FloatingWindow>()

    fun createWindow(engine: SearchEngine, url: String) {
        // Remove existing window for this engine if it exists
        removeWindow(engine.name)

        // Create and add new window
        val window = FloatingWindow(engine, url)
        windows[engine.name] = window
        windowManager.addView(window.view, window.layoutParams)
    }

    fun removeWindow(engineName: String) {
        windows[engineName]?.let { window ->
            try {
                windowManager.removeView(window.view)
                windows.remove(engineName)
            } catch (e: IllegalArgumentException) {
                // View might not be attached
            }
        }
    }

    fun removeAllWindows() {
        windows.keys.toList().forEach { removeWindow(it) }
    }

    private inner class FloatingWindow(engine: SearchEngine, url: String) {
        val view: View
        val layoutParams: WindowManager.LayoutParams
        private var initialX: Int = 0
        private var initialY: Int = 0
        private var initialTouchX: Float = 0f
        private var initialTouchY: Float = 0f
        private var isMinimized = false

        init {
            // Inflate layout
            view = LayoutInflater.from(context).inflate(R.layout.layout_floating_window, null)

            // Set up window parameters
            layoutParams = WindowManager.LayoutParams().apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
            }

            // Set up views
            view.findViewById<ImageView>(R.id.engine_icon).setImageResource(engine.iconResId)
            view.findViewById<TextView>(R.id.engine_name).text = engine.name

            // Set up WebView
            val webView = view.findViewById<WebView>(R.id.web_view)
            setupWebView(webView, url)

            // Set up close button
            view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
                removeWindow(engine.name)
            }

            // Set up minimize button
            val minimizeButton = view.findViewById<ImageButton>(R.id.btn_minimize)
            minimizeButton.setOnClickListener {
                toggleMinimize()
            }

            // Set up drag handling
            view.findViewById<View>(R.id.title_bar).setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        layoutParams.x = (initialX + dx).toInt()
                        layoutParams.y = (initialY + dy).toInt()
                        windowManager.updateViewLayout(view, layoutParams)
                        true
                    }
                    else -> false
                }
            }
        }

        private fun setupWebView(webView: WebView, url: String) {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }

            webView.webViewClient = WebViewClient()
            webView.webChromeClient = WebChromeClient()
            webView.loadUrl(url)
        }

        private fun toggleMinimize() {
            isMinimized = !isMinimized
            val webView = view.findViewById<WebView>(R.id.web_view)
            if (isMinimized) {
                layoutParams.height = view.findViewById<View>(R.id.title_bar).height
                webView.visibility = View.GONE
            } else {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                webView.visibility = View.VISIBLE
            }
            windowManager.updateViewLayout(view, layoutParams)
        }
    }
} 