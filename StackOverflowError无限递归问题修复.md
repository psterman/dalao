# StackOverflowError 无限递归问题修复

## 一、错误信息

```
java.lang.StackOverflowError: stack size 8192KB
at com.example.aifloatingball.SimpleModeActivity$loadMetasoHomePages$configureMetasoWebView$1$1$1.onPageFinished(SimpleModeActivity.kt:33284)
at com.example.aifloatingball.ui.webview.CustomWebView$setWebViewClient$1.onPageFinished(CustomWebView.kt:99)
```

## 二、问题根源分析

### 2.1 无限递归的原因

**问题流程**：
1. `CustomWebView.setWebViewClient()` 会包装一层 WebViewClient
2. 包装后的 WebViewClient 的 `onPageFinished` 会调用 `delegatedClient.onPageFinished`
3. 我们在 `loadMetasoHomePages` 中获取了 `currentClient`（CustomWebView 包装的）
4. 然后创建新的 `metasoClient`，在 `onPageFinished` 中调用 `currentClient.onPageFinished`
5. 但是 `currentClient` 是 CustomWebView 包装的，它的 `onPageFinished` 会调用 `delegatedClient.onPageFinished`
6. 如果 `delegatedClient` 就是我们新创建的 `metasoClient`，就会形成循环

**调用链**：
```
metasoClient.onPageFinished()
  ↓
currentClient.onPageFinished() (CustomWebView包装的)
  ↓
delegatedClient.onPageFinished() (如果delegatedClient是metasoClient)
  ↓
metasoClient.onPageFinished() (循环！)
```

### 2.2 CustomWebView 的包装机制

```kotlin
// CustomWebView.kt
override fun setWebViewClient(client: WebViewClient) {
    delegatedClient = client  // 保存传入的client
    super.setWebViewClient(object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            delegatedClient?.onPageFinished(view, url)  // 调用保存的client
        }
    })
}
```

## 三、解决方案

### 3.1 使用静态 Set 防止递归

**关键修复**：
1. 使用静态 Set `processingMetasoWebViews` 跟踪正在处理的 WebView
2. 在 `onPageFinished` 开始时检查 WebView 是否正在处理
3. 如果正在处理，直接返回，避免递归
4. 处理完成后从 Set 中移除

```kotlin
companion object {
    // 用于防止WebView onPageFinished递归调用的Set
    private val processingMetasoWebViews = mutableSetOf<android.webkit.WebView>()
}

override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
    // 防止递归调用：使用静态Set检查WebView是否正在处理
    if (view != null && processingMetasoWebViews.contains(view)) {
        Log.w(TAG, "onPageFinished正在处理中，跳过递归调用: $url")
        return
    }
    
    if (url != null && url.contains("metaso.cn") && view != null) {
        processingMetasoWebViews.add(view)
        try {
            // 处理metaso页面
        } finally {
            processingMetasoWebViews.remove(view)
        }
    }
}
```

### 3.2 不调用 currentClient 或 super

**关键修复**：
- 对于 metaso URL，我们自己处理，不调用 `currentClient` 或 `super`
- 对于非 metaso URL，不处理，让 CustomWebView 的包装层处理
- 这样避免了循环调用

```kotlin
override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
    if (url != null && url.contains("metaso.cn")) {
        // 只处理metaso URL，不调用currentClient或super
    } else {
        // 对于非metaso URL，不处理，让CustomWebView的包装层处理
        // 不调用super或currentClient，避免循环
    }
}
```

### 3.3 使用 Tag 防止重复设置

**关键修复**：
- 使用 Tag 标记 WebView 是否已设置 metaso WebViewClient
- 避免重复设置导致问题

```kotlin
val tagKey = "metaso_webview_client_set"
val isAlreadySet = it.getTag(tagKey) as? Boolean ?: false

if (!isAlreadySet) {
    it.setTag(tagKey, true)
    // 设置WebViewClient
}
```

## 四、修复后的代码结构

```kotlin
companion object {
    // 用于防止WebView onPageFinished递归调用的Set
    private val processingMetasoWebViews = mutableSetOf<android.webkit.WebView>()
}

private fun loadMetasoHomePages() {
    val configureMetasoWebView: (CustomWebView?) -> Unit = { webView ->
        webView?.let {
            // ... WebView配置 ...
            
            // 检查是否已设置
            val tagKey = "metaso_webview_client_set"
            val isAlreadySet = it.getTag(tagKey) as? Boolean ?: false
            
            if (!isAlreadySet) {
                it.setTag(tagKey, true)
                
                val metasoClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // 防止递归
                        if (view != null && processingMetasoWebViews.contains(view)) {
                            return
                        }
                        
                        if (url != null && url.contains("metaso.cn") && view != null) {
                            processingMetasoWebViews.add(view)
                            try {
                                // 处理metaso页面
                            } finally {
                                processingMetasoWebViews.remove(view)
                            }
                        }
                        // 不调用super或currentClient，避免循环
                    }
                }
                
                it.webViewClient = metasoClient
            }
        }
    }
}
```

## 五、关键修复点总结

1. ✅ **添加静态 Set** - `processingMetasoWebViews` 跟踪正在处理的 WebView
2. ✅ **递归检查** - 在 `onPageFinished` 开始时检查是否正在处理
3. ✅ **避免循环调用** - 不调用 `currentClient` 或 `super`，避免触发 CustomWebView 的包装层
4. ✅ **使用 Tag 标记** - 防止重复设置 WebViewClient
5. ✅ **异常处理** - 确保在 finally 块中移除 WebView

## 六、测试验证

### 6.1 功能测试

1. ✅ 加载 metaso 页面
2. ✅ 检查页面是否正常显示
3. ✅ 检查是否有 StackOverflowError

### 6.2 递归测试

1. ✅ 多次加载和卸载页面
2. ✅ 快速切换页面
3. ✅ 检查是否有无限递归

## 七、总结

StackOverflowError 的根本原因是：

1. **CustomWebView 的包装机制** - 会包装 WebViewClient，导致循环调用
2. **调用链形成循环** - metasoClient → currentClient → delegatedClient → metasoClient
3. **缺少递归保护** - 没有检查是否正在处理

**关键修复**：
- ✅ 使用静态 Set 防止递归调用
- ✅ 不调用 currentClient 或 super，避免循环
- ✅ 使用 Tag 防止重复设置
- ✅ 添加异常处理确保资源释放

通过这些修复，StackOverflowError 问题应该得到解决。










