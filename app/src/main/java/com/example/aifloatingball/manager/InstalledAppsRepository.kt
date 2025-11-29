package com.example.aifloatingball.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.aifloatingball.model.AppCategory
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.R

/**
 * 负责扫描设备上可启动的应用，按类别归类并过滤“无搜索意义”的系统工具。
 * 仅使用 ACTION_MAIN + CATEGORY_LAUNCHER，兼容各厂商系统与 Android 11+ 包可见性。
 */
class InstalledAppsRepository private constructor(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val overrides = AppCategoryOverridesManager.getInstance(context)

    // 内存缓存，避免重复扫描
    @Volatile
    private var cached: List<AppSearchConfig>? = null

    companion object {
        @Volatile
        private var instance: InstalledAppsRepository? = null

        fun getInstance(context: Context): InstalledAppsRepository {
            return instance ?: synchronized(this) {
                instance ?: InstalledAppsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 异步前应在后台线程调用。
     */
    fun scanInstalledSearchableApps(): List<AppSearchConfig> {
        cached?.let { return it }

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, 0)

        val results = mutableListOf<AppSearchConfig>()
        var order = 1

        for (ri in activities) {
            val appInfo = ri.activityInfo?.applicationInfo ?: continue
            val pkg = ri.activityInfo.packageName ?: continue
            val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()

            // 过滤无搜索意义或系统工具
            if (isNonSearchUtility(pkg, label, appInfo)) continue

            val category = overrides.getOverride(pkg) ?: classify(appInfo, pkg, label)

            // 构造 AppSearchConfig；searchUrl 对未知应用留空，由通用发送/自动粘贴逻辑处理
            val config = AppSearchConfig(
                appId = pkg,
                appName = label.ifEmpty { pkg.substringAfterLast('.') },
                packageName = pkg,
                isEnabled = true,
                order = order++,
                iconResId = R.drawable.ic_web_default,
                searchUrl = "",
                category = category,
                description = ""
            )
            results.add(config)
        }

        // 中文友好排序
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        val sorted = results.sortedWith(compareBy(collator) { it.appName })
        cached = sorted
        return sorted
    }

    fun getCached(): List<AppSearchConfig>? = cached

    fun invalidate() { cached = null }

    private fun isNonSearchUtility(pkg: String, label: String, appInfo: ApplicationInfo): Boolean {
        val lower = label.lowercase()
        val systemToolKeywords = listOf(
            "设置", "系統設置", "system", "settings", "时钟", "闹钟", "时鐘", "clock",
            "日历", "行事曆", "calendar", "记事", "备忘", "notes", "notepad", "便签",
            "录音", "相机", "相册", "图库", "文件管理", "下载", "计算器", "天气", "联系人",
            "电话", "短信", "信息", "电子邮件", "邮箱", "邮件", "浏览器" // 浏览器保留到分类器中判断
        )

        val pkgBlacklist = listOf(
            "com.android.settings",
            "com.android.deskclock",
            "com.android.calendar",
            "com.android.notes",
            "com.coloros.notes",
            "com.miui.notes",
            "com.huawei.notepad",
            "com.samsung.android.app.notes"
        )

        if (pkg in pkgBlacklist) return true

        // 系统组件但非用户应用（仍允许来自厂商商店/浏览器等用户级应用）
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val looksLikeTool = systemToolKeywords.any { lower.contains(it) }

        // 若明显是工具且为系统应用，则过滤
        if (looksLikeTool && (isSystemApp || isUpdatedSystem)) return true

        // 浏览器另判：常见浏览器应保留用于搜索
        val browserPkgs = listOf("com.android.chrome", "com.tencent.mtt", "org.mozilla.firefox", "com.ucmobile")
        if (lower.contains("浏览器") && pkg !in browserPkgs) return true

        return false
    }

    private fun classify(appInfo: ApplicationInfo, pkg: String, label: String): AppCategory {
        val l = label.lowercase().trim()
        val p = pkg.lowercase().trim()
        
        // 优先检查AI应用（在系统category之前）
        // 这样可以确保AI应用不会被系统category误分类
        val aiPackageNames = setOf(
            "com.poe.android", "ai.perplexity.app.android", "com.manus.im.app",
            "ai.x.grok", "com.microsoft.copilot", "com.google.android.apps.assistant",
            "com.google.android.apps.gemini", "com.tencent.ima"
        )
        if (p in aiPackageNames) {
            android.util.Log.d("InstalledAppsRepository", "✅ AI应用（完整包名匹配）: $label ($pkg) -> AI")
            return AppCategory.AI
        }
        
        // 1) 使用系统 category（Android 26+），但AI应用优先
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.VIDEO
                ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC
                ApplicationInfo.CATEGORY_MAPS -> return AppCategory.MAPS
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> {
                    // 生产力类大多非搜索，先落生活
                    return AppCategory.LIFESTYLE
                }
            }
        }

        // 2) 规则与关键词（覆盖国产主流）
        // 注意：规则按顺序匹配，AI应用规则放在最前面，确保优先匹配
        val rules: List<Pair<(String, String) -> Boolean, AppCategory>> = listOf(
            // AI应用分类规则（最高优先级，放在最前面）
            { ll: String, pp: String -> 
                // 包名关键词匹配（精确匹配，避免误匹配）
                // 完整包名匹配（最高优先级）
                pp == "com.poe.android" || pp == "ai.perplexity.app.android" || 
                pp == "com.manus.im.app" || pp == "ai.x.grok" || 
                pp == "com.microsoft.copilot" || pp == "com.google.android.apps.assistant" ||
                pp == "com.google.android.apps.gemini" || pp == "com.tencent.ima" ||
                // 包名关键词匹配
                (pp.contains("openai") && !pp.contains("open")) || // 避免匹配"open"关键词
                pp.contains("deepseek") || pp.contains("kimi") || 
                (pp.contains("gpt") && !pp.contains("script")) || // 避免匹配"script"关键词
                pp.contains("copilot") || pp.contains("yuanbao") || 
                pp.contains("zhipu") || pp.contains("larus.nova") || pp.contains("doubao") ||
                pp.contains("tongyi") || pp.contains("aliyun.tongyi") || pp.contains("hunyuan") ||
                (pp.contains("tencent.ima") && !pp.contains("tim")) || // IMA，排除TIM
                pp.contains("com.tencent.ima") && !pp.contains("tim") || // IMA完整包名，排除TIM
                pp.contains("wenxiaoyan") || pp.contains("baidu.newapp") || pp.contains("spark") || 
                pp.contains("iflytek") || pp.contains("qingyan") || pp.contains("chatglm") || 
                pp.contains("claude") || pp.contains("gemini") || pp.contains("perplexity") || 
                pp.contains("grok") || pp.contains("manus") || pp.contains("metaso") || 
                pp.contains("mita") || pp.contains("poe") || pp.contains("namiso") || 
                (pp.contains("nano") && (pp.contains("ai") || pp.contains("qihoo"))) || // 纳米AI，避免误匹配其他nano应用
                pp.contains("anthropic") || pp.contains("moonshot") || pp.contains("xinghuo") ||
                // 新增AI应用包名匹配
                pp.contains("monica") || pp.contains("com.monica") || // Monica
                pp.contains("metaai") || pp.contains("meta.ai") || pp.contains("com.meta.ai") || // MetaAI
                pp.contains("merlin") || pp.contains("com.merlin") || // Merlin
                pp.contains("google.android.apps.assistant") || pp.contains("googleassistant") || // Google Assistant
                pp.contains("xiaonao") || pp.contains("com.xiaonao") || pp.contains("xiaonao.ai") // 小脑
            } to AppCategory.AI,
            { ll: String, pp: String -> 
                // 应用名称关键词匹配（ll已经是小写，直接匹配）
                // 完整名称匹配（去除空格、连字符、下划线后比较）
                val cleanName = ll.replace(" ", "").replace("-", "").replace("_", "").replace(".", "")
                cleanName == "poe" || cleanName == "perplexity" || cleanName == "monica" || 
                cleanName == "metaai" || cleanName == "merlin" || cleanName == "manus" || 
                cleanName == "grok" || cleanName == "copilot" || cleanName == "小脑" ||
                // 名称关键词匹配（ll已经是小写）
                ll.contains("google assistant") || ll.contains("谷歌助手") || ll.contains("google助手") ||
                (ll.contains("ai") && !ll.contains("mail")) || // 避免匹配"mail"
                ll.contains("助手") || ll.contains("豆包") || 
                ll.contains("千问") || ll.contains("元宝") || ll.contains("文小言") ||
                ll.contains("星火") || ll.contains("智谱") || ll.contains("清言") ||
                ll.contains("deepseek") || ll.contains("kimi") || ll.contains("chatgpt") ||
                ll.contains("claude") || ll.contains("gemini") || ll.contains("grok") ||
                ll.contains("perplexity") || ll.contains("manus") || ll.contains("秘塔") ||
                ll.contains("poe") || ll.contains("纳米") || ll.contains("ima") ||
                ll.contains("通义") || ll.contains("讯飞") || ll.contains("腾讯元宝") ||
                ll.contains("monica") || ll.contains("metaai") || ll.contains("meta ai") || // Monica, MetaAI
                ll.contains("merlin") || // Merlin
                ll.contains("copilot") || ll.contains("小脑") // Copilot, 小脑
            } to AppCategory.AI,
            
            // 其他应用分类规则（在AI规则之后）
            { _: String, pp: String -> pp.contains("taobao") || pp.contains("tmall") || pp.contains("jingdong") || pp.contains("jd") || pp.contains("xhs") || pp.contains("pinduoduo") } to AppCategory.SHOPPING,
            { ll: String, pp: String -> ll.contains("购物") || ll.contains("商城") || pp.contains("mall") } to AppCategory.SHOPPING,
            { ll: String, pp: String -> ll.contains("聊天") || ll.contains("社交") || pp.contains("weibo") || pp.contains("zhihu") || pp.contains("tiktok") || pp.contains("douyin") || pp.contains("qq") || pp.contains("wechat") || pp.contains("tim") } to AppCategory.SOCIAL,
            { ll: String, pp: String -> ll.contains("视频") || ll.contains("影视") || pp.contains("bili") || pp.contains("youku") || pp.contains("iqiyi") || pp.contains("kuaishou") } to AppCategory.VIDEO,
            { ll: String, pp: String -> ll.contains("音乐") || ll.contains("听歌") || pp.contains("music") || pp.contains("spotify") || pp.contains("netease") } to AppCategory.MUSIC,
            { ll: String, pp: String -> ll.contains("地图") || ll.contains("导航") || ll.contains("打车") || pp.contains("map") || pp.contains("gaode") || pp.contains("baidu.map") } to AppCategory.MAPS,
            { ll: String, pp: String -> ll.contains("浏览器") || pp.contains("chrome") || pp.contains("mtt") || pp.contains("firefox") || pp.contains("ucbrowser") } to AppCategory.BROWSER,
            { ll: String, pp: String -> ll.contains("支付") || ll.contains("钱包") || ll.contains("银行") || pp.contains("alipay") || pp.contains("wallet") || pp.contains("bank") } to AppCategory.FINANCE,
            { ll: String, pp: String -> ll.contains("出行") || ll.contains("打车") || ll.contains("航旅") || pp.contains("ctrip") || pp.contains("qunar") || pp.contains("didiglobal") } to AppCategory.TRAVEL,
            { ll: String, pp: String -> ll.contains("招聘") || ll.contains("求职") || pp.contains("boss") || pp.contains("zhilian") } to AppCategory.JOBS,
            { ll: String, pp: String -> ll.contains("教育") || ll.contains("学习") || pp.contains("school") || pp.contains("study") } to AppCategory.EDUCATION,
            { ll: String, pp: String -> ll.contains("新闻") || ll.contains("资讯") || pp.contains("news") } to AppCategory.NEWS,
        )

        for ((pred, cat) in rules) {
            if (pred(l, p)) {
                // 调试日志：记录AI应用分类
                if (cat == AppCategory.AI) {
                    android.util.Log.d("InstalledAppsRepository", "✅ AI应用分类: $label ($pkg) -> AI")
                }
                return cat
            }
        }
        // 调试日志：记录未匹配的应用
        android.util.Log.d("InstalledAppsRepository", "⚠️ 未匹配应用: $label ($pkg) -> LIFESTYLE")
        return AppCategory.LIFESTYLE
    }
}

