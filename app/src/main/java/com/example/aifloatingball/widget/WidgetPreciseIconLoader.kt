package com.example.aifloatingball.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.RemoteViews
import com.example.aifloatingball.manager.PreciseIconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 小组件精准图标加载器
 * 专门为小组件提供高质量图标加载服务
 */
class WidgetPreciseIconLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "WidgetPreciseIconLoader"
        private const val CACHE_DIR = "widget_icons"
        private const val TIMEOUT = 5000
    }
    
    private val preciseIconManager = PreciseIconManager(context)
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * 为小组件加载精准图标
     */
    fun loadPreciseIconForWidget(
        packageName: String,
        appName: String,
        remoteViews: RemoteViews,
        iconViewId: Int,
        defaultIconRes: Int,
        onComplete: (() -> Unit)? = null
    ) {
        Log.d(TAG, "开始为小组件加载图标: $appName ($packageName)")
        
        // 1. 检查缓存
        val cachedIcon = getCachedIcon(packageName, appName)
        if (cachedIcon != null) {
            Log.d(TAG, "使用缓存图标: $appName")
            remoteViews.setImageViewBitmap(iconViewId, cachedIcon)
            onComplete?.invoke()
            return
        }
        
        // 2. 设置默认图标
        remoteViews.setImageViewResource(iconViewId, defaultIconRes)
        
        // 3. 异步加载精准图标
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appType = preciseIconManager.getAppType(packageName, appName)
                val drawable = preciseIconManager.getPreciseIcon(packageName, appName, appType)
                
                if (drawable != null) {
                    val bitmap = drawableToBitmap(drawable)
                    if (bitmap != null) {
                        // 缓存图标
                        cacheIcon(packageName, appName, bitmap)
                        
                        // 更新小组件
                        withContext(Dispatchers.Main) {
                            remoteViews.setImageViewBitmap(iconViewId, bitmap)
                            updateAllWidgets()
                            onComplete?.invoke()
                            Log.d(TAG, "成功加载精准图标: $appName")
                        }
                    }
                } else {
                    Log.w(TAG, "未能获取精准图标: $appName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载精准图标失败: $appName", e)
            }
        }
    }
    
    /**
     * 批量加载图标（用于小组件配置）
     */
    fun batchLoadIcons(
        apps: List<Pair<String, String>>, // packageName, appName
        onIconLoaded: (String, String, Bitmap?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            for ((packageName, appName) in apps) {
                try {
                    // 检查缓存
                    val cachedIcon = getCachedIcon(packageName, appName)
                    if (cachedIcon != null) {
                        withContext(Dispatchers.Main) {
                            onIconLoaded(packageName, appName, cachedIcon)
                        }
                        continue
                    }
                    
                    // 加载新图标
                    val appType = preciseIconManager.getAppType(packageName, appName)
                    val drawable = preciseIconManager.getPreciseIcon(packageName, appName, appType)
                    
                    val bitmap = if (drawable != null) {
                        val bmp = drawableToBitmap(drawable)
                        if (bmp != null) {
                            cacheIcon(packageName, appName, bmp)
                            bmp
                        } else null
                    } else null
                    
                    withContext(Dispatchers.Main) {
                        onIconLoaded(packageName, appName, bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "批量加载图标失败: $appName", e)
                    withContext(Dispatchers.Main) {
                        onIconLoaded(packageName, appName, null)
                    }
                }
            }
        }
    }
    
    /**
     * 预加载常用图标
     */
    fun preloadCommonIcons() {
        val commonApps = listOf(
            // AI应用
            "com.deepseek.chat" to "DeepSeek",
            "com.moonshot.kimi" to "Kimi",
            "com.google.android.apps.bard" to "Gemini",
            "com.zhipu.chatglm" to "智谱",
            "com.anthropic.claude" to "Claude",
            
            // 常规应用
            "com.xingin.xhs" to "小红书",
            "com.zhihu.android" to "知乎",
            "com.ss.android.ugc.aweme" to "抖音",
            "com.sankuai.meituan" to "美团",
            "com.sina.weibo" to "微博",
            
            // 搜索引擎
            "google" to "Google",
            "baidu" to "百度",
            "bing" to "Bing"
        )
        
        batchLoadIcons(commonApps) { packageName, appName, bitmap ->
            if (bitmap != null) {
                Log.d(TAG, "预加载图标成功: $appName")
            }
        }
    }
    
    /**
     * 获取缓存图标
     */
    private fun getCachedIcon(packageName: String, appName: String): Bitmap? {
        return try {
            val cacheKey = generateCacheKey(packageName, appName)
            val cacheFile = File(cacheDir, "$cacheKey.png")
            
            if (cacheFile.exists()) {
                BitmapFactory.decodeFile(cacheFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取缓存图标失败", e)
            null
        }
    }
    
    /**
     * 缓存图标
     */
    private fun cacheIcon(packageName: String, appName: String, bitmap: Bitmap) {
        try {
            val cacheKey = generateCacheKey(packageName, appName)
            val cacheFile = File(cacheDir, "$cacheKey.png")
            
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Log.d(TAG, "图标缓存成功: $appName")
        } catch (e: Exception) {
            Log.e(TAG, "缓存图标失败", e)
        }
    }
    
    /**
     * 生成缓存键
     */
    private fun generateCacheKey(packageName: String, appName: String): String {
        return "${packageName}_${appName}".replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }
    
    /**
     * Drawable转Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
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
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Drawable转Bitmap失败", e)
            null
        }
    }
    
    /**
     * 创建字母图标（备用方案）
     */
    private fun createLetterIcon(appName: String, size: Int = 96): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 背景色
        val colors = listOf(
            0xFF4285F4.toInt(), // Google Blue
            0xFF34A853.toInt(), // Google Green  
            0xFFEA4335.toInt(), // Google Red
            0xFFFBBC05.toInt(), // Google Yellow
            0xFF9C27B0.toInt(), // Purple
            0xFF00BCD4.toInt()  // Cyan
        )
        val bgColor = colors[appName.hashCode().rem(colors.size).let { if (it < 0) -it else it }]
        canvas.drawColor(bgColor)
        
        // 文字
        val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.4f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        val letter = appName.firstOrNull()?.toString()?.uppercase() ?: "A"
        val textBounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, textBounds)
        
        canvas.drawText(
            letter,
            size / 2f,
            size / 2f + textBounds.height() / 2f,
            paint
        )
        
        return bitmap
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".png")) {
                    file.delete()
                }
            }
            Log.d(TAG, "缓存清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long {
        return try {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 更新所有小组件实例
     */
    private fun updateAllWidgets() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // 更新CustomizableWidgetProvider
            val customizableComponent = ComponentName(context, "com.example.dalao.widget.CustomizableWidgetProvider")
            val customizableIds = appWidgetManager.getAppWidgetIds(customizableComponent)
            if (customizableIds.isNotEmpty()) {
                val updateIntent = android.content.Intent(context, Class.forName("com.example.dalao.widget.CustomizableWidgetProvider"))
                updateIntent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, customizableIds)
                context.sendBroadcast(updateIntent)
            }

            // 更新SearchWidgetProvider
            val searchComponent = ComponentName(context, "com.example.aifloatingball.widget.SearchWidgetProvider")
            val searchIds = appWidgetManager.getAppWidgetIds(searchComponent)
            if (searchIds.isNotEmpty()) {
                val updateIntent = android.content.Intent(context, Class.forName("com.example.aifloatingball.widget.SearchWidgetProvider"))
                updateIntent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, searchIds)
                context.sendBroadcast(updateIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新小组件失败", e)
        }
    }
}
