package com.example.aifloatingball.viewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.aifloatingball.R
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream

/**
 * 文件阅读器Activity
 * 支持txt、pdf、epub、mobi、azw、azw3、azw4、prc、pdb等格式
 * 参考alook的文件阅读功能实现
 */
class FileReaderActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "FileReaderActivity"
        private const val EXTRA_FILE_URI = "file_uri"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_FILE_NAME = "file_name"
        
        /**
         * 启动文件阅读器
         */
        fun start(context: Activity, fileUri: Uri, fileName: String? = null) {
            val intent = Intent(context, FileReaderActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, fileUri.toString())
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
            }
            context.startActivity(intent)
        }
        
        /**
         * 启动文件阅读器（使用文件路径）
         */
        fun startWithPath(context: Activity, filePath: String, fileName: String? = null) {
            val intent = Intent(context, FileReaderActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
            }
            context.startActivity(intent)
        }
    }
    
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleTextView: TextView
    private lateinit var errorTextView: TextView
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏显示
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.activity_file_reader)
        
        initViews()
        loadFile()
    }
    
    private fun initViews() {
        webView = findViewById(R.id.fileReaderWebView)
        progressBar = findViewById(R.id.fileReaderProgressBar)
        titleTextView = findViewById(R.id.fileReaderTitle)
        errorTextView = findViewById(R.id.fileReaderError)
        
        // 配置WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                Log.d(TAG, "页面加载完成: $url")
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                progressBar.visibility = View.GONE
                showError("加载失败: $description")
                Log.e(TAG, "页面加载错误: $description, URL: $failingUrl")
            }
        }
        
        // 返回按钮
        findViewById<View>(R.id.fileReaderBackButton)?.setOnClickListener {
            finish()
        }
    }
    
    private fun loadFile() {
        val fileUri = intent.getStringExtra(EXTRA_FILE_URI)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        
        scope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                errorTextView.visibility = View.GONE
                
                val uri = when {
                    !filePath.isNullOrEmpty() -> {
                        Uri.fromFile(File(filePath))
                    }
                    !fileUri.isNullOrEmpty() -> {
                        Uri.parse(fileUri)
                    }
                    else -> {
                        showError("未提供文件路径或URI")
                        return@launch
                    }
                }
                
                val actualFileName = fileName ?: getFileNameFromUri(uri)
                titleTextView.text = actualFileName
                
                Log.d(TAG, "开始加载文件: $uri, 文件名: $actualFileName")
                
                // 根据文件扩展名选择加载方式
                val extension = getFileExtension(actualFileName).lowercase()
                when (extension) {
                    "txt" -> loadTextFile(uri)
                    "pdf" -> loadPdfFile(uri)
                    "epub", "mobi", "azw", "azw3", "azw4", "prc", "pdb" -> {
                        // 电子书格式，尝试使用WebView加载
                        // 注意：这些格式需要专门的解析库，这里先尝试直接加载
                        showError("电子书格式($extension)需要专门的阅读器，建议使用外部应用打开")
                    }
                    else -> {
                        showError("不支持的文件格式: $extension")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "加载文件异常", e)
                showError("加载文件失败: ${e.message}")
            }
        }
    }
    
    /**
     * 加载文本文件
     */
    private suspend fun loadTextFile(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (file.exists()) file.inputStream() else null
                }
                "content" -> {
                    contentResolver.openInputStream(uri)
                }
                else -> null
            }
            
            inputStream?.use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                
                withContext(Dispatchers.Main) {
                    // 将文本内容转换为HTML格式显示
                    val htmlContent = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                                    font-size: 16px;
                                    line-height: 1.6;
                                    color: #333;
                                    padding: 20px;
                                    max-width: 800px;
                                    margin: 0 auto;
                                    background-color: #fff;
                                }
                                pre {
                                    white-space: pre-wrap;
                                    word-wrap: break-word;
                                }
                            </style>
                        </head>
                        <body>
                            <pre>${escapeHtml(text)}</pre>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    Log.d(TAG, "文本文件加载成功")
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    showError("无法读取文件")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载文本文件失败", e)
            withContext(Dispatchers.Main) {
                showError("加载文本文件失败: ${e.message}")
            }
        }
    }
    
    /**
     * 加载PDF文件
     */
    private fun loadPdfFile(uri: Uri) {
        try {
            // 对于PDF文件，使用Google Docs Viewer或直接加载
            // 注意：Android WebView不支持直接显示PDF，需要使用外部服务或PDF库
            val pdfUrl = when (uri.scheme) {
                "file" -> {
                    // 将本地文件转换为可访问的URI
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        // 使用FileProvider提供访问
                        try {
                            FileProvider.getUriForFile(
                                this,
                                "${packageName}.fileprovider",
                                file
                            ).toString()
                        } catch (e: Exception) {
                            // 如果FileProvider不可用，使用Google Docs Viewer
                            "https://docs.google.com/viewer?url=${Uri.fromFile(file)}&embedded=true"
                        }
                    } else {
                        null
                    }
                }
                "content" -> {
                    // Content URI，尝试使用Google Docs Viewer
                    "https://docs.google.com/viewer?url=$uri&embedded=true"
                }
                "http", "https" -> {
                    // 网络URL，直接使用Google Docs Viewer
                    "https://docs.google.com/viewer?url=$uri&embedded=true"
                }
                else -> null
            }
            
            if (pdfUrl != null) {
                webView.loadUrl(pdfUrl)
                Log.d(TAG, "PDF文件加载URL: $pdfUrl")
            } else {
                showError("无法加载PDF文件")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载PDF文件失败", e)
            showError("加载PDF文件失败: ${e.message}")
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1)
        } else {
            ""
        }
    }
    
    /**
     * 从URI获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: ""
                File(path).name
            }
            "content" -> {
                // 尝试从ContentResolver获取文件名
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        uri.lastPathSegment ?: "未知文件"
                    }
                } ?: (uri.lastPathSegment ?: "未知文件")
            }
            else -> uri.lastPathSegment ?: "未知文件"
        }
    }
    
    /**
     * HTML转义
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        webView.destroy()
    }
}

