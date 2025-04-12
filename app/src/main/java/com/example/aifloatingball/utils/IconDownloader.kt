package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class IconDownloader(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val iconDir = File(context.filesDir, "icons")
    
    // 搜索引擎图标映射
    private val searchEngineIcons = mapOf(
        // 普通搜索引擎
        "baidu" to listOf(
            "https://www.baidu.com/favicon.ico",
            "https://www.baidu.com/img/baidu_85beaf5496f291521eb75ba38eacbd87.svg"
        ),
        "google" to listOf(
            "https://www.google.com/favicon.ico",
            "https://www.google.com/images/branding/googleg/1x/googleg_standard_color_128dp.png"
        ),
        "bing" to listOf(
            "https://www.bing.com/favicon.ico",
            "https://www.bing.com/sa/simg/favicon-2x.ico"
        ),
        "sogou" to listOf(
            "https://www.sogou.com/images/favicon/favicon.ico",
            "https://dlweb.sogoucdn.com/logo/favicon.ico"
        ),
        "360" to listOf(
            "https://www.so.com/favicon.ico",
            "https://p.ssl.qhimg.com/t01ed238fa5a1ff2cf2.png"
        ),
        "toutiao" to listOf(
            "https://so.toutiao.com/favicon.ico",
            "https://sf3-cdn-tos.douyinstatic.com/obj/eden-cn/uhbfnupkbps/toutiao_favicon.ico"
        ),
        "quark" to listOf(
            "https://quark.sm.cn/favicon.ico",
            "https://p.ssl.qhimg.com/t01ed238fa5a1ff2cf2.png"
        ),
        "sm" to listOf(
            "https://m.sm.cn/favicon.ico",
            "https://p.ssl.qhimg.com/t01ed238fa5a1ff2cf2.png"
        ),
        "yandex" to listOf(
            "https://yandex.com/favicon.ico",
            "https://yastatic.net/s3/home-static/_/7c/7ccf0ae7226c63127e31fd1323c408ef.png"
        ),
        "duckduckgo" to listOf(
            "https://duckduckgo.com/favicon.ico",
            "https://duckduckgo.com/assets/icons/meta/DDG-iOS-icon_152x152.png"
        ),
        "yahoo" to listOf(
            "https://www.yahoo.com/favicon.ico",
            "https://s.yimg.com/cv/apiv2/default/icons/favicon_y19_32x32.ico"
        ),
        "ecosia" to listOf(
            "https://www.ecosia.org/favicon.ico",
            "https://cdn-static.ecosia.org/static/icons/favicon.ico"
        ),

        // AI搜索引擎
        "chatgpt" to listOf(
            "https://chat.openai.com/favicon.ico",
            "https://chat.openai.com/apple-touch-icon.png"
        ),
        "claude" to listOf(
            "https://claude.ai/favicon.ico",
            "https://claude.ai/apple-touch-icon.png"
        ),
        "wenxin" to listOf(
            "https://yiyan.baidu.com/favicon.ico",
            "https://nlp-eb.cdn.bcebos.com/logo/favicon.ico"
        ),
        "qianwen" to listOf(
            "https://tongyi.aliyun.com/qianwen/favicon.ico",
            "https://tongyi.aliyun.com/favicon.ico",
            "https://img.alicdn.com/tfs/TB1_ZXuvcHqK1RjSZFkXXX.WFXa-16-16.ico"
        ),
        "xinghuo" to listOf(
            "https://xinghuo.xfyun.cn/favicon.ico",
            "https://xinghuo.xfyun.cn/static/media/favicon.ico"
        ),
        "gemini" to listOf(
            "https://gemini.google.com/favicon.ico",
            "https://www.gstatic.com/lamda/images/favicon_v1_150160cddff7f294ce30.svg"
        ),
        "deepseek" to listOf(
            "https://chat.deepseek.com/favicon.ico",
            "https://assets.deepseek.com/images/favicon.ico",
            "https://chat.deepseek.com/apple-touch-icon.png"
        ),
        "chatglm" to listOf(
            "https://chatglm.cn/favicon.ico",
            "https://zhipu-ai-web.oss-cn-beijing.aliyuncs.com/favicon.ico"
        ),
        "kimi" to listOf(
            "https://kimi.moonshot.cn/favicon.ico",
            "https://moonshot.cn/favicon.ico",
            "https://moonshot.cn/apple-touch-icon.png"
        ),
        "xiaodong" to listOf(
            "https://xiaodong.baidu.com/favicon.ico",
            "https://www.baidu.com/favicon.ico"
        ),
        "doubao" to listOf(
            "https://doubao.com/favicon.ico",
            "https://doubao.com/apple-touch-icon.png",
            "https://sf3-cdn-tos.douyinstatic.com/obj/eden-cn/uhbfnupkbps/doubao_favicon.ico"
        ),
        "hunyuan" to listOf(
            "https://hunyuan.tencent.com/favicon.ico",
            "https://www.tencent.com/favicon.ico",
            "https://hunyuan.tencent.com/apple-touch-icon.png"
        ),
        "meta-ai" to listOf(
            "https://meta-ai.com/favicon.ico",
            "https://meta-ai.com/apple-touch-icon.png"
        ),
        "pi" to listOf(
            "https://pi.ai/favicon.ico",
            "https://pi.ai/apple-touch-icon.png"
        ),
        "character" to listOf(
            "https://character.ai/favicon.ico",
            "https://character.ai/apple-touch-icon.png",
            "https://characterai.io/static/favicon.ico"
        ),
        "poe" to listOf(
            "https://poe.com/favicon.ico",
            "https://poe.com/apple-touch-icon.png"
        ),
        "perplexity" to listOf(
            "https://www.perplexity.ai/favicon.ico",
            "https://www.perplexity.ai/apple-touch-icon.png"
        ),
        "tiangong" to listOf(
            "https://tiangong.kunlun.com/favicon.ico",
            "https://tiangong.kunlun.com/apple-touch-icon.png"
        ),
        "grok" to listOf(
            "https://grok.x.ai/favicon.ico",
            "https://grok.x.ai/apple-touch-icon.png",
            "https://x.ai/favicon.ico"
        ),
        "xiaoyi" to listOf(
            "https://xiaoyi.baidu.com/favicon.ico",
            "https://www.baidu.com/favicon.ico"
        ),
        "monica" to listOf(
            "https://monica.im/favicon.ico",
            "https://monica.im/apple-touch-icon.png"
        ),
        "you" to listOf(
            "https://you.com/favicon.ico",
            "https://you.com/apple-touch-icon.png"
        ),
        "nano" to listOf(
            "https://nanoai.com/favicon.ico",
            "https://nanoai.com/apple-touch-icon.png"
        ),
        "copilot" to listOf(
            "https://copilot.microsoft.com/favicon.ico",
            "https://www.microsoft.com/favicon.ico"
        ),
        "anthropic" to listOf(
            "https://www.anthropic.com/favicon.ico",
            "https://www.anthropic.com/apple-touch-icon.png"
        )
    )

    // 备用图标服务
    private val backupIconServices = listOf(
        // 国内图标服务
        { domain: String -> "https://api.iowen.cn/favicon/$domain.png" },
        { domain: String -> "https://favicon.cccyun.cc/$domain" },
        { domain: String -> "https://favicon.rss.ink/v1/$domain" },
        { domain: String -> "https://api.xinac.net/icon/?url=$domain" },
        
        // 国外图标服务
        { domain: String -> "https://www.google.com/s2/favicons?sz=64&domain=$domain" },
        { domain: String -> "https://icon.horse/icon/$domain" },
        { domain: String -> "https://favicone.com/$domain" }
    )

    init {
        if (!iconDir.exists()) {
            iconDir.mkdirs()
        }
    }

    fun downloadAllIcons() {
        scope.launch {
            searchEngineIcons.forEach { (name, urls) ->
                try {
                    // 尝试从主要URL下载
                    var downloaded = false
                    for (url in urls) {
                        if (downloadIcon(name, url)) {
                            downloaded = true
                            break
                        }
                    }

                    // 如果主要URL都失败了，尝试备用服务
                    if (!downloaded) {
                        for (service in backupIconServices) {
                            val backupUrl = service(name)
                            if (downloadIcon(name, backupUrl)) {
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("IconDownloader", "Failed to download icon for $name", e)
                }
            }
        }
    }

    private suspend fun downloadIcon(name: String, url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                if (bitmap != null) {
                    val file = File(iconDir, "${name}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("IconDownloader", "Failed to download icon from $url", e)
                false
            }
        }
    }

    fun getLocalIconPath(name: String): String? {
        val file = File(iconDir, "${name}.png")
        return if (file.exists()) file.absolutePath else null
    }

    fun clearCache() {
        iconDir.listFiles()?.forEach { it.delete() }
    }
} 