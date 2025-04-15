package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import com.example.aifloatingball.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class IconLoader(private val context: Context) {
    private val iconCache = mutableMapOf<String, Bitmap>()
    private val iconCacheDir by lazy { File(context.cacheDir, "search_icons") }
    private val executor = Executors.newFixedThreadPool(3) // 使用线程池优化并发加载
    
    fun loadIcon(url: String, iconView: ImageView, defaultIconRes: Int) {
        try {
            val domain = extractDomain(url)
        val iconFile = File(iconCacheDir, "${domain.replace(".", "_")}.png")
        
        // 设置默认图标
        iconView.setImageResource(defaultIconRes)
        
        // 检查内存缓存
        iconCache[domain]?.let {
            iconView.setImageBitmap(it)
            return
        }
        
        // 检查文件缓存
        if (iconFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    iconCache[domain] = bitmap
                    iconView.setImageBitmap(bitmap)
                    return
                }
            } catch (e: Exception) {
                Log.e("IconLoader", "从缓存加载图标失败: ${e.message}")
            }
        }
        
        // 从网络加载
            executor.execute {
            try {
                // 尝试不同的 favicon URL
                    val iconUrls = getIconUrls(domain)
                var bitmap: Bitmap? = null
                
                for (iconUrl in iconUrls) {
                    try {
                        val connection = URL(iconUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        
                        if (connection.responseCode == 200) {
                            bitmap = BitmapFactory.decodeStream(connection.inputStream)
                                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                                    break
                                }
                        }
                    } catch (e: Exception) {
                            Log.d("IconLoader", "加载 $iconUrl 失败: ${e.message}")
                        continue
                    }
                }
                
                bitmap?.let {
                    // 缓存到内存
                    iconCache[domain] = it
                    
                    // 缓存到文件
                    iconCacheDir.mkdirs()
                    try {
                        FileOutputStream(iconFile).use { out ->
                            it.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (e: Exception) {
                        Log.e("IconLoader", "保存图标到文件失败: ${e.message}")
                    }
                    
                    // 在主线程更新UI
                    Handler(Looper.getMainLooper()).post {
                        iconView.setImageBitmap(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("IconLoader", "加载图标失败: ${e.message}")
            }
            }
        } catch (e: Exception) {
            Log.e("IconLoader", "图标加载异常: ${e.message}")
            // 出错时至少显示默认图标
            iconView.setImageResource(defaultIconRes)
        }
    }

    private fun extractDomain(urlString: String): String {
        return try {
            val uri = Uri.parse(urlString)
            uri.host ?: urlString.replace("https://", "").replace("http://", "").split("/")[0]
        } catch (e: Exception) {
            urlString.replace("https://", "").replace("http://", "").split("/")[0]
        }
    }

    private fun getIconUrls(domain: String): List<String> {
        val baseUrls = listOf(
            "https://$domain",
            "https://www.$domain"
        )

        val paths = listOf(
            "/favicon.ico",
            "/favicon.png",
            "/apple-touch-icon.png",
            "/apple-touch-icon-precomposed.png",
            "/touch-icon.png",
            "/assets/favicon.ico",
            "/static/favicon.ico",
            "/static/images/favicon.ico",
            "/public/favicon.ico"
        )

        val urls = mutableListOf<String>()
        
        // 添加特定的图标URL - 使用精准匹配
        when {
            // AI搜索引擎 - 精准匹配
            domain == "chat.openai.com" || domain.contains("openai.com") -> {
                urls.add("https://chat.openai.com/apple-touch-icon.png")
                urls.add("https://openai.com/favicon.ico")
            }
            domain == "claude.ai" || domain.contains("anthropic.com") -> {
                urls.add("https://claude.ai/apple-touch-icon.png")
                urls.add("https://claude.ai/favicon.ico")
            }
            domain == "gemini.google.com" -> {
                urls.add("https://www.gstatic.com/lamda/images/favicon_v1_150160cddff7f294ce30.svg")
                urls.add("https://gemini.google.com/favicon.ico")
            }
            domain == "yiyan.baidu.com" -> {
                urls.add("https://nlp-eb.cdn.bcebos.com/logo/favicon.ico")
                urls.add("https://yiyan.baidu.com/favicon.ico")
            }
            domain == "chatglm.cn" -> {
                urls.add("https://chatglm.cn/favicon.ico")
                urls.add("https://chatglm.cn/static/favicon.ico")
            }
            domain.contains("tongyi.aliyun.com") -> {
                urls.add("https://img.alicdn.com/imgextra/i1/O1CN01OzQd341jtBJJmKuEF_!!6000000004614-2-tps-144-144.png")
                urls.add("https://tongyi.aliyun.com/favicon.ico")
            }
            domain.contains("xinghuo.xfyun.cn") || domain.contains("xfyun.cn") -> {
                urls.add("https://xinghuo.xfyun.cn/favicon-32x32.ico")
                urls.add("https://xinghuo.xfyun.cn/favicon.ico")
            }
            domain == "chat.deepseek.com" || domain.contains("deepseek.com") -> {
                urls.add("https://chat.deepseek.com/apple-touch-icon.png")
                urls.add("https://chat.deepseek.com/favicon.ico")
            }
            domain == "kimi.moonshot.cn" || domain.contains("moonshot.cn") -> {
                urls.add("https://www.moonshot.cn/apple-touch-icon.png")
                urls.add("https://kimi.moonshot.cn/favicon.ico")
            }
            domain == "xiaodong.baidu.com" -> {
                urls.add("https://xiaodong.baidu.com/favicon.ico")
                urls.add("https://dlbaikebcs.cdn.bcebos.com/cms/pc/xiaodongfav.ico")
            }
            domain.contains("doubao.com") -> {
                urls.add("https://sf3-cdn-tos.douyinstatic.com/obj/eden-cn/uhbfnupkbps/doubao_favicon.ico")
                urls.add("https://www.doubao.com/favicon.ico")
            }
            domain.contains("hunyuan.tencent.com") -> {
                urls.add("https://hunyuan.tencent.com/favicon.ico")
                urls.add("https://hunyuan.tencent.com/favicon.png")
            }
            domain.contains("meta-ai.com") || domain.contains("metaai.com") -> {
                urls.add("https://meta-ai.com/favicon.ico")
                urls.add("https://metaai.com/favicon.ico")
            }
            domain == "poe.com" -> {
                urls.add("https://poe.com/favicon.ico")
                urls.add("https://poe.com/apple-touch-icon.png")
            }
            domain.contains("perplexity.ai") -> {
                urls.add("https://www.perplexity.ai/apple-touch-icon.png")
                urls.add("https://www.perplexity.ai/favicon.ico")
            }
            domain.contains("tiangong.kunlun.com") -> {
                urls.add("https://tiangong.kunlun.com/favicon.ico")
                urls.add("https://tiangong.kunlun.com/static/favicon.ico")
            }
            domain.contains("grok.x.ai") || domain.contains("x.ai") -> {
                urls.add("https://grok.x.ai/favicon.ico")
                urls.add("https://x.ai/favicon.ico")
            }
            domain == "xiaoyi.baidu.com" -> {
                urls.add("https://xiaoyi.baidu.com/favicon.ico")
                urls.add("https://xiaoyi.baidu.com/static/favicon.ico")
            }
            domain == "monica.im" -> {
                urls.add("https://monica.im/favicon.ico")
                urls.add("https://monica.im/static/favicon.ico")
            }
            domain == "you.com" -> {
                urls.add("https://you.com/favicon.ico")
                urls.add("https://you.com/apple-touch-icon.png")
            }
            domain == "pi.ai" -> {
                urls.add("https://pi.ai/favicon.ico")
                urls.add("https://pi.ai/apple-touch-icon.png")
            }
            domain.contains("character.ai") -> {
                urls.add("https://characterai.io/static/favicon.ico")
                urls.add("https://character.ai/favicon.ico")
            }
            domain.contains("coze.com") -> {
                urls.add("https://www.coze.com/favicon.ico")
                urls.add("https://coze.com/favicon.ico")
            }
            domain.contains("copilot.microsoft.com") -> {
                urls.add("https://www.bing.com/sa/simg/favicon-copilot-chat.ico")
                urls.add("https://copilot.microsoft.com/favicon.ico")
            }
            domain.contains("scott-ai.cn") -> {
                urls.add("https://www.scott-ai.cn/favicon.ico") 
                urls.add("https://scott-ai.cn/favicon.png")
            }
            domain.contains("chat.shellgpt.com") -> {
                urls.add("https://chat.shellgpt.com/favicon.ico")
                urls.add("https://shellgpt.com/favicon.ico")
            }
            
            // 普通搜索引擎
            domain.contains("baidu.com") -> urls.add("https://www.baidu.com/img/baidu_85beaf5496f291521eb75ba38eacbd87.svg")
            domain.contains("google.com") -> urls.add("https://www.google.com/images/branding/googleg/1x/googleg_standard_color_128dp.png")
            domain.contains("bing.com") -> urls.add("https://www.bing.com/sa/simg/favicon-2x.ico")
            domain.contains("sogou.com") -> urls.add("https://www.sogou.com/images/logo/new/favicon.ico?v=4")
            domain.contains("so.com") -> urls.add("https://s.ssl.qhimg.com/static/121a1737750aa53d.ico")
            domain.contains("zhihu.com") -> urls.add("https://static.zhihu.com/heifetz/favicon.ico")
            domain.contains("weibo.com") -> urls.add("https://weibo.com/favicon.ico")
            domain.contains("douban.com") -> urls.add("https://img3.doubanio.com/favicon.ico")
            domain.contains("taobao.com") -> urls.add("https://www.taobao.com/favicon.ico")
            domain.contains("jd.com") -> urls.add("https://www.jd.com/favicon.ico")
            domain.contains("douyin.com") -> urls.add("https://s3.bytedance.com/site/assets/img/favicon.ico")
            domain.contains("xiaohongshu.com") -> urls.add("https://fe-static.xiaohongshu.com/xhs-pc-web/icons/favicon.ico")
            domain.contains("bilibili.com") -> urls.add("https://www.bilibili.com/favicon.ico")
        }

        // 添加常规图标路径
        baseUrls.forEach { baseUrl ->
            paths.forEach { path ->
                urls.add(baseUrl + path)
            }
        }

        // 添加Google Favicon服务作为备选
        urls.add("https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$domain&size=32")
        urls.add("https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$domain&size=64")
        urls.add("https://www.google.com/s2/favicons?domain=$domain&sz=64")
        
        return urls.distinct()
    }
    
    fun clearCache() {
        iconCache.clear()
        try {
            if (iconCacheDir.exists()) {
                iconCacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e("IconLoader", "清理缓存失败", e)
        }
    }
    
    fun cleanupOldCache() {
        try {
            if (iconCacheDir.exists()) {
                // 删除超过7天的缓存文件
                val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                iconCacheDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < sevenDaysAgo) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IconLoader", "清理旧缓存失败", e)
        }
    }
} 