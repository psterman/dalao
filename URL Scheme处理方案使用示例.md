# URL Scheme处理方案使用示例

## 基本使用

### 1. 在WebViewClient中使用

```kotlin
class MyWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        
        return when {
            // 处理应用URL scheme
            url.contains("://") && !url.startsWith("http://") && !url.startsWith("https://") -> {
                handleAppUrlScheme(view, url)
                true
            }
            else -> false
        }
    }
    
    private fun handleAppUrlScheme(view: WebView?, url: String) {
        try {
            val context = view?.context
            if (context != null) {
                val urlSchemeHandler = UrlSchemeHandler(context)
                urlSchemeHandler.handleUrlScheme(
                    url = url,
                    onSuccess = {
                        Log.d("WebView", "URL scheme处理成功: $url")
                    },
                    onFailure = {
                        Log.w("WebView", "URL scheme处理失败: $url")
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("WebView", "处理URL scheme时出错: $url", e)
        }
    }
}
```

### 2. 在Activity中直接使用

```kotlin
class MainActivity : AppCompatActivity() {
    private fun handleUrlScheme(url: String) {
        val urlSchemeHandler = UrlSchemeHandler(this)
        urlSchemeHandler.handleUrlScheme(
            url = url,
            onSuccess = {
                // 处理成功
                Toast.makeText(this, "链接处理成功", Toast.LENGTH_SHORT).show()
            },
            onFailure = {
                // 处理失败
                Toast.makeText(this, "链接处理失败", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
```

## 高级使用

### 1. 自定义URL Scheme映射

```kotlin
class CustomUrlSchemeHandler(context: Context) : UrlSchemeHandler(context) {
    companion object {
        // 扩展已知的URL scheme映射
        private val CUSTOM_SCHEMES = mapOf(
            "myapp" to AppSchemeInfo(
                appName = "我的应用",
                packageName = "com.example.myapp",
                downloadUrl = "https://www.example.com/download",
                webUrl = "https://www.example.com",
                description = "我的应用 - 功能描述"
            )
        )
        
        init {
            // 合并自定义scheme到已知scheme中
            KNOWN_SCHEMES = KNOWN_SCHEMES + CUSTOM_SCHEMES
        }
    }
}
```

### 2. 自定义处理逻辑

```kotlin
class AdvancedUrlSchemeHandler(context: Context) : UrlSchemeHandler(context) {
    override fun handleKnownScheme(url: String, appInfo: AppSchemeInfo, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        // 自定义处理逻辑
        when (appInfo.appName) {
            "小红书" -> {
                // 小红书特殊处理
                handleXiaohongshuScheme(url, appInfo, onSuccess, onFailure)
            }
            "知乎" -> {
                // 知乎特殊处理
                handleZhihuScheme(url, appInfo, onSuccess, onFailure)
            }
            else -> {
                // 使用默认处理
                super.handleKnownScheme(url, appInfo, onSuccess, onFailure)
            }
        }
    }
    
    private fun handleXiaohongshuScheme(url: String, appInfo: AppSchemeInfo, onSuccess: (() -> Unit)?, onFailure: (() -> Unit)?) {
        // 小红书特殊处理逻辑
        // 例如：检查用户是否登录、特殊参数处理等
    }
}
```

### 3. 批量处理URL Scheme

```kotlin
class BatchUrlSchemeHandler(context: Context) {
    private val urlSchemeHandler = UrlSchemeHandler(context)
    
    fun handleMultipleSchemes(urls: List<String>) {
        urls.forEach { url ->
            urlSchemeHandler.handleUrlScheme(
                url = url,
                onSuccess = {
                    Log.d("BatchHandler", "处理成功: $url")
                },
                onFailure = {
                    Log.w("BatchHandler", "处理失败: $url")
                }
            )
        }
    }
}
```

## 实际应用场景

### 1. 社交媒体链接处理

```kotlin
// 处理社交媒体分享链接
fun handleSocialMediaLink(url: String) {
    val urlSchemeHandler = UrlSchemeHandler(context)
    urlSchemeHandler.handleUrlScheme(
        url = url,
        onSuccess = {
            // 记录分享成功
            analytics.track("social_share_success", mapOf("url" to url))
        },
        onFailure = {
            // 记录分享失败
            analytics.track("social_share_failure", mapOf("url" to url))
        }
    )
}
```

### 2. 电商链接处理

```kotlin
// 处理电商应用链接
fun handleEcommerceLink(url: String) {
    val urlSchemeHandler = UrlSchemeHandler(context)
    urlSchemeHandler.handleUrlScheme(
        url = url,
        onSuccess = {
            // 记录跳转成功
            analytics.track("ecommerce_redirect_success", mapOf("url" to url))
        },
        onFailure = {
            // 记录跳转失败
            analytics.track("ecommerce_redirect_failure", mapOf("url" to url))
        }
    )
}
```

### 3. 内容分享处理

```kotlin
// 处理内容分享链接
fun handleContentShareLink(url: String) {
    val urlSchemeHandler = UrlSchemeHandler(context)
    urlSchemeHandler.handleUrlScheme(
        url = url,
        onSuccess = {
            // 显示分享成功提示
            showShareSuccessToast()
        },
        onFailure = {
            // 显示分享失败提示
            showShareFailureToast()
        }
    )
}
```

## 错误处理

### 1. 异常捕获

```kotlin
fun safeHandleUrlScheme(url: String) {
    try {
        val urlSchemeHandler = UrlSchemeHandler(context)
        urlSchemeHandler.handleUrlScheme(url)
    } catch (e: Exception) {
        Log.e("UrlScheme", "处理URL scheme时出错: $url", e)
        // 显示错误提示
        showErrorToast("链接处理失败，请稍后重试")
    }
}
```

### 2. 降级处理

```kotlin
fun handleUrlSchemeWithFallback(url: String) {
    val urlSchemeHandler = UrlSchemeHandler(context)
    urlSchemeHandler.handleUrlScheme(
        url = url,
        onSuccess = {
            // 处理成功
        },
        onFailure = {
            // 降级处理：复制链接到剪贴板
            copyToClipboard(url)
            showToast("链接已复制到剪贴板")
        }
    )
}
```

## 性能优化

### 1. 单例模式

```kotlin
class UrlSchemeHandlerSingleton private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: UrlSchemeHandler? = null
        
        fun getInstance(context: Context): UrlSchemeHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UrlSchemeHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
```

### 2. 缓存优化

```kotlin
class CachedUrlSchemeHandler(context: Context) {
    private val urlSchemeHandler = UrlSchemeHandler(context)
    private val appInstallCache = mutableMapOf<String, Boolean>()
    
    fun handleUrlScheme(url: String) {
        val scheme = Uri.parse(url).scheme
        if (scheme != null) {
            val appInfo = KNOWN_SCHEMES[scheme]
            if (appInfo != null) {
                // 使用缓存检查应用安装状态
                val isInstalled = appInstallCache[appInfo.packageName] 
                    ?: isAppInstalled(appInfo.packageName).also { 
                        appInstallCache[appInfo.packageName] = it 
                    }
                
                if (isInstalled) {
                    // 应用已安装，直接跳转
                    tryJumpToApp(url, appInfo)
                } else {
                    // 应用未安装，显示下载提示
                    showDownloadDialog(appInfo)
                }
            }
        }
    }
}
```

## 测试用例

### 1. 单元测试

```kotlin
class UrlSchemeHandlerTest {
    private lateinit var urlSchemeHandler: UrlSchemeHandler
    private lateinit var mockContext: Context
    
    @Before
    fun setUp() {
        mockContext = mockk<Context>()
        urlSchemeHandler = UrlSchemeHandler(mockContext)
    }
    
    @Test
    fun testHandleKnownScheme() {
        val url = "xhsdiscover://item/1234567890"
        urlSchemeHandler.handleUrlScheme(url)
        // 验证处理结果
    }
    
    @Test
    fun testHandleUnknownScheme() {
        val url = "unknownapp://action/1234567890"
        urlSchemeHandler.handleUrlScheme(url)
        // 验证处理结果
    }
}
```

### 2. 集成测试

```kotlin
class UrlSchemeIntegrationTest {
    @Test
    fun testWebViewIntegration() {
        val webView = WebView(InstrumentationRegistry.getTargetContext())
        val webViewClient = MyWebViewClient()
        webView.webViewClient = webViewClient
        
        // 测试URL scheme处理
        val url = "xhsdiscover://item/1234567890"
        val result = webViewClient.shouldOverrideUrlLoading(webView, url)
        
        assertTrue(result)
    }
}
```

## 注意事项

1. **权限检查**: 确保应用有必要的权限
2. **异常处理**: 妥善处理各种异常情况
3. **用户体验**: 提供清晰的提示信息
4. **性能考虑**: 避免频繁创建实例
5. **兼容性**: 考虑不同Android版本的兼容性

## 最佳实践

1. **统一处理**: 在所有WebView中使用统一的URL scheme处理逻辑
2. **错误处理**: 提供完善的错误处理和降级方案
3. **用户反馈**: 及时向用户反馈处理结果
4. **性能优化**: 使用缓存和单例模式优化性能
5. **测试覆盖**: 编写完整的测试用例
