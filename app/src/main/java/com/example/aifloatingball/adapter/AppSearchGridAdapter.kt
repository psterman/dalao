package com.example.aifloatingball.adapter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppCategory
import com.example.aifloatingball.model.AppSearchSettings
import com.example.aifloatingball.manager.AppIconManager
import com.example.aifloatingball.manager.IconPreloader
import com.example.aifloatingball.utils.IconProcessor
import kotlinx.coroutines.*

/**
 * 应用搜索网格适配器
 */
class AppSearchGridAdapter(
    private val context: Context,
    private var appConfigs: List<AppSearchConfig> = emptyList(),
    private val onAppClick: (AppSearchConfig, String) -> Unit,
    private val onAppSelected: ((AppSearchConfig) -> Unit)? = null,
    private val getCurrentQuery: (() -> String)? = null
) : RecyclerView.Adapter<AppSearchGridAdapter.AppViewHolder>() {

    private var searchQuery: String = ""
    private val iconManager = AppIconManager.getInstance(context)
    private val iconPreloader = IconPreloader.getInstance(context)
    private val iconProcessor = IconProcessor(context)
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val appStatusIndicator: View = itemView.findViewById(R.id.app_status_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_app_search_grid, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appConfig = appConfigs[position]
        
        // 设置应用名称
        holder.appName.text = appConfig.appName
        
        // 检查应用是否已安装
        val isInstalled = isAppInstalled(appConfig.packageName)
        android.util.Log.i("APP_DETECTION", "🔍 Binding ${appConfig.appName} (${appConfig.packageName}): installed=$isInstalled")
        
        // 设置应用图标 - 使用异步加载
        loadAppIconAsync(appConfig, holder, isInstalled)
        
        // 设置安装状态 - 绿点表示已安装，红点表示未安装
        holder.appStatusIndicator.visibility = View.VISIBLE
        if (isInstalled) {
            holder.appStatusIndicator.setBackgroundResource(R.drawable.status_indicator_installed)
            holder.appIcon.alpha = 1.0f
        } else {
            holder.appStatusIndicator.setBackgroundResource(R.drawable.status_indicator_not_installed)
            holder.appIcon.alpha = 0.6f
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            if (!isInstalled) {
                // 应用未安装，显示安装提示弹窗
                showInstallDialog(appConfig)
                return@setOnClickListener
            }

            // 通知应用被选中（仅在已安装时）
            onAppSelected?.invoke(appConfig)

            // 获取当前输入框的实时内容
            val currentQuery = getCurrentQuery?.invoke()?.trim() ?: searchQuery

            if (currentQuery.isNotEmpty()) {
                // 有搜索内容时直接搜索
                onAppClick(appConfig, currentQuery)
            }
            // 输入框为空时，只切换图标，不显示任何提示
        }
        
        // 长按显示菜单
        holder.itemView.setOnLongClickListener {
            showAppMenu(appConfig, isInstalled)
            true
        }
    }

    override fun getItemCount(): Int = appConfigs.size

    /**
     * 更新应用配置列表
     */
    fun updateAppConfigs(newConfigs: List<AppSearchConfig>) {
        appConfigs = newConfigs
        notifyDataSetChanged()
    }
    
    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        searchQuery = query
        notifyDataSetChanged()
    }

    /**
     * 清理资源
     */
    fun onDestroy() {
        adapterScope.cancel()
    }

    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            // 尝试多种方法检测应用是否安装
            val packageManager = context.packageManager
            
            // 方法1: 使用getPackageInfo
            try {
                packageManager.getPackageInfo(packageName, 0)
                android.util.Log.i("APP_DETECTION", "✅ App $packageName is installed (method 1)")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                android.util.Log.w("APP_DETECTION", "❌ App $packageName not found with method 1")
            }
            
            // 方法2: 使用getApplicationInfo
            try {
                packageManager.getApplicationInfo(packageName, 0)
                android.util.Log.i("APP_DETECTION", "✅ App $packageName is installed (method 2)")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                android.util.Log.w("APP_DETECTION", "❌ App $packageName not found with method 2")
            }
            
            // 方法3: 使用getLaunchIntentForPackage
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                android.util.Log.i("APP_DETECTION", "✅ App $packageName is installed (method 3)")
                return true
            }
            
            android.util.Log.e("APP_DETECTION", "❌ App $packageName is NOT installed (all methods failed)")
            false
        } catch (e: Exception) {
            android.util.Log.e("APP_DETECTION", "💥 Error checking app $packageName: ${e.message}")
            false
        }
    }

    /**
     * 异步加载应用图标 - 增强版
     */
    private fun loadAppIconAsync(appConfig: AppSearchConfig, holder: AppViewHolder, isInstalled: Boolean) {
        // 1. 优先使用已安装应用的真实图标
        if (isInstalled) {
            try {
                val realIcon = context.packageManager.getApplicationIcon(appConfig.packageName)
                val processedIcon = iconProcessor.processIcon(realIcon, IconProcessor.IconStyle.ROUNDED_SQUARE)
                setAppIcon(holder, processedIcon ?: realIcon, true)
                return
            } catch (e: Exception) {
                // 继续尝试其他方法
            }
        }

        // 2. 检查预加载缓存
        val preloadedIcon = iconPreloader.getPreloadedIcon(appConfig.packageName, appConfig.appName)
        if (preloadedIcon != null) {
            setAppIcon(holder, preloadedIcon, false)
            return
        }

        // 3. 尝试使用预设的高质量图标资源
        val iconResId = getCustomIconResourceId(appConfig.appId)
        if (iconResId != 0) {
            try {
                val customIcon = ContextCompat.getDrawable(context, iconResId)
                if (customIcon != null) {
                    val processedIcon = iconProcessor.processIcon(customIcon, IconProcessor.IconStyle.ROUNDED_SQUARE)
                    setAppIcon(holder, processedIcon ?: customIcon, false)
                    return
                }
            } catch (e: Exception) {
                // 继续尝试其他方法
            }
        }

        // 4. 显示字母图标作为占位符
        val letterIcon = generateLetterIcon(appConfig)
        val processedLetterIcon = iconProcessor.processIcon(letterIcon, IconProcessor.IconStyle.ROUNDED_SQUARE)
        setAppIcon(holder, processedLetterIcon ?: letterIcon, false)

        // 5. 异步加载在线图标 (如果未预加载)
        if (!isInstalled && !iconPreloader.isIconPreloaded(appConfig.packageName, appConfig.appName)) {
            adapterScope.launch {
                // 首先尝试从App Store获取高质量图标 (使用应用搜索网格配置)
                val appStoreIconManager = com.example.aifloatingball.manager.AppStoreIconManager.getInstance(context)

                // 使用suspend函数需要在协程中调用
                try {
                    appStoreIconManager.getAppStoreIcon(
                        packageName = appConfig.packageName,
                        appName = appConfig.appName,
                        displayContext = com.example.aifloatingball.config.IconResolutionConfig.DisplayContext.APP_SEARCH_GRID
                    ) { appStoreIcon ->
                        if (appStoreIcon != null) {
                            // 处理并更新App Store图标
                            val processedIcon = iconProcessor.processIcon(
                                appStoreIcon,
                                IconProcessor.IconStyle.ROUNDED_SQUARE
                            )
                            setAppIcon(holder, processedIcon ?: appStoreIcon, false)
                        } else {
                            // 如果App Store没有找到，使用原有的图标管理器
                            launch {
                                iconManager.getAppIconAsync(
                                    packageName = appConfig.packageName,
                                    appName = appConfig.appName
                                ) { downloadedIcon ->
                                    if (downloadedIcon != null) {
                                        // 处理并更新图标
                                        val processedIcon = iconProcessor.processIcon(
                                            downloadedIcon,
                                            IconProcessor.IconStyle.ROUNDED_SQUARE
                                        )
                                        setAppIcon(holder, processedIcon ?: downloadedIcon, false)
                                    }
                                    // 如果下载失败，保持字母图标
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 如果App Store获取失败，回退到原有的图标管理器
                    iconManager.getAppIconAsync(
                        packageName = appConfig.packageName,
                        appName = appConfig.appName
                    ) { downloadedIcon ->
                        if (downloadedIcon != null) {
                            // 处理并更新图标
                            val processedIcon = iconProcessor.processIcon(
                                downloadedIcon,
                                IconProcessor.IconStyle.ROUNDED_SQUARE
                            )
                            setAppIcon(holder, processedIcon ?: downloadedIcon, false)
                        }
                        // 如果下载失败，保持字母图标
                    }
                }
            }
        }
    }

    /**
     * 设置应用图标到ImageView
     */
    private fun setAppIcon(holder: AppViewHolder, icon: Drawable?, isRealIcon: Boolean) {
        if (icon != null) {
            holder.appIcon.setImageDrawable(icon)
            if (isRealIcon) {
                // 真实图标使用CENTER_CROP，清除背景
                holder.appIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.appIcon.background = null
            } else {
                // 自定义图标使用CENTER_INSIDE
                holder.appIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
                holder.appIcon.background = null
            }
        } else {
            // 使用默认分类图标
            holder.appIcon.setImageResource(android.R.drawable.ic_menu_gallery)
            holder.appIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            holder.appIcon.setBackgroundResource(R.drawable.launcher_icon_background)
        }
    }

    /**
     * 获取自定义应用图标
     */
    private fun getCustomAppIcon(appConfig: AppSearchConfig): Drawable? {
        // 1. 尝试从本地缓存获取图标
        val cachedIcon = getCachedAppIcon(appConfig.packageName)
        if (cachedIcon != null) {
            return cachedIcon
        }

        // 2. 尝试从预设的高质量图标资源获取
        val iconResId = getCustomIconResourceId(appConfig.appId)
        if (iconResId != 0) {
            try {
                return ContextCompat.getDrawable(context, iconResId)
            } catch (e: Exception) {
                // 继续尝试其他方法
            }
        }

        // 3. 尝试从在线图标库获取
        val onlineIcon = getOnlineAppIcon(appConfig)
        if (onlineIcon != null) {
            // 缓存下载的图标
            cacheAppIcon(appConfig.packageName, onlineIcon)
            return onlineIcon
        }

        // 4. 尝试从APK文件提取图标
        val apkIcon = getIconFromAPK(appConfig.packageName)
        if (apkIcon != null) {
            cacheAppIcon(appConfig.packageName, apkIcon)
            return apkIcon
        }

        // 5. 最后使用字母图标
        return generateLetterIcon(appConfig)
    }

    /**
     * 根据应用ID获取自定义图标资源ID
     */
    private fun getCustomIconResourceId(appId: String): Int {
        return when (appId) {
            "qqmusic" -> R.drawable.ic_qqmusic
            "netease_music" -> R.drawable.ic_netease_music
            "eleme" -> R.drawable.ic_eleme
            "douban" -> R.drawable.ic_douban
            "gaode_map" -> R.drawable.ic_gaode_map
            "baidu_map" -> R.drawable.ic_baidu_map
            "quark" -> R.drawable.ic_quark
            "uc_browser" -> R.drawable.ic_uc_browser
            "alipay" -> R.drawable.ic_alipay
            // 对于没有专用图标的应用，返回0，将使用字母图标
            else -> 0
        }
    }

    /**
     * 生成字母图标
     */
    private fun generateLetterIcon(appConfig: AppSearchConfig): Drawable? {
        return try {
            val letter = appConfig.appName.firstOrNull()?.toString()?.uppercase() ?: "A"
            val color = getAppBrandColor(appConfig.appId)

            // 创建bitmap
            val size = 96 // 48dp * 2 for better quality
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 绘制圆形背景
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

            // 绘制字母
            paint.color = Color.WHITE
            paint.textSize = size * 0.5f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD

            val textBounds = Rect()
            paint.getTextBounds(letter, 0, letter.length, textBounds)
            val textY = size / 2f + textBounds.height() / 2f

            canvas.drawText(letter, size / 2f, textY, paint)

            BitmapDrawable(context.resources, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取应用品牌色彩
     */
    private fun getAppBrandColor(appId: String): Int {
        return when (appId) {
            "qqmusic" -> Color.parseColor("#31C27C")
            "netease_music" -> Color.parseColor("#D33A31")
            "eleme" -> Color.parseColor("#0099FF")
            "douban" -> Color.parseColor("#00B51D")
            "gaode_map" -> Color.parseColor("#00A6FB")
            "baidu_map" -> Color.parseColor("#2932E1")
            "quark" -> Color.parseColor("#4A90E2")
            "uc_browser" -> Color.parseColor("#FF6600")
            "alipay" -> Color.parseColor("#00A0E9")
            "wechat_pay" -> Color.parseColor("#07C160")
            "cmb" -> Color.parseColor("#D32F2F")
            "antfortune" -> Color.parseColor("#1976D2")
            "didi" -> Color.parseColor("#FF6600")
            "railway12306" -> Color.parseColor("#1976D2")
            "ctrip" -> Color.parseColor("#0099FF")
            "qunar" -> Color.parseColor("#00C853")
            "hellobike" -> Color.parseColor("#00BCD4")
            "boss" -> Color.parseColor("#00C853")
            "liepin" -> Color.parseColor("#FF6600")
            "zhaopin" -> Color.parseColor("#1976D2")
            "youdao_dict" -> Color.parseColor("#D32F2F")
            "baicizhan" -> Color.parseColor("#00C853")
            "zuoyebang" -> Color.parseColor("#0099FF")
            "yuansouti" -> Color.parseColor("#FF6600")
            "netease_news" -> Color.parseColor("#D33A31")
            else -> Color.parseColor("#757575") // 默认灰色
        }
    }

    /**
     * 从本地缓存获取应用图标
     */
    private fun getCachedAppIcon(packageName: String): Drawable? {
        return try {
            val cacheDir = File(context.cacheDir, "app_icons")
            val iconFile = File(cacheDir, "$packageName.png")
            if (iconFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    BitmapDrawable(context.resources, bitmap)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 缓存应用图标到本地
     */
    private fun cacheAppIcon(packageName: String, drawable: Drawable) {
        try {
            val cacheDir = File(context.cacheDir, "app_icons")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val iconFile = File(cacheDir, "$packageName.png")
            val bitmap = drawableToBitmap(drawable)

            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            // 缓存失败不影响主要功能
        }
    }

    /**
     * 将Drawable转换为Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 96,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 96,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * 从在线图标库获取应用图标
     */
    private fun getOnlineAppIcon(appConfig: AppSearchConfig): Drawable? {
        return try {
            // 方法1: 从Google Play Store获取图标
            val playStoreIcon = getIconFromPlayStore(appConfig.packageName)
            if (playStoreIcon != null) return playStoreIcon

            // 方法2: 从应用市场API获取图标
            val marketIcon = getIconFromAppMarket(appConfig.packageName)
            if (marketIcon != null) return marketIcon

            // 方法3: 从图标数据库获取图标
            val dbIcon = getIconFromIconDatabase(appConfig.appName, appConfig.packageName)
            if (dbIcon != null) return dbIcon

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从Google Play Store获取应用图标
     */
    private fun getIconFromPlayStore(packageName: String): Drawable? {
        return try {
            // 构建Google Play Store图标URL
            val iconUrl = "https://play-lh.googleusercontent.com/apps/$packageName/icon"
            downloadImageFromUrl(iconUrl)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从应用市场API获取图标
     */
    private fun getIconFromAppMarket(packageName: String): Drawable? {
        return try {
            // 可以使用多个应用市场的API
            val urls = listOf(
                "https://api.appstore.com/v1/apps/$packageName/icon",
                "https://api.apkpure.com/v1/apps/$packageName/icon",
                "https://api.coolapk.com/v6/apk/$packageName/icon"
            )

            for (url in urls) {
                val icon = downloadImageFromUrl(url)
                if (icon != null) return icon
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从图标数据库获取图标
     */
    private fun getIconFromIconDatabase(appName: String, packageName: String): Drawable? {
        return try {
            // 使用预定义的图标映射数据库
            val iconMapping = getIconMappingDatabase()
            val iconUrl = iconMapping[packageName] ?: iconMapping[appName.lowercase()]

            if (iconUrl != null) {
                downloadImageFromUrl(iconUrl)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取图标映射数据库
     */
    private fun getIconMappingDatabase(): Map<String, String> {
        return mapOf(
            // 音乐类
            "com.tencent.qqmusic" to "https://cdn.jsdelivr.net/gh/appicons/icons/qqmusic.png",
            "com.netease.cloudmusic" to "https://cdn.jsdelivr.net/gh/appicons/icons/netease-music.png",

            // 生活服务类
            "me.ele" to "https://cdn.jsdelivr.net/gh/appicons/icons/eleme.png",
            "com.douban.frodo" to "https://cdn.jsdelivr.net/gh/appicons/icons/douban.png",

            // 地图导航类
            "com.autonavi.minimap" to "https://cdn.jsdelivr.net/gh/appicons/icons/gaode-map.png",
            "com.baidu.BaiduMap" to "https://cdn.jsdelivr.net/gh/appicons/icons/baidu-map.png",

            // 浏览器类
            "com.quark.browser" to "https://cdn.jsdelivr.net/gh/appicons/icons/quark.png",
            "com.UCMobile" to "https://cdn.jsdelivr.net/gh/appicons/icons/uc-browser.png",

            // 金融类
            "com.eg.android.AlipayGphone" to "https://cdn.jsdelivr.net/gh/appicons/icons/alipay.png",
            "com.tencent.mm" to "https://cdn.jsdelivr.net/gh/appicons/icons/wechat.png",

            // 出行类
            "com.sdu.didi.psnger" to "https://cdn.jsdelivr.net/gh/appicons/icons/didi.png",
            "com.MobileTicket" to "https://cdn.jsdelivr.net/gh/appicons/icons/12306.png",

            // 招聘类
            "com.hpbr.bosszhipin" to "https://cdn.jsdelivr.net/gh/appicons/icons/boss.png",
            "com.liepin.android" to "https://cdn.jsdelivr.net/gh/appicons/icons/liepin.png",

            // 教育类
            "com.youdao.dict" to "https://cdn.jsdelivr.net/gh/appicons/icons/youdao.png",
            "com.baidu.homework" to "https://cdn.jsdelivr.net/gh/appicons/icons/zuoyebang.png"
        )
    }

    /**
     * 从APK文件提取图标
     */
    private fun getIconFromAPK(packageName: String): Drawable? {
        return try {
            // 尝试从系统中获取未安装应用的信息
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES)
            pm.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            // 如果无法直接获取，尝试其他方法
            try {
                // 尝试从APK文件路径获取
                val apkPath = getAPKPath(packageName)
                if (apkPath != null) {
                    extractIconFromAPK(apkPath)
                } else null
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * 获取APK文件路径
     */
    private fun getAPKPath(packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.sourceDir
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从APK文件提取图标
     */
    private fun extractIconFromAPK(apkPath: String): Drawable? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
            packageInfo?.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
                pm.getApplicationIcon(appInfo)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从URL下载图片
     */
    private fun downloadImageFromUrl(url: String): Drawable? {
        return try {
            // 注意：这里应该在后台线程中执行
            // 为了演示，这里使用同步方式，实际应用中应该使用异步
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            connection.connect()

            val inputStream = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap != null) {
                BitmapDrawable(context.resources, bitmap)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 显示应用菜单
     */
    private fun showAppMenu(appConfig: AppSearchConfig, isInstalled: Boolean) {
        val menuItems = mutableListOf<String>()
        menuItems.add("查看应用信息")

        if (isInstalled) {
            menuItems.add("添加到自定义分类")
        }

        // 如果是自定义分类中的应用，添加删除选项
        if (appConfig.category == AppCategory.CUSTOM) {
            menuItems.add("从自定义分类中删除")
        }

        AlertDialog.Builder(context)
            .setTitle(appConfig.appName)
            .setItems(menuItems.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showAppInfo(appConfig, isInstalled)
                    1 -> {
                        if (isInstalled) {
                            addToCustomCategory(appConfig)
                        } else if (appConfig.category == AppCategory.CUSTOM) {
                            removeFromCustomCategory(appConfig)
                        }
                    }
                    2 -> if (appConfig.category == AppCategory.CUSTOM) removeFromCustomCategory(appConfig)
                }
            }
            .show()
    }

    /**
     * 添加应用到自定义分类
     */
    private fun addToCustomCategory(appConfig: AppSearchConfig) {
        try {
            val appSearchSettings = AppSearchSettings.getInstance(context)
            val configs = appSearchSettings.getAppConfigs().toMutableList()

            // 创建自定义应用配置
            val customConfig = appConfig.copy(
                appId = "${appConfig.appId}_custom",
                category = AppCategory.CUSTOM,
                isEnabled = true,
                order = configs.maxOfOrNull { it.order }?.plus(1) ?: 1
            )

            // 检查是否已存在
            val existingIndex = configs.indexOfFirst {
                it.packageName == appConfig.packageName && it.category == AppCategory.CUSTOM
            }

            if (existingIndex == -1) {
                configs.add(customConfig)
                appSearchSettings.saveAppConfigs(configs)
                Toast.makeText(context, "已添加 ${appConfig.appName} 到自定义分类", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "${appConfig.appName} 已在自定义分类中", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从自定义分类中删除应用
     */
    private fun removeFromCustomCategory(appConfig: AppSearchConfig) {
        try {
            AlertDialog.Builder(context)
                .setTitle("删除确认")
                .setMessage("确定要从自定义分类中删除 ${appConfig.appName} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    val appSearchSettings = AppSearchSettings.getInstance(context)
                    val configs = appSearchSettings.getAppConfigs().toMutableList()

                    // 找到并删除对应的自定义配置
                    val wasRemoved = configs.removeAll {
                        it.appId == appConfig.appId && it.category == AppCategory.CUSTOM
                    }

                    if (wasRemoved) {
                        appSearchSettings.saveAppConfigs(configs)
                        Toast.makeText(context, "已从自定义分类中删除 ${appConfig.appName}", Toast.LENGTH_SHORT).show()

                        // 通知适配器更新数据
                        updateAppConfigs(configs.filter { it.category == AppCategory.CUSTOM })
                    } else {
                        Toast.makeText(context, "删除失败，未找到对应的配置", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示安装提示弹窗
     */
    private fun showInstallDialog(appConfig: AppSearchConfig) {
        try {
            // 确保context是Activity类型
            val activityContext = when (context) {
                is android.app.Activity -> context
                is androidx.appcompat.app.AppCompatActivity -> context
                else -> {
                    // 如果不是Activity context，直接显示Toast
                    Toast.makeText(context, "${appConfig.appName} 尚未安装", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            AlertDialog.Builder(activityContext)
                .setTitle("应用未安装")
                .setMessage("${appConfig.appName} 尚未安装，是否前往应用商店安装？")
                .setPositiveButton("去安装") { _, _ ->
                    try {
                        // 尝试打开应用商店
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("market://details?id=${appConfig.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            // 如果应用商店不可用，尝试打开浏览器
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://play.google.com/store/apps/details?id=${appConfig.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "无法打开应用商店", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            // 如果对话框创建失败，显示简单的Toast
            Toast.makeText(context, "${appConfig.appName} 尚未安装，请手动安装", Toast.LENGTH_LONG).show()
            android.util.Log.e("AppSearchGridAdapter", "显示安装对话框失败", e)
        }
    }

    /**
     * 显示应用信息
     */
    private fun showAppInfo(appConfig: AppSearchConfig, isInstalled: Boolean) {
        val message = buildString {
            append("应用名称: ${appConfig.appName}\n")
            append("包名: ${appConfig.packageName}\n")
            append("分类: ${appConfig.category.displayName}\n")
            append("状态: ${if (isInstalled) "已安装" else "未安装"}\n")
            if (appConfig.description.isNotEmpty()) {
                append("描述: ${appConfig.description}")
            }
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
