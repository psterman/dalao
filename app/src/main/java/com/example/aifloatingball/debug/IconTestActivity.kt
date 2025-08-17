package com.example.aifloatingball.debug

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.PreciseIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图标测试Activity
 * 用于测试和验证精准图标获取效果
 */
class IconTestActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "IconTestActivity"
    }
    
    private lateinit var preciseIconManager: PreciseIconManager
    private lateinit var testContainer: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_test)
        
        preciseIconManager = PreciseIconManager(this)
        testContainer = findViewById(R.id.test_container)
        
        setupTestButtons()
        runIconTests()
    }
    
    private fun setupTestButtons() {
        findViewById<Button>(R.id.btn_test_ai_apps).setOnClickListener {
            testAIApps()
        }
        
        findViewById<Button>(R.id.btn_test_regular_apps).setOnClickListener {
            testRegularApps()
        }
        
        findViewById<Button>(R.id.btn_test_search_engines).setOnClickListener {
            testSearchEngines()
        }
        
        findViewById<Button>(R.id.btn_clear_results).setOnClickListener {
            testContainer.removeAllViews()
        }
    }
    
    private fun runIconTests() {
        testAIApps()
        testRegularApps()
        testSearchEngines()
    }
    
    private fun testAIApps() {
        val aiApps = listOf(
            "com.deepseek.chat" to "DeepSeek",
            "com.moonshot.kimi" to "Kimi",
            "com.google.android.apps.bard" to "Gemini",
            "com.zhipu.chatglm" to "智谱",
            "com.anthropic.claude" to "Claude",
            "com.openai.chatgpt" to "ChatGPT"
        )
        
        addSectionHeader("AI应用图标测试")
        
        for ((packageName, appName) in aiApps) {
            testSingleIcon(packageName, appName, PreciseIconManager.IconType.AI_APP)
        }
    }
    
    private fun testRegularApps() {
        val regularApps = listOf(
            "com.xingin.xhs" to "小红书",
            "com.zhihu.android" to "知乎",
            "com.ss.android.ugc.aweme" to "抖音",
            "com.sankuai.meituan" to "美团",
            "com.sina.weibo" to "微博",
            "com.douban.frodo" to "豆瓣"
        )
        
        addSectionHeader("常规应用图标测试")
        
        for ((packageName, appName) in regularApps) {
            testSingleIcon(packageName, appName, PreciseIconManager.IconType.REGULAR_APP)
        }
    }
    
    private fun testSearchEngines() {
        val searchEngines = listOf(
            "google" to "Google",
            "baidu" to "百度",
            "bing" to "Bing",
            "sogou" to "搜狗",
            "360" to "360搜索",
            "duckduckgo" to "DuckDuckGo"
        )
        
        addSectionHeader("搜索引擎图标测试")
        
        for ((packageName, appName) in searchEngines) {
            testSingleIcon(packageName, appName, PreciseIconManager.IconType.SEARCH_ENGINE)
        }
    }
    
    private fun testSingleIcon(packageName: String, appName: String, type: PreciseIconManager.IconType) {
        val testItemView = layoutInflater.inflate(R.layout.item_icon_test, testContainer, false)
        val iconView = testItemView.findViewById<ImageView>(R.id.test_icon)
        val nameView = testItemView.findViewById<TextView>(R.id.test_name)
        val statusView = testItemView.findViewById<TextView>(R.id.test_status)
        
        nameView.text = appName
        statusView.text = "加载中..."
        iconView.setImageResource(R.drawable.ic_apps)
        
        testContainer.addView(testItemView)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "开始测试图标: $appName ($packageName)")
                val startTime = System.currentTimeMillis()
                
                val drawable = preciseIconManager.getPreciseIcon(packageName, appName, type)
                
                val endTime = System.currentTimeMillis()
                val loadTime = endTime - startTime
                
                withContext(Dispatchers.Main) {
                    if (drawable != null) {
                        iconView.setImageDrawable(drawable)
                        statusView.text = "✅ 成功 (${loadTime}ms)"
                        statusView.setTextColor(getColor(android.R.color.holo_green_dark))
                        Log.d(TAG, "图标加载成功: $appName, 耗时: ${loadTime}ms")
                    } else {
                        statusView.text = "❌ 失败 (${loadTime}ms)"
                        statusView.setTextColor(getColor(android.R.color.holo_red_dark))
                        Log.w(TAG, "图标加载失败: $appName, 耗时: ${loadTime}ms")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "图标测试异常: $appName", e)
                withContext(Dispatchers.Main) {
                    statusView.text = "💥 异常: ${e.message}"
                    statusView.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }
    }
    
    private fun addSectionHeader(title: String) {
        val headerView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(getColor(android.R.color.black))
            setPadding(16, 32, 16, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        testContainer.addView(headerView)
    }
}
