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
     */
    fun loadIcon(imageView: ImageView, url: String, defaultIconRes: Int) {
        loadFavicon(imageView, url)
        // 如果favicon加载失败，会保持默认图标
        imageView.setImageResource(defaultIconRes)
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
        
        for (url in urls) {
            try {
                val bitmap = downloadBitmap(url)
            if (bitmap != null) {
                    return bitmap
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load favicon from $url: ${e.message}")
                continue
            }
        }
        
        return null
    }
    
    /**
     * 生成favicon URL列表
     */
    private fun generateFaviconUrls(domain: String): List<String> {
        return listOf(
            "https://$domain/favicon.ico",
            "https://$domain/favicon.png",
            "https://$domain/apple-touch-icon.png",
            "https://www.google.com/s2/favicons?domain=$domain&sz=32",
            "https://www.google.com/s2/favicons?domain=$domain&sz=64"
        )
    }
    
    /**
     * 下载Bitmap
     */
    private suspend fun downloadBitmap(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream: InputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                    connection.disconnect()
                bitmap
            } else {
                    connection.disconnect()
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
     */
    fun loadAIEngineIcon(imageView: ImageView, engineName: String, defaultIconRes: Int) {
        val cacheKey = "ai_engine_$engineName"

        // 检查内存缓存
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 设置默认图标
        imageView.setImageResource(defaultIconRes)
        
        // 异步加载AI引擎图标
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadAIEngineIconFromUrl(engineName)
                    if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    
                    withContext(Dispatchers.Main) {
                        if (imageView.tag == cacheKey) {
                                imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load AI engine icon for $engineName: ${e.message}")
            }
        }
    }
    
    /**
     * 从URL加载AI引擎图标
     */
    private suspend fun loadAIEngineIconFromUrl(engineName: String): Bitmap? {
        val urls = generateAIEngineIconUrls(engineName)
        
        for (url in urls) {
            try {
                val bitmap = downloadBitmap(url)
                if (bitmap != null) {
                    return bitmap
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to load AI engine icon from $url: ${e.message}")
                continue
            }
        }
        
            return null
    }
    
    /**
     * 生成AI引擎图标URL列表
     */
    private fun generateAIEngineIconUrls(engineName: String): List<String> {
        return when {
            engineName.contains("ChatGPT") || engineName.contains("OpenAI") -> listOf(
                "https://chat.openai.com/apple-touch-icon.png",
                "https://chat.openai.com/favicon.ico",
                "https://openai.com/favicon.ico"
            )
            engineName.contains("Claude") || engineName.contains("Anthropic") -> listOf(
                "https://claude.ai/apple-touch-icon.png",
                "https://claude.ai/favicon.ico"
            )
            engineName.contains("Gemini") || engineName.contains("Google") -> listOf(
                "https://www.gstatic.com/lamda/images/favicon_v1_150160cddff7f294ce30.svg",
                "https://gemini.google.com/favicon.ico"
            )
            engineName.contains("文心一言") || engineName.contains("百度") -> listOf(
                "https://nlp-eb.cdn.bcebos.com/logo/favicon.ico",
                "https://yiyan.baidu.com/favicon.ico"
            )
            engineName.contains("ChatGLM") -> listOf(
                "https://chatglm.cn/favicon.ico",
                "https://chatglm.cn/static/favicon.ico"
            )
            engineName.contains("通义千问") || engineName.contains("阿里") -> listOf(
                "https://img.alicdn.com/imgextra/i1/O1CN01OzQd341jtBJJmKuEF_!!6000000004614-2-tps-144-144.png",
                "https://tongyi.aliyun.com/favicon.ico"
            )
            engineName.contains("讯飞星火") || engineName.contains("星火") -> listOf(
                "https://xinghuo.xfyun.cn/favicon-32x32.ico",
                "https://xinghuo.xfyun.cn/favicon.ico"
            )
            engineName.contains("DeepSeek") -> listOf(
                "https://chat.deepseek.com/apple-touch-icon.png",
                "https://chat.deepseek.com/favicon.ico"
            )
            engineName.contains("Kimi") || engineName.contains("月之暗面") -> listOf(
                "https://www.moonshot.cn/apple-touch-icon.png",
                "https://kimi.moonshot.cn/favicon.ico"
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