# 搜索Tab网页返回功能问题分析与修复方案

## 🔍 问题分析

### 问题现象
用户在搜索tab浏览网页时，有些特殊页面无法实现跳转到上一页，而是跳转到主页。

### 根本原因

#### 1. WebView历史记录被意外清除
```kotlin
// 问题代码示例
webView.loadUrl("about:blank")  // 这会清除历史记录
webView.clearHistory()          // 直接清除历史记录
```

#### 2. 特殊页面的URL拦截问题
```kotlin
override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    // 某些拦截逻辑可能导致历史记录异常
    return when {
        url.startsWith("baiduboxapp://") -> {
            handleSearchEngineRedirect(view, url, "baidu")
            true  // 返回true可能影响历史记录
        }
        // ...
    }
}
```

#### 3. 多WebView管理器冲突
- MobileCardManager
- GestureCardWebViewManager  
- MultiPageWebViewManager
- 多个管理器之间的切换可能导致历史记录丢失

#### 4. 页面重定向处理不当
- 搜索引擎重定向处理
- JavaScript注入干扰
- 移动端适配重定向

## 🛠️ 修复方案

### 1. 优化WebView历史记录管理

#### 创建历史记录管理器
```kotlin
class WebViewHistoryManager {
    private val historyStack = mutableListOf<String>()
    private val maxHistorySize = 50
    
    fun addToHistory(url: String) {
        if (url.isNotEmpty() && url != "about:blank") {
            // 避免重复添加相同URL
            if (historyStack.isEmpty() || historyStack.last() != url) {
                historyStack.add(url)
                
                // 限制历史记录大小
                if (historyStack.size > maxHistorySize) {
                    historyStack.removeAt(0)
                }
                
                Log.d(TAG, "添加到历史记录: $url, 当前历史记录数: ${historyStack.size}")
            }
        }
    }
    
    fun canGoBack(): Boolean = historyStack.size > 1
    
    fun goBack(): String? {
        return if (canGoBack()) {
            historyStack.removeAt(historyStack.size - 1)
            historyStack.lastOrNull()
        } else null
    }
    
    fun getCurrentUrl(): String? = historyStack.lastOrNull()
    
    fun clearHistory() {
        historyStack.clear()
        Log.d(TAG, "清除历史记录")
    }
}
```

### 2. 改进WebView返回逻辑

#### 统一的返回处理方法
```kotlin
private fun performUnifiedWebViewBack(source: String): Boolean {
    Log.d(TAG, "执行统一WebView后退操作，来源: $source")
    
    // 1. 首先检查当前WebView的历史记录
    val currentWebView = getCurrentActiveWebView()
    if (currentWebView?.canGoBack() == true) {
        val currentUrl = currentWebView.url
        Log.d(TAG, "当前URL: $currentUrl")
        
        // 检查是否是特殊页面
        if (isSpecialPage(currentUrl)) {
            return handleSpecialPageBack(currentWebView, source)
        }
        
        // 正常返回
        currentWebView.goBack()
        showBrowserGestureHint("网页后退")
        Log.d(TAG, "$source：正常返回上一页")
        return true
    }
    
    // 2. 检查历史记录管理器
    if (historyManager.canGoBack()) {
        val previousUrl = historyManager.goBack()
        if (previousUrl != null) {
            currentWebView?.loadUrl(previousUrl)
            showBrowserGestureHint("返回上一页")
            Log.d(TAG, "$source：通过历史记录管理器返回")
            return true
        }
    }
    
    // 3. 检查其他WebView管理器
    val handled = checkOtherWebViewManagers(source)
    if (handled) return true
    
    // 4. 无可返回页面，返回搜索tab首页
    showBrowserHome()
    showBrowserGestureHint("返回搜索首页")
    Log.d(TAG, "$source：无可返回页面，显示搜索tab首页")
    return false
}
```

### 3. 特殊页面处理优化

#### 识别和处理特殊页面
```kotlin
private fun isSpecialPage(url: String?): Boolean {
    if (url == null) return false
    
    return when {
        // 搜索引擎页面
        url.contains("google.com/search") -> true
        url.contains("baidu.com/s?") -> true
        url.contains("bing.com/search") -> true
        
        // 社交媒体页面
        url.contains("weibo.com") -> true
        url.contains("douyin.com") -> true
        url.contains("xiaohongshu.com") -> true
        
        // 电商页面
        url.contains("taobao.com") -> true
        url.contains("tmall.com") -> true
        url.contains("jd.com") -> true
        
        // 视频网站
        url.contains("youtube.com") -> true
        url.contains("bilibili.com") -> true
        
        // 新闻网站
        url.contains("news.") -> true
        
        else -> false
    }
}

private fun handleSpecialPageBack(webView: WebView, source: String): Boolean {
    val currentUrl = webView.url ?: ""
    Log.d(TAG, "处理特殊页面返回: $currentUrl")
    
    return when {
        // 搜索引擎页面 - 尝试返回搜索结果
        currentUrl.contains("google.com/search") -> {
            handleSearchEngineBack(webView, "google")
        }
        currentUrl.contains("baidu.com/s?") -> {
            handleSearchEngineBack(webView, "baidu")
        }
        
        // 社交媒体页面 - 使用JavaScript返回
        currentUrl.contains("weibo.com") -> {
            handleJavaScriptBack(webView, "history.back()")
        }
        currentUrl.contains("douyin.com") -> {
            handleJavaScriptBack(webView, "history.back()")
        }
        
        // 其他特殊页面
        else -> {
            // 尝试JavaScript返回
            handleJavaScriptBack(webView, "history.back()")
        }
    }
}
```

### 4. JavaScript返回处理

#### 安全的JavaScript返回
```kotlin
private fun handleJavaScriptBack(webView: WebView, jsCode: String): Boolean {
    return try {
        webView.evaluateJavascript(jsCode) { result ->
            Log.d(TAG, "JavaScript返回结果: $result")
            
            // 检查JavaScript是否成功执行
            if (result == "null" || result == "undefined") {
                // JavaScript返回失败，使用备用方案
                handleFallbackBack(webView)
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "JavaScript返回失败", e)
        handleFallbackBack(webView)
        false
    }
}

private fun handleFallbackBack(webView: WebView) {
    // 备用方案：尝试加载上一页URL
    val previousUrl = historyManager.goBack()
    if (previousUrl != null) {
        webView.loadUrl(previousUrl)
        Log.d(TAG, "使用备用方案返回: $previousUrl")
    } else {
        // 最后备用方案：返回搜索首页
        showBrowserHome()
        Log.d(TAG, "使用最后备用方案：返回搜索首页")
    }
}
```

### 5. 搜索引擎返回处理

#### 智能搜索引擎返回
```kotlin
private fun handleSearchEngineBack(webView: WebView, engine: String): Boolean {
    return try {
        when (engine) {
            "google" -> {
                // Google搜索页面返回处理
                webView.evaluateJavascript("""
                    if (window.history.length > 1) {
                        history.back();
                    } else {
                        window.location.href = 'https://www.google.com';
                    }
                """.trimIndent(), null)
            }
            "baidu" -> {
                // 百度搜索页面返回处理
                webView.evaluateJavascript("""
                    if (window.history.length > 1) {
                        history.back();
                    } else {
                        window.location.href = 'https://www.baidu.com';
                    }
                """.trimIndent(), null)
            }
            else -> {
                // 通用搜索引擎返回
                webView.evaluateJavascript("history.back()", null)
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "搜索引擎返回失败", e)
        false
    }
}
```

### 6. 改进URL拦截逻辑

#### 优化shouldOverrideUrlLoading
```kotlin
override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val url = request?.url?.toString()
    if (url == null) return false
    
    Log.d(TAG, "URL加载拦截: $url")
    
    // 添加到历史记录
    historyManager.addToHistory(url)
    
    return when {
        // 处理移动应用URL scheme重定向
        url.startsWith("baiduboxapp://") -> {
            Log.d(TAG, "拦截百度App重定向，保持在WebView中")
            handleSearchEngineRedirect(view, url, "baidu")
            true
        }
        url.startsWith("mttbrowser://") -> {
            Log.d(TAG, "拦截搜狗浏览器重定向，保持在WebView中")
            handleSearchEngineRedirect(view, url, "sogou")
            true
        }
        
        // 处理应用URL scheme
        url.contains("://") && !url.startsWith("http://") && !url.startsWith("https://") -> {
            Log.d(TAG, "检测到应用URL scheme: $url")
            handleAppUrlScheme(view, url)
            true
        }
        
        // 广告拦截
        url.contains("ads") || url.contains("doubleclick") -> {
            Log.d(TAG, "拦截广告URL: $url")
            true
        }
        
        // 正常HTTP/HTTPS链接
        url.startsWith("http://") || url.startsWith("https://") -> {
            // 不拦截，让WebView正常加载
            false
        }
        
        else -> false
    }
}
```

### 7. 多WebView管理器协调

#### 统一WebView管理器
```kotlin
class UnifiedWebViewManager {
    private val webViewManagers = mutableListOf<WebViewManager>()
    private var currentManager: WebViewManager? = null
    
    fun registerManager(manager: WebViewManager) {
        webViewManagers.add(manager)
        if (currentManager == null) {
            currentManager = manager
        }
    }
    
    fun getCurrentActiveWebView(): WebView? {
        return currentManager?.getCurrentWebView()
    }
    
    fun canGoBack(): Boolean {
        return webViewManagers.any { it.canGoBack() }
    }
    
    fun goBack(): Boolean {
        // 优先使用当前管理器
        if (currentManager?.canGoBack() == true) {
            return currentManager?.goBack() ?: false
        }
        
        // 检查其他管理器
        for (manager in webViewManagers) {
            if (manager.canGoBack()) {
                currentManager = manager
                return manager.goBack()
            }
        }
        
        return false
    }
}
```

## ✅ 测试验证

### 测试场景
1. **正常网页浏览**：测试普通网页的返回功能
2. **搜索引擎页面**：测试Google、百度等搜索引擎的返回
3. **社交媒体页面**：测试微博、抖音等社交媒体的返回
4. **电商页面**：测试淘宝、京东等电商网站的返回
5. **视频网站**：测试YouTube、B站等视频网站的返回
6. **特殊重定向**：测试各种重定向页面的返回

### 预期结果
- ✅ 正常网页能够正确返回上一页
- ✅ 特殊页面使用JavaScript返回或备用方案
- ✅ 搜索引擎页面智能返回到搜索结果或首页
- ✅ 社交媒体页面正确处理返回逻辑
- ✅ 多WebView管理器协调工作
- ✅ 历史记录管理正确

## 🔧 实施步骤

1. **创建历史记录管理器**
2. **优化WebView返回逻辑**
3. **实现特殊页面处理**
4. **改进URL拦截逻辑**
5. **统一多WebView管理器**
6. **测试验证修复效果**

通过以上修复方案，应该能够解决搜索tab中特殊页面无法正确返回上一页的问题。
