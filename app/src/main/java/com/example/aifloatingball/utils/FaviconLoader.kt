package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Favicon加载器服务
 * 用于获取网站图标作为候补方案
 */
object FaviconLoader {

    private const val TAG = "FaviconLoader"
    
    // 内存缓存
    private val memoryCache: LruCache<String, Bitmap>
    
    // 网站到favicon URL的映射
    private val faviconUrls = ConcurrentHashMap<String, List<String>>()

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        memoryCache = LruCache<String, Bitmap>(cacheSize)
        
        // 初始化常用网站的favicon URL
        initFaviconUrls()
    }
    
    /**
     * 初始化常用网站的favicon URL
     */
    private fun initFaviconUrls() {
        faviconUrls["weixin.sogou.com"] = listOf(
            "https://weixin.sogou.com/favicon.ico",
            "https://www.sogou.com/favicon.ico"
        )
        faviconUrls["s.taobao.com"] = listOf(
            "https://s.taobao.com/favicon.ico",
            "https://www.taobao.com/favicon.ico"
        )
        faviconUrls["search.jd.com"] = listOf(
            "https://search.jd.com/favicon.ico",
            "https://www.jd.com/favicon.ico"
        )
        faviconUrls["www.zhihu.com"] = listOf(
            "https://www.zhihu.com/favicon.ico",
            "https://static.zhihu.com/heifetz/favicon.ico"
        )
        faviconUrls["mobile.yangkeduo.com"] = listOf(
            "https://mobile.yangkeduo.com/favicon.ico",
            "https://www.pinduoduo.com/favicon.ico"
        )
        faviconUrls["list.tmall.com"] = listOf(
            "https://list.tmall.com/favicon.ico",
            "https://www.tmall.com/favicon.ico"
        )
        faviconUrls["www.google.com"] = listOf(
            "https://www.google.com/favicon.ico",
            "https://www.google.com/images/branding/product/ico/googleg_lodp.ico"
        )
    }
    
    /**
     * 加载图标（兼容原有接口）
     * 优先使用Google的favicon服务获取网站图标
     */
    fun loadIcon(imageView: ImageView, url: String, defaultIconRes: Int) {
        val domain = extractDomain(url)
        val cacheKey = "favicon_$domain"
        
        // 检查内存缓存
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 设置默认图标
        imageView.setImageResource(defaultIconRes)
        imageView.tag = cacheKey
        
        // 异步加载favicon
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadFaviconFromUrl(domain)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    
                    withContext(Dispatchers.Main) {
                        if (imageView.tag == cacheKey) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load favicon for $domain: ${e.message}")
            }
        }
    }
    
    /**
     * 加载favicon图标
     */
    fun loadFavicon(imageView: ImageView, url: String) {
        val domain = extractDomain(url)
        val cacheKey = "favicon_$domain"
        
        // 检查内存缓存
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 设置默认图标
        imageView.setImageResource(android.R.drawable.ic_menu_search)
        imageView.tag = cacheKey
        
        // 异步加载favicon
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadFaviconFromUrl(domain)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    
                    withContext(Dispatchers.Main) {
                        if (imageView.tag == cacheKey) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load favicon for $domain: ${e.message}")
            }
        }
    }
    
    /**
     * 从URL加载favicon
     */
    private suspend fun loadFaviconFromUrl(domain: String): Bitmap? {
        val urls = faviconUrls[domain] ?: generateFaviconUrls(domain)
        
        Log.d(TAG, "🔍 Loading favicon for domain: $domain, trying ${urls.size} URLs")
        
        for (url in urls) {
            try {
                Log.d(TAG, "  Trying: $url")
                val bitmap = downloadBitmap(url)
                if (bitmap != null) {
                    Log.d(TAG, "✅ Successfully loaded from: $url")
                    return bitmap
                }
            } catch (e: Exception) {
                Log.d(TAG, "  ❌ Failed: $url - ${e.message}")
                continue
            }
        }
        
        Log.e(TAG, "❌ All URLs failed for domain: $domain")
        return null
    }
    
    /**
     * 生成favicon URL列表
     * 优先使用Google的favicon服务，因为它的可靠性和覆盖面最广
     */
    private fun generateFaviconUrls(domain: String): List<String> {
        return listOf(
            // 1. 优先使用Google的favicon服务（最可靠）
            "https://www.google.com/s2/favicons?domain=$domain&sz=64",
            "https://www.google.com/s2/favicons?domain=$domain&sz=128",
            "https://www.google.com/s2/favicons?domain=$domain&sz=32",
            // 2. 使用网站自带的favicon
            "https://$domain/favicon.ico",
            "https://$domain/favicon-32x32.png",
            "https://$domain/favicon-96x96.png",
            "https://$domain/apple-touch-icon.png",
            // 3. 备用：DuckDuckGo的favicon服务
            "https://icons.duckduckgo.com/ip3/$domain.ico"
        )
    }
    
    /**
     * 下载Bitmap
     * 支持Google favicon服务、PNG、ICO等多种格式
     */
    private suspend fun downloadBitmap(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "📥 Downloading: $url")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                connection.setRequestProperty("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                connection.instanceFollowRedirects = true
                
                // 自动跟随重定向
                var actualConnection = connection
                var redirectCount = 0
                while (actualConnection.responseCode in arrayOf(
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        301, 302, 303, 307, 308
                    ) && redirectCount < 5
                ) {
                    val redirectUrl = actualConnection.getHeaderField("Location")
                    if (redirectUrl != null) {
                        actualConnection.disconnect()
                        actualConnection = URL(redirectUrl).openConnection() as HttpURLConnection
                        actualConnection.connectTimeout = 8000
                        actualConnection.readTimeout = 8000
                        actualConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                        actualConnection.setRequestProperty("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        redirectCount++
                    } else {
                        break
                    }
                }
                
                if (actualConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream: InputStream = actualConnection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    actualConnection.disconnect()
                    
                    // 验证bitmap是否有效
                    if (bitmap != null && !bitmap.isRecycled) {
                        Log.d(TAG, "✅ Bitmap decoded: ${bitmap.width}x${bitmap.height} from $url")
                        bitmap
                    } else {
                        Log.e(TAG, "❌ Invalid bitmap decoded from $url")
                        actualConnection.disconnect()
                        null
                    }
                } else {
                    Log.d(TAG, "❌ HTTP ${actualConnection.responseCode} from $url")
                    actualConnection.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download bitmap from $url: ${e.message}")
                null
            }
        }
    }
    
    /**
     * 从URL提取域名
     */
    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract domain from $url: ${e.message}")
            url
        }
    }
    
    /**
     * 加载AI引擎图标
     * 使用AI引擎名称生成对应的favicon URL
     */
    fun loadAIEngineIcon(imageView: ImageView, engineName: String, defaultIconRes: Int) {
        val cacheKey = "ai_engine_${engineName.lowercase()}"
        
        Log.d(TAG, "🔍 Loading AI engine icon for: $engineName, cacheKey: $cacheKey")

        // 检查内存缓存
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            Log.d(TAG, "✅ Found cached bitmap for $engineName")
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 设置默认图标
        imageView.setImageResource(defaultIconRes)
        imageView.tag = cacheKey
        
        Log.d(TAG, "🌐 Starting async load for $engineName")
        
        // 异步加载AI引擎图标
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadAIEngineIconFromUrl(engineName)
                if (bitmap != null) {
                    Log.d(TAG, "✅ Successfully loaded bitmap for $engineName (${bitmap.width}x${bitmap.height})")
                    memoryCache.put(cacheKey, bitmap)
                    
                    withContext(Dispatchers.Main) {
                        if (imageView.tag == cacheKey) {
                            Log.d(TAG, "🎨 Applying bitmap to ImageView for $engineName")
                            imageView.setImageBitmap(bitmap)
                        } else {
                            Log.w(TAG, "⚠️ Tag mismatch for $engineName: expected $cacheKey, got ${imageView.tag}")
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Failed to load bitmap for $engineName: returned null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception loading AI engine icon for $engineName", e)
            }
        }
    }
    
    /**
     * 从URL加载AI引擎图标
     */
    private suspend fun loadAIEngineIconFromUrl(engineName: String): Bitmap? {
        val urls = generateAIEngineIconUrls(engineName)
        
        Log.d(TAG, "🔍 Loading AI engine icon for: $engineName, trying ${urls.size} URLs")
        
        for (url in urls) {
            try {
                Log.d(TAG, "  Trying: $url")
                val bitmap = downloadBitmap(url)
                if (bitmap != null) {
                    Log.d(TAG, "✅ Successfully loaded AI engine icon from: $url")
                    return bitmap
                }
            } catch (e: Exception) {
                Log.d(TAG, "  ❌ Failed: $url - ${e.message}")
                continue
            }
        }
        
        Log.e(TAG, "❌ All URLs failed for AI engine: $engineName")
        return null
    }
    
    /**
     * 生成AI引擎图标URL列表
     * 优先使用Google的favicon服务
     */
    private fun generateAIEngineIconUrls(engineName: String): List<String> {
        return when {
            engineName.contains("ChatGPT") || engineName.contains("OpenAI") -> listOf(
                "https://www.google.com/s2/favicons?domain=chat.openai.com&sz=64",
                "https://www.google.com/s2/favicons?domain=openai.com&sz=64",
                "https://chat.openai.com/apple-touch-icon.png",
                "https://chat.openai.com/favicon.ico",
                "https://openai.com/favicon.ico"
            )
            engineName.contains("Claude") || engineName.contains("Anthropic") -> listOf(
                "https://www.google.com/s2/favicons?domain=claude.ai&sz=64",
                "https://www.google.com/s2/favicons?domain=anthropic.com&sz=64",
                "https://claude.ai/apple-touch-icon.png",
                "https://claude.ai/favicon.ico"
            )
            engineName.contains("Gemini") || engineName.contains("Google") -> listOf(
                "https://www.google.com/s2/favicons?domain=gemini.google.com&sz=64",
                "https://www.gstatic.com/lamda/images/favicon_v1_150160cddff7f294ce30.svg",
                "https://gemini.google.com/favicon.ico"
            )
            engineName.contains("文心一言") || engineName.contains("百度") -> listOf(
                "https://www.google.com/s2/favicons?domain=yiyan.baidu.com&sz=64",
                "https://nlp-eb.cdn.bcebos.com/logo/favicon.ico",
                "https://yiyan.baidu.com/favicon.ico"
            )
            engineName.contains("ChatGLM") -> listOf(
                "https://www.google.com/s2/favicons?domain=chatglm.cn&sz=64",
                "https://chatglm.cn/favicon.ico",
                "https://chatglm.cn/static/favicon.ico"
            )
            engineName.contains("通义千问") || engineName.contains("阿里") -> listOf(
                "https://www.google.com/s2/favicons?domain=tongyi.aliyun.com&sz=64",
                "https://img.alicdn.com/imgextra/i1/O1CN01OzQd341jtBJJmKuEF_!!6000000004614-2-tps-144-144.png",
                "https://tongyi.aliyun.com/favicon.ico"
            )
            engineName.contains("讯飞星火") || engineName.contains("星火") -> listOf(
                "https://xinghuo.xfyun.cn/favicon-32x32.ico",
                "https://xinghuo.xfyun.cn/favicon.ico"
            )
            engineName.contains("DeepSeek") -> listOf(
                "https://www.google.com/s2/favicons?domain=chat.deepseek.com&sz=64",
                "https://chat.deepseek.com/apple-touch-icon.png",
                "https://chat.deepseek.com/favicon.ico"
            )
            engineName.contains("Kimi") || engineName.contains("月之暗面") -> listOf(
                "https://www.google.com/s2/favicons?domain=kimi.moonshot.cn&sz=64",
                "https://www.moonshot.cn/apple-touch-icon.png",
                "https://kimi.moonshot.cn/favicon.ico"
            )
            engineName.contains("智谱") || engineName.contains("zhipu") || engineName.contains("glm") || engineName.contains("GLM") -> listOf(
                "https://www.google.com/s2/favicons?domain=open.bigmodel.cn&sz=64",
                "https://open.bigmodel.cn/favicon.ico",
                "https://chatglm.cn/favicon.ico"
            )
            engineName.contains("MiniMax") || engineName.contains("minimax") -> listOf(
                "https://www.minimax.chat/favicon.ico",
                "https://api.minimax.chat/favicon.ico"
            )
            engineName.contains("百川") || engineName.contains("baichuan") -> listOf(
                "https://www.baichuan-ai.com/favicon.ico"
            )
            engineName.contains("豆包") -> listOf(
                "https://sf3-cdn-tos.douyinstatic.com/obj/eden-cn/uhbfnupkbps/doubao_favicon.ico",
                "https://www.doubao.com/favicon.ico"
            )
            engineName.contains("混元") || engineName.contains("腾讯") -> listOf(
                "https://hunyuan.tencent.com/favicon.ico",
                "https://hunyuan.tencent.com/favicon.png"
            )
            engineName.contains("Meta") -> listOf(
                "https://meta-ai.com/favicon.ico",
                "https://metaai.com/favicon.ico"
            )
            engineName.contains("Poe") -> listOf(
                "https://poe.com/favicon.ico",
                "https://poe.com/apple-touch-icon.png"
            )
            engineName.contains("Perplexity") -> listOf(
                "https://www.perplexity.ai/apple-touch-icon.png",
                "https://www.perplexity.ai/favicon.ico"
            )
            engineName.contains("天工") -> listOf(
                "https://tiangong.kunlun.com/favicon.ico",
                "https://tiangong.kunlun.com/static/favicon.ico"
            )
            engineName.contains("Grok") -> listOf(
                "https://grok.x.ai/favicon.ico",
                "https://x.ai/favicon.ico"
            )
            engineName.contains("小度") -> listOf(
                "https://xiaoyi.baidu.com/favicon.ico",
                "https://xiaoyi.baidu.com/static/favicon.ico"
            )
            engineName.contains("Monica") -> listOf(
                "https://monica.im/favicon.ico",
                "https://monica.im/static/favicon.ico"
            )
            engineName.contains("You.com") -> listOf(
                "https://you.com/favicon.ico",
                "https://you.com/apple-touch-icon.png"
            )
            engineName.contains("Pi") -> listOf(
                "https://pi.ai/favicon.ico",
                "https://pi.ai/apple-touch-icon.png"
            )
            engineName.contains("Character") -> listOf(
                "https://characterai.io/static/favicon.ico",
                "https://character.ai/favicon.ico"
            )
            engineName.contains("Coze") -> listOf(
                "https://www.coze.com/favicon.ico",
                "https://coze.com/favicon.ico"
            )
            engineName.contains("Copilot") -> listOf(
                "https://www.bing.com/sa/simg/favicon-copilot-chat.ico",
                "https://copilot.microsoft.com/favicon.ico"
            )
            else -> listOf(
                "https://www.google.com/s2/favicons?domain=openai.com&sz=64",
                "https://www.google.com/s2/favicons?domain=anthropic.com&sz=64"
            )
        }
    }
    
    /**
     * 预加载常用网站的favicon
     */
    fun preloadCommonFavicons() {
        CoroutineScope(Dispatchers.IO).launch {
            val commonDomains = listOf(
                "weixin.sogou.com",
                "s.taobao.com", 
                "search.jd.com",
                "www.zhihu.com",
                "mobile.yangkeduo.com",
                "list.tmall.com",
                "www.google.com"
            )
            
            commonDomains.forEach { domain ->
                try {
                    val bitmap = loadFaviconFromUrl(domain)
                    if (bitmap != null) {
                        memoryCache.put("favicon_$domain", bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to preload favicon for $domain: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
        Log.d(TAG, "Favicon cache cleared")
    }
} 