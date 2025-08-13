package com.example.aifloatingball.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * iTunes API测试工具
 * 用于测试和验证iTunes Search API的图标获取效果
 */
class iTunesApiTester(private val context: Context) {
    
    companion object {
        private const val TAG = "iTunesApiTester"
    }
    
    /**
     * 测试热门应用的图标获取效果
     */
    suspend fun testPopularApps() {
        val testApps = listOf(
            TestApp("QQ音乐", "com.tencent.qqmusic"),
            TestApp("网易云音乐", "com.netease.cloudmusic"),
            TestApp("支付宝", "com.eg.android.AlipayGphone"),
            TestApp("微信", "com.tencent.mm"),
            TestApp("淘宝", "com.taobao.taobao"),
            TestApp("滴滴出行", "com.sdu.didi.psnger"),
            TestApp("饿了么", "me.ele"),
            TestApp("豆瓣", "com.douban.frodo"),
            TestApp("高德地图", "com.autonavi.minimap"),
            TestApp("百度地图", "com.baidu.BaiduMap")
        )
        
        Log.d(TAG, "开始测试${testApps.size}个热门应用的iTunes API图标获取...")
        
        val results = mutableListOf<TestResult>()
        
        for (app in testApps) {
            try {
                val icons = getIconsFromiTunes(app.name, app.packageName)
                val result = TestResult(
                    app = app,
                    success = icons.isNotEmpty(),
                    iconCount = icons.size,
                    iconUrls = icons,
                    error = null
                )
                results.add(result)
                
                Log.d(TAG, "✅ ${app.name}: 找到${icons.size}个图标")
                icons.forEachIndexed { index, url ->
                    Log.d(TAG, "   图标${index + 1}: $url")
                }
                
            } catch (e: Exception) {
                val result = TestResult(
                    app = app,
                    success = false,
                    iconCount = 0,
                    iconUrls = emptyList(),
                    error = e.message
                )
                results.add(result)
                
                Log.e(TAG, "❌ ${app.name}: 获取失败 - ${e.message}")
            }
            
            // 避免API调用过于频繁
            delay(500)
        }
        
        // 输出测试总结
        printTestSummary(results)
    }
    
    /**
     * 测试单个应用的图标获取
     */
    suspend fun testSingleApp(appName: String, packageName: String): List<String> {
        Log.d(TAG, "测试单个应用: $appName ($packageName)")
        
        return try {
            val icons = getIconsFromiTunes(appName, packageName)
            Log.d(TAG, "✅ 找到${icons.size}个图标:")
            icons.forEachIndexed { index, url ->
                Log.d(TAG, "   图标${index + 1}: $url")
            }
            icons
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 从iTunes获取应用图标
     */
    private suspend fun getIconsFromiTunes(appName: String, packageName: String): List<String> {
        return withContext(Dispatchers.IO) {
            val icons = mutableListOf<String>()
            
            // 1. 通过应用名称搜索
            try {
                val nameIcons = searchiTunesByName(appName)
                icons.addAll(nameIcons)
                Log.d(TAG, "通过名称搜索找到${nameIcons.size}个图标")
            } catch (e: Exception) {
                Log.w(TAG, "名称搜索失败: ${e.message}")
            }
            
            // 2. 通过关键词搜索
            try {
                val keywords = generateSearchKeywords(appName, packageName)
                for (keyword in keywords.take(3)) { // 限制关键词数量
                    val keywordIcons = searchiTunesByKeyword(keyword)
                    icons.addAll(keywordIcons)
                }
                Log.d(TAG, "通过关键词搜索总共找到${icons.size}个图标")
            } catch (e: Exception) {
                Log.w(TAG, "关键词搜索失败: ${e.message}")
            }
            
            icons.distinct()
        }
    }
    
    /**
     * 通过应用名称搜索iTunes
     */
    private suspend fun searchiTunesByName(appName: String): List<String> {
        val encodedName = URLEncoder.encode(appName, "UTF-8")
        val searchUrl = "https://itunes.apple.com/search?term=$encodedName&media=software&entity=software&limit=5"
        
        val response = downloadTextFromUrl(searchUrl)
        return if (response != null) {
            parseiTunesResponse(response, appName)
        } else {
            emptyList()
        }
    }
    
    /**
     * 通过关键词搜索iTunes
     */
    private suspend fun searchiTunesByKeyword(keyword: String): List<String> {
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val searchUrl = "https://itunes.apple.com/search?term=$encodedKeyword&media=software&entity=software&limit=3"
        
        val response = downloadTextFromUrl(searchUrl)
        return if (response != null) {
            parseiTunesResponse(response)
        } else {
            emptyList()
        }
    }
    
    /**
     * 解析iTunes API响应
     */
    private fun parseiTunesResponse(response: String, targetAppName: String? = null): List<String> {
        return try {
            val jsonObject = JSONObject(response)
            val results = jsonObject.getJSONArray("results")
            val icons = mutableListOf<String>()
            
            for (i in 0 until results.length()) {
                val app = results.getJSONObject(i)
                val trackName = app.optString("trackName", "")
                
                // 如果指定了目标应用名称，进行匹配检查
                if (targetAppName != null && !isAppNameMatch(trackName, targetAppName)) {
                    continue
                }
                
                // 提取图标URL
                val iconUrls = extractIconUrls(app)
                icons.addAll(iconUrls)
                
                Log.d(TAG, "找到应用: $trackName")
            }
            
            icons.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "解析iTunes响应失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 提取图标URL
     */
    private fun extractIconUrls(app: JSONObject): List<String> {
        val icons = mutableListOf<String>()
        
        // iTunes提供的图标字段
        val iconFields = listOf("artworkUrl512", "artworkUrl100", "artworkUrl60")
        
        for (field in iconFields) {
            val iconUrl = app.optString(field)
            if (iconUrl.isNotEmpty()) {
                icons.add(iconUrl)
                // 尝试获取更高分辨率版本
                val highResUrl = iconUrl.replace("100x100", "512x512").replace("60x60", "512x512")
                if (highResUrl != iconUrl) {
                    icons.add(highResUrl)
                }
            }
        }
        
        return icons.distinct()
    }
    
    /**
     * 生成搜索关键词
     */
    private fun generateSearchKeywords(appName: String, packageName: String): List<String> {
        val keywords = mutableListOf<String>()
        
        // 应用名称变体
        keywords.add(appName)
        keywords.add(appName.replace("\\s+".toRegex(), ""))
        
        // 从包名提取
        val parts = packageName.split(".")
        if (parts.size >= 2) {
            keywords.add(parts[1]) // 公司名
            if (parts.size >= 3) {
                keywords.add(parts[2]) // 应用名
            }
        }
        
        // 已知关键词映射
        val knownKeywords = mapOf(
            "com.tencent.qqmusic" to listOf("QQ Music", "Tencent Music"),
            "com.netease.cloudmusic" to listOf("NetEase Music", "CloudMusic"),
            "com.eg.android.AlipayGphone" to listOf("Alipay"),
            "com.tencent.mm" to listOf("WeChat"),
            "me.ele" to listOf("Eleme", "Ele.me")
        )
        
        knownKeywords[packageName]?.let { keywords.addAll(it) }
        
        return keywords.distinct().filter { it.isNotBlank() }
    }
    
    /**
     * 检查应用名称匹配
     */
    private fun isAppNameMatch(trackName: String, targetName: String): Boolean {
        val track = trackName.lowercase().trim()
        val target = targetName.lowercase().trim()
        
        return track.contains(target) || target.contains(track) || 
               track.replace("\\s+".toRegex(), "") == target.replace("\\s+".toRegex(), "")
    }
    
    /**
     * 下载文本内容
     */
    private suspend fun downloadTextFromUrl(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                connection.connect()
                
                val text = connection.getInputStream().bufferedReader().use { it.readText() }
                text
            } catch (e: Exception) {
                Log.e(TAG, "下载失败: $url - ${e.message}")
                null
            }
        }
    }
    
    /**
     * 打印测试总结
     */
    private fun printTestSummary(results: List<TestResult>) {
        val successCount = results.count { it.success }
        val totalCount = results.size
        val successRate = (successCount.toDouble() / totalCount * 100).toInt()
        
        Log.d(TAG, "=== iTunes API 测试总结 ===")
        Log.d(TAG, "总应用数: $totalCount")
        Log.d(TAG, "成功获取图标: $successCount")
        Log.d(TAG, "成功率: $successRate%")
        Log.d(TAG, "========================")
        
        // 详细结果
        results.forEach { result ->
            if (result.success) {
                Log.d(TAG, "✅ ${result.app.name}: ${result.iconCount}个图标")
            } else {
                Log.d(TAG, "❌ ${result.app.name}: ${result.error}")
            }
        }
    }
    
    // 数据类
    data class TestApp(val name: String, val packageName: String)
    
    data class TestResult(
        val app: TestApp,
        val success: Boolean,
        val iconCount: Int,
        val iconUrls: List<String>,
        val error: String?
    )
}
