package com.example.aifloatingball.utils

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.util.Log
import java.net.URLEncoder

/**
 * WebView Markdown渲染器
 * 使用WebView和JavaScript库来渲染复杂的Markdown内容
 * 支持marked.js、highlight.js、prism.js等库
 */
class WebViewMarkdownRenderer(private val context: Context) {
    
    companion object {
        private const val TAG = "WebViewMarkdownRenderer"
    }
    
    /**
     * 渲染AI回复到WebView
     */
    fun renderAIResponse(webView: WebView, content: String) {
        try {
            // 配置WebView
            setupWebView(webView)
            
            // 生成HTML内容
            val htmlContent = generateHtmlContent(content)
            
            // 加载HTML内容
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
            
            Log.d(TAG, "AI回复已渲染到WebView")
            
        } catch (e: Exception) {
            Log.e(TAG, "渲染AI回复失败", e)
        }
    }
    
    /**
     * 配置WebView
     */
    private fun setupWebView(webView: WebView) {
        val settings = webView.settings
        
        // 基本设置
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        // setAppCacheEnabled在API 33+中已废弃，移除使用
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // 支持缩放
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        
        // 支持混合内容
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        // 设置WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "页面加载完成")
            }
        }
        
        // 设置WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                Log.d(TAG, "WebView Console: ${consoleMessage?.message()}")
                return true
            }
        }
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(WebViewInterface(), "Android")
    }
    
    /**
     * 生成HTML内容
     */
    private fun generateHtmlContent(content: String): String {
        val encodedContent = URLEncoder.encode(content, "UTF-8")
        
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI回复</title>
    
    <!-- 引入marked.js -->
    <script src="https://cdn.jsdelivr.net/npm/marked@4.3.0/marked.min.js"></script>
    
    <!-- 引入highlight.js -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/highlight.js@11.8.0/styles/github.min.css">
    <script src="https://cdn.jsdelivr.net/npm/highlight.js@11.8.0/highlight.min.js"></script>
    
    <!-- 引入prism.js -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/themes/prism.min.css">
    <script src="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/components/prism-core.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/prismjs@1.29.0/plugins/autoloader/prism-autoloader.min.js"></script>
    
    <!-- 自定义样式 -->
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            background-color: #fff;
            padding: 16px;
            margin: 0;
            font-size: 14px;
        }
        
        /* 标题样式 */
        h1, h2, h3, h4, h5, h6 {
            color: #2c3e50;
            margin-top: 24px;
            margin-bottom: 16px;
            font-weight: 600;
            line-height: 1.25;
        }
        
        h1 { font-size: 1.5em; border-bottom: 1px solid #eaecef; padding-bottom: 8px; }
        h2 { font-size: 1.3em; border-bottom: 1px solid #eaecef; padding-bottom: 6px; }
        h3 { font-size: 1.2em; }
        h4 { font-size: 1.1em; }
        h5 { font-size: 1em; }
        h6 { font-size: 0.9em; color: #6a737d; }
        
        /* 段落样式 */
        p {
            margin-bottom: 16px;
            text-align: justify;
        }
        
        /* 列表样式 */
        ul, ol {
            margin-bottom: 16px;
            padding-left: 24px;
        }
        
        li {
            margin-bottom: 8px;
        }
        
        /* 代码块样式 */
        pre {
            background-color: #f6f8fa;
            border-radius: 6px;
            padding: 16px;
            overflow-x: auto;
            margin-bottom: 16px;
            border: 1px solid #e1e4e8;
        }
        
        code {
            background-color: #f6f8fa;
            padding: 2px 4px;
            border-radius: 3px;
            font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
            font-size: 0.9em;
        }
        
        pre code {
            background-color: transparent;
            padding: 0;
            border-radius: 0;
        }
        
        /* 引用样式 */
        blockquote {
            border-left: 4px solid #dfe2e5;
            padding-left: 16px;
            margin: 16px 0;
            color: #6a737d;
            background-color: #f8f9fa;
            padding: 16px;
            border-radius: 0 6px 6px 0;
        }
        
        /* 表格样式 */
        table {
            border-collapse: collapse;
            width: 100%;
            margin-bottom: 16px;
        }
        
        th, td {
            border: 1px solid #dfe2e5;
            padding: 8px 12px;
            text-align: left;
        }
        
        th {
            background-color: #f6f8fa;
            font-weight: 600;
        }
        
        /* 链接样式 */
        a {
            color: #0366d6;
            text-decoration: none;
        }
        
        a:hover {
            text-decoration: underline;
        }
        
        /* 强调样式 */
        strong, b {
            font-weight: 600;
            color: #2c3e50;
        }
        
        em, i {
            font-style: italic;
        }
        
        del, s {
            text-decoration: line-through;
            color: #6a737d;
        }
        
        /* 特殊结构样式 */
        .ai-question {
            background-color: #e3f2fd;
            border-left: 4px solid #2196f3;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 0 6px 6px 0;
        }
        
        .ai-answer {
            background-color: #f3e5f5;
            border-left: 4px solid #9c27b0;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 0 6px 6px 0;
        }
        
        .ai-step {
            background-color: #fff3e0;
            border-left: 4px solid #ff9800;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 0 6px 6px 0;
        }
        
        .ai-tip {
            background-color: #e8f5e8;
            border-left: 4px solid #4caf50;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 0 6px 6px 0;
        }
        
        .ai-warning {
            background-color: #fff3e0;
            border-left: 4px solid #ff5722;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 0 6px 6px 0;
        }
        
        .ai-summary {
            background-color: #f1f8e9;
            border-left: 4px solid #8bc34a;
            padding: 12px 16px;
            margin: 16px 0;
            border-radius: 0 6px 6px 0;
        }
        
        /* 响应式设计 */
        @media (max-width: 768px) {
            body {
                padding: 12px;
                font-size: 13px;
            }
            
            h1 { font-size: 1.3em; }
            h2 { font-size: 1.2em; }
            h3 { font-size: 1.1em; }
            
            pre {
                padding: 12px;
                font-size: 12px;
            }
        }
        
        /* 深色模式支持 */
        @media (prefers-color-scheme: dark) {
            body {
                background-color: #1e1e1e;
                color: #e0e0e0;
            }
            
            h1, h2, h3, h4, h5, h6 {
                color: #ffffff;
            }
            
            pre {
                background-color: #2d2d2d;
                border-color: #404040;
            }
            
            code {
                background-color: #2d2d2d;
            }
            
            blockquote {
                background-color: #2d2d2d;
                border-color: #404040;
                color: #b0b0b0;
            }
            
            table th, table td {
                border-color: #404040;
            }
            
            table th {
                background-color: #2d2d2d;
            }
        }
    </style>
</head>
<body>
    <div id="content"></div>
    
    <script>
        // 配置marked.js
        marked.setOptions({
            breaks: true,
            gfm: true,
            tables: true,
            pedantic: false,
            sanitize: false,
            smartLists: true,
            smartypants: false
        });
        
        // 配置highlight.js
        hljs.configure({
            languages: ['javascript', 'java', 'python', 'kotlin', 'swift', 'go', 'rust', 'cpp', 'c', 'csharp', 'php', 'ruby', 'html', 'css', 'xml', 'json', 'yaml', 'sql', 'bash', 'shell', 'typescript', 'dart', 'scala', 'r', 'matlab', 'perl', 'lua', 'powershell']
        });
        
        // 自定义渲染器
        const renderer = new marked.Renderer();
        
        // 自定义代码块渲染
        renderer.code = function(code, language) {
            const validLang = language && hljs.getLanguage(language) ? language : 'plaintext';
            const highlighted = hljs.highlight(code, { language: validLang }).value;
            return '<pre><code class="hljs language-' + validLang + '">' + highlighted + '</code></pre>';
        };
        
        // 自定义段落渲染
        renderer.paragraph = function(text) {
            // 处理特殊结构
            if (text.includes('❓') || text.includes('问：')) {
                return '<div class="ai-question">' + text + '</div>';
            } else if (text.includes('💡') || text.includes('答：')) {
                return '<div class="ai-answer">' + text + '</div>';
            } else if (text.includes('📋') || text.includes('步骤')) {
                return '<div class="ai-step">' + text + '</div>';
            } else if (text.includes('💡') || text.includes('提示')) {
                return '<div class="ai-tip">' + text + '</div>';
            } else if (text.includes('⚠️') || text.includes('注意')) {
                return '<div class="ai-warning">' + text + '</div>';
            } else if (text.includes('📝') || text.includes('总结') || text.includes('结论')) {
                return '<div class="ai-summary">' + text + '</div>';
            }
            return '<p>' + text + '</p>';
        };
        
        // 自定义列表渲染
        renderer.listitem = function(text) {
            // 处理特殊列表项
            if (text.includes('•') || text.includes('◦') || text.includes('▪')) {
                return '<li>' + text + '</li>';
            }
            return '<li>' + text + '</li>';
        };
        
        // 设置自定义渲染器
        marked.use({ renderer });
        
        // 处理特殊格式
        function processSpecialFormats(text) {
            // 处理中文标题格式
            text = text.replace(/^([一二三四五六七八九十]+[、.])\s*(.*)$/gm, '## $1 $2');
            text = text.replace(/^([0-9]+[、.])\s*(.*)$/gm, '## $1 $2');
            text = text.replace(/^([^：:]+[：:])\s*$/gm, '## $1');
            
            // 处理特殊结构
            text = text.replace(/^问[:：]\s*(.*)$/gm, '❓ 问：$1');
            text = text.replace(/^答[:：]\s*(.*)$/gm, '💡 答：$1');
            text = text.replace(/^步骤\s*(\d+)[:：]\s*(.*)$/gm, '📋 步骤$1：$2');
            text = text.replace(/^第\s*(\d+)\s*步[:：]\s*(.*)$/gm, '📋 第$1步：$2');
            text = text.replace(/^要点\s*(\d+)[:：]\s*(.*)$/gm, '🔹 要点$1：$2');
            text = text.replace(/^注意[:：]\s*(.*)$/gm, '⚠️ 注意：$1');
            text = text.replace(/^提示[:：]\s*(.*)$/gm, '💡 提示：$1');
            text = text.replace(/^总结[:：]\s*(.*)$/gm, '📝 总结：$1');
            text = text.replace(/^结论[:：]\s*(.*)$/gm, '📝 结论：$1');
            
            // 处理DeepSeek风格的特殊格式
            text = text.replace(/^好的[，,]?\s*(.*)$/gm, '💡 $1');
            text = text.replace(/^这里\s*(.*)$/gm, '📖 $1');
            text = text.replace(/^核心\s*(.*)$/gm, '⭐ 核心$1');
            text = text.replace(/^主要\s*(.*)$/gm, '🔸 主要$1');
            text = text.replace(/^特点[:：]\s*(.*)$/gm, '✨ 特点：$1');
            text = text.replace(/^特色[:：]\s*(.*)$/gm, '✨ 特色：$1');
            text = text.replace(/^优势[:：]\s*(.*)$/gm, '🚀 优势：$1');
            text = text.replace(/^优点[:：]\s*(.*)$/gm, '🚀 优点：$1');
            
            return text;
        }
        
        // 渲染内容
        function renderContent() {
            const content = decodeURIComponent('$encodedContent');
            const processedContent = processSpecialFormats(content);
            const htmlContent = marked.parse(processedContent);
            document.getElementById('content').innerHTML = htmlContent;
            
            // 应用代码高亮
            hljs.highlightAll();
            
            // 应用Prism.js高亮
            if (typeof Prism !== 'undefined') {
                Prism.highlightAll();
            }
            
            // 通知Android渲染完成
            if (window.Android) {
                window.Android.onRenderComplete();
            }
        }
        
        // 页面加载完成后渲染
        document.addEventListener('DOMContentLoaded', renderContent);
        
        // 如果页面已经加载完成，立即渲染
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', renderContent);
        } else {
            renderContent();
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
    
    /**
     * WebView JavaScript接口
     */
    private inner class WebViewInterface {
        @JavascriptInterface
        fun onRenderComplete() {
            Log.d(TAG, "WebView渲染完成")
        }
        
        @JavascriptInterface
        fun onError(error: String) {
            Log.e(TAG, "WebView渲染错误: $error")
        }
    }
}
