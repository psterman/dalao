package com.example.aifloatingball.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object FaviconLoader {

    private val memoryCache: LruCache<String, Bitmap>
    private val executor = Executors.newFixedThreadPool(5)
    private val uiHandler = Handler(Looper.getMainLooper())

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // Use 1/8th of the available memory for this cache.
        val cacheSize = maxMemory / 8
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    /**
     * AI引擎专用图标URL映射
     */
    private val aiEngineIconUrls = mapOf(
        "chatgpt" to listOf(
            "https://chat.openai.com/apple-touch-icon.png",
            "https://chat.openai.com/favicon.ico",
            "https://openai.com/favicon.ico"
        ),
        "claude" to listOf(
            "https://claude.ai/apple-touch-icon.png",
            "https://claude.ai/favicon.ico"
        ),
        "gemini" to listOf(
            "https://www.gstatic.com/lamda/images/favicon_v1_150160cddff7f294ce30.svg",
            "https://gemini.google.com/favicon.ico"
        ),
        "文心一言" to listOf(
            "https://nlp-eb.cdn.bcebos.com/logo/favicon.ico",
            "https://yiyan.baidu.com/favicon.ico"
        ),
        "智谱清言" to listOf(
            "https://chatglm.cn/favicon.ico",
            "https://chatglm.cn/static/favicon.ico"
        ),
        "通义千问" to listOf(
            "https://img.alicdn.com/imgextra/i1/O1CN01OzQd341jtBJJmKuEF_!!6000000004614-2-tps-144-144.png",
            "https://qianwen.aliyun.com/favicon.ico"
        ),
        "讯飞星火" to listOf(
            "https://xinghuo.xfyun.cn/favicon-32x32.ico",
            "https://xinghuo.xfyun.cn/favicon.ico"
        ),
        "perplexity" to listOf(
            "https://www.perplexity.ai/apple-touch-icon.png",
            "https://www.perplexity.ai/favicon.ico"
        ),
        "phind" to listOf(
            "https://phind.com/favicon.ico",
            "https://phind.com/apple-touch-icon.png"
        ),
        "poe" to listOf(
            "https://poe.com/favicon.ico",
            "https://poe.com/apple-touch-icon.png"
        ),
        "天工ai" to listOf(
            "https://www.tiangong.cn/favicon.ico",
            "https://tiangong.kunlun.com/favicon.ico"
        ),
        "秘塔ai搜索" to listOf(
            "https://metaso.cn/favicon.ico",
            "https://metaso.cn/apple-touch-icon.png"
        ),
        "夸克ai" to listOf(
            "https://www.quark.cn/favicon.ico",
            "https://quark.sm.cn/favicon.ico"
        ),
        "360ai搜索" to listOf(
            "https://sou.ai.360.cn/favicon.ico",
            "https://www.so.com/favicon.ico"
        ),
        "百度ai" to listOf(
            "https://www.baidu.com/favicon.ico",
            "https://www.baidu.com/img/baidu_85beaf5496f291521eb75ba38eacbd87.svg"
        ),
        "you.com" to listOf(
            "https://you.com/favicon.ico",
            "https://you.com/apple-touch-icon.png"
        ),
        "brave search" to listOf(
            "https://search.brave.com/favicon.ico",
            "https://brave.com/favicon.ico"
        ),
        "wolframalpha" to listOf(
            "https://www.wolframalpha.com/favicon.ico",
            "https://www.wolframalpha.com/apple-touch-icon.png"
        ),
        "kimi" to listOf(
            "https://www.moonshot.cn/apple-touch-icon.png",
            "https://kimi.moonshot.cn/favicon.ico"
        ),
        "deepseek (web)" to listOf(
            "https://chat.deepseek.com/apple-touch-icon.png",
            "https://chat.deepseek.com/favicon.ico"
        ),
        "万知" to listOf(
            "https://www.wanzhi.com/favicon.ico",
            "https://www.wanzhi.com/apple-touch-icon.png"
        ),
        "百小应" to listOf(
            "https://ying.baidu.com/favicon.ico",
            "https://www.baidu.com/favicon.ico"
        ),
        "跃问" to listOf(
            "https://www.stepfun.com/favicon.ico",
            "https://www.stepfun.com/apple-touch-icon.png"
        ),
        "豆包" to listOf(
            "https://sf3-cdn-tos.douyinstatic.com/obj/eden-cn/uhbfnupkbps/doubao_favicon.ico",
            "https://www.doubao.com/favicon.ico"
        ),
        "cici" to listOf(
            "https://ciciai.com/favicon.ico",
            "https://ciciai.com/apple-touch-icon.png"
        ),
        "海螺" to listOf(
            "https://hailuoyun.com.cn/favicon.ico",
            "https://hailuoyun.com.cn/apple-touch-icon.png"
        ),
        "groq" to listOf(
            "https://groq.com/favicon.ico",
            "https://groq.com/apple-touch-icon.png"
        ),
        "腾讯元宝" to listOf(
            "https://yuanbao.tencent.com/favicon.ico",
            "https://yuanbao.tencent.com/apple-touch-icon.png"
        ),
        "商量" to listOf(
            "https://shangliang.sensetime.com/favicon.ico",
            "https://shangliang.sensetime.com/apple-touch-icon.png"
        ),
        "devv" to listOf(
            "https://devv.ai/favicon.ico",
            "https://devv.ai/apple-touch-icon.png"
        ),
        "huggingchat" to listOf(
            "https://huggingface.co/favicon.ico",
            "https://huggingface.co/apple-touch-icon.png"
        ),
        "纳米ai搜索" to listOf(
            "https://www.nainami.com/favicon.ico",
            "https://www.nainami.com/apple-touch-icon.png"
        ),
        "thinkany" to listOf(
            "https://www.thinkany.ai/favicon.ico",
            "https://www.thinkany.ai/apple-touch-icon.png"
        ),
        "hika" to listOf(
            "https://web.hika.app/favicon.ico",
            "https://web.hika.app/apple-touch-icon.png"
        ),
        "genspark" to listOf(
            "https://www.genspark.com/favicon.ico",
            "https://www.genspark.com/apple-touch-icon.png"
        ),
        "grok" to listOf(
            "https://grok.x.ai/favicon.ico",
            "https://grok.x.ai/apple-touch-icon.png"
        ),
        "flowith" to listOf(
            "https://flowith.me/favicon.ico",
            "https://flowith.me/apple-touch-icon.png"
        ),
        "notebooklm" to listOf(
            "https://notebooklm.google.com/favicon.ico",
            "https://notebooklm.google.com/apple-touch-icon.png"
        ),
        "coze" to listOf(
            "https://www.coze.com/favicon.ico",
            "https://www.coze.com/apple-touch-icon.png"
        ),
        "dify" to listOf(
            "https://dify.ai/favicon.ico",
            "https://dify.ai/apple-touch-icon.png"
        ),
        "wps灵感" to listOf(
            "https://ai.wps.cn/favicon.ico",
            "https://ai.wps.cn/apple-touch-icon.png"
        ),
        "lechat" to listOf(
            "https://lechat.minmax.com.cn/favicon.ico",
            "https://lechat.minmax.com.cn/apple-touch-icon.png"
        ),
        "monica" to listOf(
            "https://monica.im/favicon.ico",
            "https://monica.im/apple-touch-icon.png"
        ),
        "知乎" to listOf(
            "https://static.zhihu.com/heifetz/favicon.ico",
            "https://www.zhihu.com/favicon.ico"
        )
    )

    private fun getFaviconServiceUrl(engineUrl: String): String? {
        return try {
            val host = URL(engineUrl).host
            // Using DuckDuckGo's favicon service for reliability and privacy.
            "https://icons.duckduckgo.com/ip3/$host.ico"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取AI引擎专用图标URL列表
     */
    private fun getAIEngineIconUrls(engineName: String): List<String> {
        val normalizedName = engineName.lowercase()
        return aiEngineIconUrls[normalizedName] ?: aiEngineIconUrls[engineName] ?: emptyList()
    }

    /**
     * 为AI引擎加载图标
     */
    fun loadAIEngineIcon(imageView: ImageView, engineName: String, fallbackResId: Int) {
        val iconUrls = getAIEngineIconUrls(engineName)
        
        if (iconUrls.isEmpty()) {
            // 如果没有专用图标URL，使用默认方式
            imageView.setImageResource(fallbackResId)
            return
        }
        
        val cacheKey = "ai_engine_$engineName"
        imageView.tag = cacheKey

        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // Set fallback icon while loading
        imageView.setImageResource(fallbackResId)

        executor.execute {
            var bitmap: Bitmap? = null
            
            // 尝试多个图标URL
            for (iconUrl in iconUrls) {
                try {
                    val url = URL(iconUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connection.connect()

                    if (connection.responseCode == 200) {
                        val inputStream = connection.inputStream
                        bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()

                        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.d("FaviconLoader", "Failed to load AI engine icon from $iconUrl: ${e.message}")
                    continue
                }
            }

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                uiHandler.post {
                    if (imageView.tag == cacheKey) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } else {
                Log.e("FaviconLoader", "Failed to load AI engine icon for $engineName")
            }
        }
    }

    fun loadIcon(imageView: ImageView, url: String, fallbackResId: Int) {
        // 首先检查是否是AI引擎
        val engineName = getEngineNameFromUrl(url)
        if (engineName != null) {
            loadAIEngineIcon(imageView, engineName, fallbackResId)
            return
        }
        
        // 生成缓存键
        val cacheKey = "favicon_$url"
        imageView.tag = cacheKey

        // 检查内存缓存
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 设置默认图标
        imageView.setImageResource(fallbackResId)

        // 获取favicon URL
        val faviconUrl = getFaviconServiceUrl(url)
        if (faviconUrl == null) {
            Log.e("FaviconLoader", "Invalid URL: $url")
            return
        }

        // 异步加载图标
        executor.execute {
            try {
                val bitmap = loadBitmapFromUrl(faviconUrl)
                    if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                        uiHandler.post {
                        if (imageView.tag == cacheKey) {
                                imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FaviconLoader", "Error loading favicon for $url", e)
            }
        }
    }

    private fun getEngineNameFromUrl(url: String): String? {
        try {
            val host = URL(url).host.toLowerCase()
            return when {
                host.contains("chat.openai.com") -> "ChatGPT"
                host.contains("claude.ai") -> "Claude"
                host.contains("gemini.google.com") -> "Gemini"
                host.contains("bard.google.com") -> "Bard"
                host.contains("yiyan.baidu.com") -> "文心一言"
                host.contains("chatglm.cn") -> "智谱清言"
                host.contains("qianwen.aliyun.com") -> "通义千问"
                host.contains("xinghuo.xfyun.cn") -> "讯飞星火"
                host.contains("deepseek.com") -> "DeepSeek"
                host.contains("kimi.moonshot.cn") -> "Kimi"
                host.contains("doubao.com") -> "豆包"
                host.contains("perplexity.ai") -> "Perplexity"
                host.contains("phind.com") -> "Phind"
                host.contains("poe.com") -> "Poe"
                host.contains("tiangong.cn") -> "天工AI"
                host.contains("metaso.cn") -> "秘塔AI搜索"
                host.contains("quark.cn") -> "夸克AI"
                host.contains("sou.ai.360.cn") -> "360AI搜索"
                host.contains("you.com") -> "You.com"
                host.contains("search.brave.com") -> "Brave搜索"
                host.contains("wolframalpha.com") -> "WolframAlpha"
                host.contains("grok.x.ai") -> "Grok"
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun loadBitmapFromUrl(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode == 200) {
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FaviconLoader", "Failed to load bitmap from $urlString", e)
            null
        }
    }
} 