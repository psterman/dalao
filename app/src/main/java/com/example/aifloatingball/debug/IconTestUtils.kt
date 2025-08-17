package com.example.aifloatingball.debug

import android.content.Context
import android.util.Log
import com.example.aifloatingball.manager.PreciseIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 图标测试工具类
 * 用于快速测试和验证图标获取效果
 */
object IconTestUtils {
    
    private const val TAG = "IconTestUtils"
    
    /**
     * 测试AI应用图标获取
     */
    fun testAIAppIcons(context: Context) {
        val preciseIconManager = PreciseIconManager(context)
        
        val aiApps = listOf(
            "com.deepseek.chat" to "DeepSeek",
            "com.moonshot.kimi" to "Kimi",
            "com.google.android.apps.bard" to "Gemini",
            "com.zhipu.chatglm" to "智谱",
            "com.anthropic.claude" to "Claude",
            "com.openai.chatgpt" to "ChatGPT",
            "ai.perplexity.app" to "Perplexity",
            "com.bytedance.doubao" to "豆包",
            "com.aliyun.tongyi" to "通义千问",
            "com.baidu.wenxin" to "文心一言",
            "com.iflytek.xinghuo" to "讯飞星火"
        )
        
        Log.d(TAG, "开始测试AI应用图标获取...")
        
        CoroutineScope(Dispatchers.IO).launch {
            for ((packageName, appName) in aiApps) {
                try {
                    val iconType = preciseIconManager.getAppType(packageName, appName)
                    val icon = preciseIconManager.getPreciseIcon(packageName, appName, iconType)
                    
                    if (icon != null) {
                        Log.d(TAG, "✅ AI应用图标获取成功: $appName ($packageName)")
                    } else {
                        Log.w(TAG, "❌ AI应用图标获取失败: $appName ($packageName)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ AI应用图标获取异常: $appName ($packageName)", e)
                }
            }
        }
    }
    
    /**
     * 测试搜索引擎图标获取
     */
    fun testSearchEngineIcons(context: Context) {
        val preciseIconManager = PreciseIconManager(context)
        
        val searchEngines = listOf(
            "google" to "Google",
            "baidu" to "百度",
            "bing" to "Bing",
            "sogou" to "搜狗",
            "360" to "360搜索",
            "duckduckgo" to "DuckDuckGo",
            "yahoo" to "Yahoo",
            "yandex" to "Yandex"
        )
        
        Log.d(TAG, "开始测试搜索引擎图标获取...")
        
        CoroutineScope(Dispatchers.IO).launch {
            for ((packageName, appName) in searchEngines) {
                try {
                    val iconType = PreciseIconManager.IconType.SEARCH_ENGINE
                    val icon = preciseIconManager.getPreciseIcon(packageName, appName, iconType)
                    
                    if (icon != null) {
                        Log.d(TAG, "✅ 搜索引擎图标获取成功: $appName ($packageName)")
                    } else {
                        Log.w(TAG, "❌ 搜索引擎图标获取失败: $appName ($packageName)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 搜索引擎图标获取异常: $appName ($packageName)", e)
                }
            }
        }
    }
    
    /**
     * 测试常规应用图标获取
     */
    fun testRegularAppIcons(context: Context) {
        val preciseIconManager = PreciseIconManager(context)
        
        val regularApps = listOf(
            "com.xingin.xhs" to "小红书",
            "com.zhihu.android" to "知乎",
            "com.ss.android.ugc.aweme" to "抖音",
            "com.sankuai.meituan" to "美团",
            "com.sina.weibo" to "微博",
            "com.douban.frodo" to "豆瓣",
            "com.kuaishou.nebula" to "快手",
            "com.tencent.mm" to "微信",
            "com.tencent.mobileqq" to "QQ",
            "com.taobao.taobao" to "淘宝",
            "com.jingdong.app.mall" to "京东",
            "com.eg.android.AlipayGphone" to "支付宝"
        )
        
        Log.d(TAG, "开始测试常规应用图标获取...")
        
        CoroutineScope(Dispatchers.IO).launch {
            for ((packageName, appName) in regularApps) {
                try {
                    val iconType = preciseIconManager.getAppType(packageName, appName)
                    val icon = preciseIconManager.getPreciseIcon(packageName, appName, iconType)
                    
                    if (icon != null) {
                        Log.d(TAG, "✅ 常规应用图标获取成功: $appName ($packageName)")
                    } else {
                        Log.w(TAG, "❌ 常规应用图标获取失败: $appName ($packageName)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 常规应用图标获取异常: $appName ($packageName)", e)
                }
            }
        }
    }
    
    /**
     * 运行所有图标测试
     */
    fun runAllIconTests(context: Context) {
        Log.d(TAG, "🚀 开始运行所有图标测试...")
        
        testAIAppIcons(context)
        testSearchEngineIcons(context)
        testRegularAppIcons(context)
        
        Log.d(TAG, "🏁 所有图标测试已启动，请查看日志输出")
    }
    
    /**
     * 测试特定应用的图标获取
     */
    fun testSpecificApp(context: Context, packageName: String, appName: String) {
        val preciseIconManager = PreciseIconManager(context)
        
        Log.d(TAG, "测试特定应用图标: $appName ($packageName)")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val iconType = preciseIconManager.getAppType(packageName, appName)
                Log.d(TAG, "应用类型判断: $iconType")
                
                val icon = preciseIconManager.getPreciseIcon(packageName, appName, iconType)
                
                if (icon != null) {
                    Log.d(TAG, "✅ 特定应用图标获取成功: $appName")
                } else {
                    Log.w(TAG, "❌ 特定应用图标获取失败: $appName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 特定应用图标获取异常: $appName", e)
            }
        }
    }
}
