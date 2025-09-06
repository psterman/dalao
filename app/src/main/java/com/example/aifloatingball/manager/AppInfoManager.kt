package com.example.aifloatingball.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.aifloatingball.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer
import java.util.*
import kotlin.math.max
import kotlin.math.min

class AppInfoManager private constructor() {

    private var appList: List<AppInfo> = emptyList()
    private var isLoaded = false
    private val searchCache = mutableMapOf<String, List<AppInfo>>() // 搜索结果缓存
    private val TAG = "AppInfoManager"

    fun loadApps(context: Context) {
        if (isLoaded) return
        CoroutineScope(Dispatchers.IO).launch {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
            
            val loadedApps = resolvedInfos.map { resolvedInfo ->
                val packageName = resolvedInfo.activityInfo.packageName
                val urlScheme = getUrlScheme(pm, packageName)
                
                // 使用与简易模式相同的图标加载策略
                val icon = try {
                    // 首先尝试从PackageManager直接加载应用图标
                    pm.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    android.util.Log.w("AppInfoManager", "无法加载应用图标: $packageName", e)
                    try {
                        // 尝试从resolvedInfo加载
                        resolvedInfo.loadIcon(pm)
                    } catch (e2: Exception) {
                        android.util.Log.w("AppInfoManager", "resolvedInfo也无法加载图标: $packageName", e2)
                        // 最后使用系统默认图标
                        pm.getDefaultActivityIcon()
                    }
                }
                
                AppInfo(
                    label = resolvedInfo.loadLabel(pm).toString(),
                    packageName = packageName,
                    icon = icon,
                    urlScheme = urlScheme
                )
            }.sortedBy { it.label }
            
            withContext(Dispatchers.Main) {
                appList = loadedApps
                isLoaded = true
            }
        }
    }

    /**
     * 智能搜索匹配算法
     * 支持多种匹配策略：完全匹配、前缀匹配、拼音匹配、模糊匹配等
     */
    fun search(query: String): List<AppInfo> {
        if (query.isBlank() || !isLoaded) {
            return emptyList()
        }
        
        val normalizedQuery = normalizeString(query)
        
        // 检查缓存
        searchCache[normalizedQuery]?.let { cachedResults ->
            Log.d(TAG, "使用缓存结果: '$query', 找到 ${cachedResults.size} 个应用")
            return cachedResults
        }
        
        Log.d(TAG, "开始搜索: '$query', 应用总数: ${appList.size}")
        
        val results = mutableMapOf<AppInfo, Int>() // 应用 -> 匹配分数
        
        // 并行处理搜索以提升性能
        appList.forEach { app ->
            val score = calculateMatchScore(normalizedQuery, app)
            if (score > 0) {
                results[app] = score
                Log.d(TAG, "匹配应用: ${app.label}, 分数: $score")
            }
        }
        
        // 按匹配分数降序排序，分数相同时按应用名称排序
        val sortedResults = results.toList()
            .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }
                .thenBy { it.first.label })
            .map { it.first }
        
        // 缓存结果
        searchCache[normalizedQuery] = sortedResults
        
        // 限制缓存大小，避免内存溢出
        if (searchCache.size > 100) {
            val keysToRemove = searchCache.keys.take(20)
            keysToRemove.forEach { searchCache.remove(it) }
        }
        
        Log.d(TAG, "搜索结果: ${sortedResults.size} 个应用")
        return sortedResults
    }
    
    /**
     * 计算匹配分数
     * 分数越高，匹配度越好
     */
    private fun calculateMatchScore(query: String, app: AppInfo): Int {
        val appName = normalizeString(app.label)
        var score = 0
        
        // 1. 完全匹配 (最高分100)
        if (appName.equals(query, ignoreCase = true)) {
            score += 100
        }
        
        // 2. 前缀匹配 (分数80-90)
        if (appName.startsWith(query, ignoreCase = true)) {
            score += 90 - (appName.length - query.length) // 长度差异越小分数越高
        }
        
        // 3. 包含匹配 (分数60-80)
        if (appName.contains(query, ignoreCase = true)) {
            val containsScore = 80 - (appName.indexOf(query, ignoreCase = true) * 2) // 位置越靠前分数越高
            score += max(containsScore, 60)
        }
        
        // 4. 拼音匹配 (分数40-70)
        val pinyinScore = calculatePinyinMatch(query, appName)
        score += pinyinScore
        
        // 5. 首字母匹配 (分数30-50)
        val initialScore = calculateInitialMatch(query, appName)
        score += initialScore
        
        // 6. 模糊匹配 (分数20-40)
        val fuzzyScore = calculateFuzzyMatch(query, appName)
        score += fuzzyScore
        
        // 7. 英文匹配 (分数10-30)
        val englishScore = calculateEnglishMatch(query, appName)
        score += englishScore
        
        // 8. 包名匹配 (分数5-15)
        val packageScore = calculatePackageMatch(query, app.packageName)
        score += packageScore
        
        return score
    }
    
    /**
     * 标准化字符串：去除特殊字符、统一大小写、去除空格
     */
    private fun normalizeString(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "") // 只保留字母、数字和空格
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * 拼音匹配算法
     * 支持中文转拼音匹配
     */
    private fun calculatePinyinMatch(query: String, appName: String): Int {
        try {
            // 简单的拼音匹配：提取中文字符进行匹配
            val chineseChars = appName.filter { it.toString().matches(Regex("[\\u4e00-\\u9fa5]")) }
            if (chineseChars.isNotEmpty()) {
                // 检查是否存在拼音匹配模式
                val pinyinPattern = generatePinyinPattern(chineseChars)
                if (pinyinPattern.contains(query, ignoreCase = true)) {
                    return 60
                }
                
                // 首字母拼音匹配
                val initials = extractChineseInitials(chineseChars)
                if (initials.equals(query, ignoreCase = true)) {
                    return 50
                }
                
                // 部分拼音匹配
                if (initials.contains(query, ignoreCase = true)) {
                    return 40
                }
            }
        } catch (e: Exception) {
            Log.w("AppInfoManager", "拼音匹配失败: ${e.message}")
        }
        return 0
    }
    
    /**
     * 生成简单的拼音模式
     */
    private fun generatePinyinPattern(chineseText: String): String {
        // 这里实现一个简化的拼音转换
        // 在实际项目中，可以使用更完善的拼音库如TinyPinyin
        val pinyinMap = mapOf(
            '微' to "wei", '信' to "xin", '支' to "zhi", '付' to "fu", '宝' to "bao",
            '淘' to "tao", '宝' to "bao", '京' to "jing", '东' to "dong", '美' to "mei",
            '团' to "tuan", '滴' to "di", '滴' to "di", '出' to "chu", '行' to "xing",
            '饿' to "e", '了' to "le", '么' to "me", '百' to "bai", '度' to "du",
            '腾' to "teng", '讯' to "xun", '阿' to "a", '里' to "li", '巴' to "ba",
            '网' to "wang", '易' to "yi", '云' to "yun", '音' to "yin", '乐' to "yue",
            '抖' to "dou", '音' to "yin", '快' to "kuai", '手' to "shou", '小' to "xiao",
            '红' to "hong", '书' to "shu", '知' to "zhi", '乎' to "hu", '哔' to "bi",
            '哩' to "li", '哔' to "bi", '哩' to "li", '爱' to "ai", '奇' to "qi",
            '艺' to "yi", '优' to "you", '酷' to "ku", '搜' to "sou", '狐' to "hu",
            '新' to "xin", '浪' to "lang", '微' to "wei", '博' to "bo", '今' to "jin",
            '日' to "ri", '头' to "tou", '条' to "tiao", '智' to "zhi", '联' to "lian",
            '招' to "zhao", '商' to "shang", '银' to "yin", '行' to "hang", '建' to "jian",
            '设' to "she", '工' to "gong", '商' to "shang", '农' to "nong", '业' to "ye",
            '中' to "zhong", '国' to "guo", '平' to "ping", '安' to "an", '人' to "ren",
            '寿' to "shou", '保' to "bao", '险' to "xian", '太' to "tai", '平' to "ping",
            '洋' to "yang", '钉' to "ding", '钉' to "ding", '企' to "qi", '业' to "ye",
            'Q' to "Q", 'Q' to "Q", '邮' to "you", '箱' to "xiang", '高' to "gao",
            '德' to "de", '地' to "di", '图' to "tu"
        )
        
        return chineseText.map { char ->
            pinyinMap[char] ?: char.toString()
        }.joinToString("")
    }
    
    /**
     * 提取中文首字母
     */
    private fun extractChineseInitials(chineseText: String): String {
        val initialMap = mapOf(
            '微' to "w", '信' to "x", '支' to "z", '付' to "f", '宝' to "b",
            '淘' to "t", '宝' to "b", '京' to "j", '东' to "d", '美' to "m",
            '团' to "t", '滴' to "d", '滴' to "d", '出' to "c", '行' to "x",
            '饿' to "e", '了' to "l", '么' to "m", '百' to "b", '度' to "d",
            '腾' to "t", '讯' to "x", '阿' to "a", '里' to "l", '巴' to "b",
            '网' to "w", '易' to "y", '云' to "y", '音' to "y", '乐' to "y",
            '抖' to "d", '音' to "y", '快' to "k", '手' to "s", '小' to "x",
            '红' to "h", '书' to "s", '知' to "z", '乎' to "h", '哔' to "b",
            '哩' to "l", '哔' to "b", '哩' to "l", '爱' to "a", '奇' to "q",
            '艺' to "y", '优' to "y", '酷' to "k", '搜' to "s", '狐' to "h",
            '新' to "x", '浪' to "l", '微' to "w", '博' to "b", '今' to "j",
            '日' to "r", '头' to "t", '条' to "t", '智' to "z", '联' to "l",
            '招' to "z", '商' to "s", '银' to "y", '行' to "h", '建' to "j",
            '设' to "s", '工' to "g", '商' to "s", '农' to "n", '业' to "y",
            '中' to "z", '国' to "g", '平' to "p", '安' to "a", '人' to "r",
            '寿' to "s", '保' to "b", '险' to "x", '太' to "t", '平' to "p",
            '洋' to "y", '钉' to "d", '钉' to "d", '企' to "q", '业' to "y",
            'Q' to "q", 'Q' to "q", '邮' to "y", '箱' to "x", '高' to "g",
            '德' to "d", '地' to "d", '图' to "t"
        )
        
        return chineseText.map { char ->
            initialMap[char] ?: if (char.toString().matches(Regex("[a-zA-Z]"))) char.lowercase() else ""
        }.joinToString("")
    }
    
    /**
     * 首字母匹配算法
     */
    private fun calculateInitialMatch(query: String, appName: String): Int {
        val initials = extractInitials(appName)
        
        if (initials.equals(query, ignoreCase = true)) {
            return 50
        }
        
        if (initials.contains(query, ignoreCase = true)) {
            return 30
        }
        
        return 0
    }
    
    /**
     * 提取首字母
     */
    private fun extractInitials(text: String): String {
        return text.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { word ->
                when {
                    word[0].toString().matches(Regex("[a-zA-Z]")) -> word[0].lowercase()
                    word[0].toString().matches(Regex("[\\u4e00-\\u9fa5]")) -> {
                        // 中文字符，使用简单映射
                        extractChineseInitials(word[0].toString())
                    }
                    else -> ""
                }
            }
            .joinToString("")
    }
    
    /**
     * 模糊匹配算法（基于编辑距离）
     */
    private fun calculateFuzzyMatch(query: String, appName: String): Int {
        if (query.length < 2 || appName.length < 2) return 0
        
        val similarity = calculateSimilarity(query, appName)
        
        return when {
            similarity > 0.8 -> 40
            similarity > 0.6 -> 30
            similarity > 0.4 -> 20
            else -> 0
        }
    }
    
    /**
     * 计算字符串相似度
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return (maxLen - levenshteinDistance(s1, s2)) / maxLen.toDouble()
    }
    
    /**
     * 计算编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    /**
     * 英文匹配算法
     */
    private fun calculateEnglishMatch(query: String, appName: String): Int {
        val englishWords = extractEnglishWords(appName)
        
        if (englishWords.isNotEmpty()) {
            if (englishWords.contains(query, ignoreCase = true)) {
                return 30
            }
            
            val englishInitials = englishWords.split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { it[0].lowercase() }
                .joinToString("")
                
            if (englishInitials.equals(query, ignoreCase = true)) {
                return 20
            }
            
            if (englishInitials.contains(query, ignoreCase = true)) {
                return 10
            }
        }
        
        return 0
    }
    
    /**
     * 提取英文单词
     */
    private fun extractEnglishWords(text: String): String {
        return text.replace(Regex("[^a-zA-Z\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * 包名匹配算法
     */
    private fun calculatePackageMatch(query: String, packageName: String): Int {
        if (packageName.contains(query, ignoreCase = true)) {
            return 15
        }
        
        // 提取包名中的关键词
        val packageParts = packageName.split(".")
        packageParts.forEach { part ->
            if (part.contains(query, ignoreCase = true)) {
                return 10
            }
        }
        
        return 0
    }
    
    fun isLoaded(): Boolean = isLoaded
    
    /**
     * 获取所有应用列表
     */
    fun getAllApps(): List<AppInfo> = appList
    
    /**
     * 清理搜索缓存
     */
    fun clearSearchCache() {
        searchCache.clear()
        Log.d(TAG, "搜索缓存已清理")
    }
    
    /**
     * 重新加载应用列表
     */
    fun reload(context: Context) {
        isLoaded = false
        appList = emptyList()
        clearSearchCache()
        loadApps(context)
        Log.d(TAG, "应用列表重新加载")
    }
    
    private fun getUrlScheme(pm: PackageManager, packageName: String): String? {
        return try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = packageInfo.activities
            
            if (activities != null) {
                for (activityInfo in activities) {
                    val intentFilters = pm.queryIntentActivities(
                        Intent().setClassName(packageName, activityInfo.name),
                        PackageManager.GET_INTENT_FILTERS
                    )
                    
                    for (resolveInfo in intentFilters) {
                        resolveInfo.filter?.let { filter ->
                            val schemes = filter.schemesIterator()
                            while (schemes.hasNext()) {
                                val scheme = schemes.next()
                                if (scheme.isNotEmpty()) {
                                    return scheme
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: AppInfoManager? = null

        fun getInstance(): AppInfoManager {
            return instance ?: synchronized(this) {
                instance ?: AppInfoManager().also { instance = it }
            }
        }
    }
} 