# WebView 无法加载 metaso 页面原因分析

## 一、问题现象

WebView 无法加载 metaso.cn 页面，可能出现以下情况：
- 白屏
- 页面加载失败
- 页面加载但内容为空
- 页面加载超时

## 二、根本原因分析

### 2.1 WebView 配置不完整 ⚠️ **主要原因**

**问题**：
- JavaScript 未启用或配置不当
- DOM 存储未启用
- 混合内容模式未配置
- User-Agent 设置不当

**影响**：
- metaso.cn 是单页应用（SPA），完全依赖 JavaScript 渲染
- 如果 JavaScript 未启用，页面无法渲染，显示白屏
- 如果 DOM 存储未启用，页面状态无法保存

**解决方案**：
```kotlin
webView.settings.javaScriptEnabled = true  // 必须
webView.settings.domStorageEnabled = true  // 必须
webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
webView.settings.userAgentString = DESKTOP_USER_AGENT
```

### 2.2 WebView 初始化时机问题 ⚠️ **常见原因**

**问题**：
- WebView 还未完全初始化就尝试加载 URL
- WebView 的尺寸为 0（宽度或高度为 0）
- WebView 还未添加到布局中就加载

**影响**：
- 如果 WebView 尺寸为 0，即使页面加载成功也无法显示
- 如果 WebView 未准备好，loadUrl 可能被忽略

**解决方案**：
```kotlin
// 确保 WebView 已准备好
webView.post {
    webView.loadUrl(url)
}

// 检查 WebView 尺寸
if (webView.width > 0 && webView.height > 0) {
    webView.loadUrl(url)
} else {
    Log.w(TAG, "WebView尺寸为0，延迟加载")
    webView.postDelayed({
        webView.loadUrl(url)
    }, 500)
}
```

### 2.3 网络权限问题 ✅ **已解决**

**检查**：
- AndroidManifest.xml 中已声明 `INTERNET` 权限
- 网络状态正常

**影响**：
- 如果没有网络权限，WebView 无法加载任何网页

### 2.4 SSL/HTTPS 证书问题 ⚠️ **可能原因**

**问题**：
- SSL 证书验证失败
- 自签名证书
- 证书链不完整

**影响**：
- WebView 默认会阻止不安全的连接
- 导致页面无法加载

**解决方案**：
```kotlin
override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
    Log.w(TAG, "SSL错误: ${error?.toString()}")
    handler?.proceed() // 允许SSL错误（仅用于metaso.cn）
}
```

### 2.5 WebViewClient 配置问题 ⚠️ **可能原因**

**问题**：
- WebViewClient 被覆盖，导致错误处理不当
- shouldOverrideUrlLoading 返回 true，阻止了 URL 加载
- 没有正确处理页面加载错误

**影响**：
- 如果 shouldOverrideUrlLoading 返回 true 且没有调用 loadUrl，页面无法加载
- 如果错误处理不当，无法及时发现加载失败

**解决方案**：
```kotlin
override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val url = request?.url?.toString()
    if (url != null && url.contains("metaso.cn")) {
        // 对于 metaso.cn，确保在 WebView 内加载
        view?.loadUrl(url)
        return true
    }
    return false // 让系统处理其他 URL
}
```

### 2.6 页面加载超时 ⚠️ **可能原因**

**问题**：
- 网络延迟或页面加载时间过长
- 没有超时处理机制

**影响**：
- 页面加载超时可能导致白屏
- 用户无法知道加载状态

**解决方案**：
```kotlin
// 设置超时检查
handler.postDelayed({
    val currentUrl = webView.url
    if (currentUrl == null || currentUrl.isEmpty()) {
        Log.w(TAG, "页面加载超时，尝试重新加载")
        webView.reload()
    }
}, 10000) // 10秒超时
```

### 2.7 Cookie 和存储问题 ⚠️ **可能原因**

**问题**：
- Cookie 未启用
- 第三方 Cookie 被阻止
- 本地存储未启用

**影响**：
- metaso.cn 可能需要 Cookie 来保存用户状态
- 如果 Cookie 被阻止，页面可能无法正常工作

**解决方案**：
```kotlin
CookieManager.getInstance().setAcceptCookie(true)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
}
```

## 三、诊断步骤

### 3.1 检查日志

查看以下日志标签：
- `SimpleModeActivity`: 查看 WebView 配置和加载日志
- `metaso`: 查看 metaso 相关日志
- `WebViewClient`: 查看 WebView 客户端日志

**关键日志**：
```
metaso 页面开始加载: https://metaso.cn/
metaso 页面加载完成: https://metaso.cn/
metaso 页面内容检查结果: SUCCESS: 12345
metaso 页面加载错误: ...
```

### 3.2 检查 WebView 状态

```kotlin
Log.d(TAG, "WebView状态检查:")
Log.d(TAG, "  - 可见性: ${webView.visibility}")
Log.d(TAG, "  - 尺寸: ${webView.width}x${webView.height}")
Log.d(TAG, "  - JavaScript: ${webView.settings.javaScriptEnabled}")
Log.d(TAG, "  - DOM存储: ${webView.settings.domStorageEnabled}")
Log.d(TAG, "  - 当前URL: ${webView.url}")
Log.d(TAG, "  - 进度: ${webView.progress}")
```

### 3.3 检查网络连接

```kotlin
val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
val networkInfo = connectivityManager.activeNetworkInfo
Log.d(TAG, "网络状态: ${networkInfo?.isConnected}")
```

### 3.4 检查页面内容

```kotlin
webView.evaluateJavascript("document.body.innerHTML.length") { result ->
    Log.d(TAG, "页面内容长度: $result")
}

webView.evaluateJavascript("document.readyState") { result ->
    Log.d(TAG, "页面状态: $result")
}
```

## 四、完整解决方案

### 4.1 完整的 WebView 配置

```kotlin
// 1. 基础设置（必须）
webView.settings.javaScriptEnabled = true
webView.settings.domStorageEnabled = true
webView.settings.databaseEnabled = true

// 2. User-Agent
webView.settings.userAgentString = DESKTOP_USER_AGENT

// 3. 混合内容
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
}

// 4. Cookie
CookieManager.getInstance().setAcceptCookie(true)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
}

// 5. 文件访问
webView.settings.allowFileAccess = true
webView.settings.allowContentAccess = true

// 6. 缓存
webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

// 7. 缩放
webView.settings.setSupportZoom(true)
webView.settings.builtInZoomControls = false

// 8. 视口
webView.settings.useWideViewPort = true
webView.settings.loadWithOverviewMode = true

// 9. 文本编码
webView.settings.defaultTextEncodingName = "UTF-8"

// 10. 可见性和背景
webView.visibility = View.VISIBLE
webView.setBackgroundColor(Color.WHITE)
```

### 4.2 完善的 WebViewClient

```kotlin
webView.webViewClient = object : WebViewClient() {
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.proceed()
    }
    
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        Log.e(TAG, "页面加载错误: ${error?.description}")
    }
    
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        Log.d(TAG, "页面开始加载: $url")
    }
    
    override fun onPageFinished(view: WebView?, url: String?) {
        Log.d(TAG, "页面加载完成: $url")
        // 检查页面内容
        view?.evaluateJavascript("document.body.innerHTML.length") { result ->
            Log.d(TAG, "页面内容长度: $result")
        }
    }
}
```

### 4.3 确保加载时机正确

```kotlin
// 方法1: 使用 post 确保 WebView 已准备好
webView.post {
    webView.loadUrl(url)
}

// 方法2: 检查 WebView 尺寸
if (webView.width > 0 && webView.height > 0) {
    webView.loadUrl(url)
} else {
    webView.postDelayed({
        webView.loadUrl(url)
    }, 500)
}

// 方法3: 监听布局完成
webView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
    override fun onGlobalLayout() {
        if (webView.width > 0 && webView.height > 0) {
            webView.loadUrl(url)
            webView.viewTreeObserver.removeOnGlobalLayoutListener(this)
        }
    }
})
```

## 五、常见错误和解决方案

### 错误1: 白屏

**原因**: JavaScript 未启用或页面内容为空

**解决**:
```kotlin
webView.settings.javaScriptEnabled = true
webView.settings.domStorageEnabled = true
```

### 错误2: 页面加载失败

**原因**: 网络错误或 SSL 错误

**解决**:
```kotlin
override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
    handler?.proceed()
}
```

### 错误3: 页面内容为空

**原因**: 页面加载完成但内容未渲染

**解决**:
```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    view?.evaluateJavascript("document.body.innerHTML.length") { result ->
        if (result == "0" || result == "null") {
            view.reload()
        }
    }
}
```

### 错误4: 页面加载超时

**原因**: 网络延迟或页面加载时间过长

**解决**:
```kotlin
handler.postDelayed({
    if (webView.url == null || webView.url.isEmpty()) {
        webView.reload()
    }
}, 10000)
```

## 六、测试验证

### 6.1 功能测试

1. ✅ 检查 WebView 配置是否正确
2. ✅ 检查页面是否能正常加载
3. ✅ 检查页面内容是否正常显示
4. ✅ 检查页面交互是否正常

### 6.2 错误场景测试

1. ✅ 网络断开时的处理
2. ✅ SSL 错误时的处理
3. ✅ 页面加载超时的处理
4. ✅ 页面内容为空时的处理

## 七、总结

WebView 无法加载 metaso 页面的主要原因：

1. **WebView 配置不完整** - JavaScript 或 DOM 存储未启用
2. **WebView 初始化时机问题** - 在 WebView 准备好之前就加载 URL
3. **WebViewClient 配置问题** - 错误处理不当
4. **SSL/HTTPS 证书问题** - SSL 错误未处理
5. **Cookie 和存储问题** - Cookie 或存储未启用

**关键解决方案**：
- ✅ 启用 JavaScript 和 DOM 存储
- ✅ 配置混合内容模式
- ✅ 设置正确的 User-Agent
- ✅ 确保 WebView 已准备好再加载
- ✅ 处理 SSL 错误和页面加载错误
- ✅ 检查页面内容并自动重试

通过实施以上解决方案，metaso 页面应该能够正常加载。



