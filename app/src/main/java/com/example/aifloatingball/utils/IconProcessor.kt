package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.min

/**
 * 图标处理工具类
 * 用于统一图标大小、外轮廓造型和视觉效果
 */
class IconProcessor(private val context: Context) {

    companion object {
        // 根据实际显示需求优化尺寸
        private const val STANDARD_SIZE = 144 // 标准图标尺寸 (48dp * 3 for high density)
        private const val WIDGET_SIZE = 96    // 小组件图标尺寸 (32dp * 3)
        private const val TOOLBAR_SIZE = 72   // 工具栏图标尺寸 (24dp * 3)
        private const val CORNER_RADIUS = 16f // 圆角半径
        private const val SHADOW_RADIUS = 4f // 阴影半径
        private const val SHADOW_OFFSET = 2f // 阴影偏移
    }

    /**
     * 检查是否为暗色模式
     */
    private fun isDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 获取适合当前主题的背景色
     */
    private fun getBackgroundColor(): Int {
        return if (isDarkMode()) {
            Color.TRANSPARENT // 暗色模式下使用透明背景，避免白边
        } else {
            Color.WHITE // 浅色模式下使用白色背景
        }
    }

    /**
     * 获取适合当前主题的边框色
     */
    private fun getBorderColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#40FFFFFF") // 暗色模式下使用半透明白色边框
        } else {
            Color.parseColor("#E0E0E0") // 浅色模式下使用浅灰色边框
        }
    }

    /**
     * 处理图标，统一大小和外观
     */
    fun processIcon(drawable: Drawable?, iconStyle: IconStyle = IconStyle.ROUNDED_SQUARE): Drawable? {
        if (drawable == null) return null

        return try {
            val originalBitmap = drawable.toBitmap(STANDARD_SIZE, STANDARD_SIZE, Bitmap.Config.ARGB_8888)
            val processedBitmap = when (iconStyle) {
                IconStyle.CIRCLE -> createCircleIcon(originalBitmap)
                IconStyle.ROUNDED_SQUARE -> createRoundedSquareIcon(originalBitmap)
                IconStyle.SQUARE -> createSquareIcon(originalBitmap)
                IconStyle.IOS_STYLE -> createiOSStyleIcon(originalBitmap)
            }

            BitmapDrawable(context.resources, processedBitmap)
        } catch (e: Exception) {
            drawable // 如果处理失败，返回原图标
        }
    }
    
    /**
     * 创建圆形图标
     */
    private fun createCircleIcon(originalBitmap: Bitmap): Bitmap {
        val size = STANDARD_SIZE
        val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // 创建圆形路径
        val path = Path()
        path.addCircle(size / 2f, size / 2f, size / 2f - 2, Path.Direction.CW)

        // 裁剪并绘制
        canvas.clipPath(path)

        // 绘制背景（根据主题调整）
        val backgroundColor = getBackgroundColor()
        if (backgroundColor != Color.TRANSPARENT) {
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            backgroundPaint.color = backgroundColor
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, backgroundPaint)
        }

        // 绘制原图标
        val srcRect = Rect(0, 0, originalBitmap.width, originalBitmap.height)
        val dstRect = Rect(4, 4, size - 4, size - 4) // 留出边距
        canvas.drawBitmap(originalBitmap, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG))

        // 添加边框（根据主题调整）
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = if (isDarkMode()) 1f else 2f // 暗色模式下使用更细的边框
        borderPaint.color = getBorderColor()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, borderPaint)

        return outputBitmap
    }
    
    /**
     * 创建圆角方形图标
     */
    private fun createRoundedSquareIcon(originalBitmap: Bitmap): Bitmap {
        val size = STANDARD_SIZE
        val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // 创建圆角矩形路径
        val path = Path()
        val rectF = RectF(2f, 2f, size - 2f, size - 2f)
        path.addRoundRect(rectF, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW)

        // 裁剪并绘制
        canvas.clipPath(path)

        // 绘制背景（根据主题调整）
        val backgroundColor = getBackgroundColor()
        if (backgroundColor != Color.TRANSPARENT) {
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            backgroundPaint.color = backgroundColor
            canvas.drawRoundRect(rectF, CORNER_RADIUS, CORNER_RADIUS, backgroundPaint)
        }

        // 绘制原图标
        val srcRect = Rect(0, 0, originalBitmap.width, originalBitmap.height)
        val dstRect = Rect(6, 6, size - 6, size - 6) // 留出边距
        canvas.drawBitmap(originalBitmap, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG))

        // 添加边框（根据主题调整）
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = if (isDarkMode()) 0.5f else 1f // 暗色模式下使用更细的边框
        borderPaint.color = getBorderColor()
        canvas.drawRoundRect(rectF, CORNER_RADIUS, CORNER_RADIUS, borderPaint)

        return outputBitmap
    }
    
    /**
     * 创建方形图标
     */
    private fun createSquareIcon(originalBitmap: Bitmap): Bitmap {
        val size = STANDARD_SIZE
        val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // 绘制背景（根据主题调整）
        val backgroundColor = getBackgroundColor()
        if (backgroundColor != Color.TRANSPARENT) {
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            backgroundPaint.color = backgroundColor
            canvas.drawRect(2f, 2f, size - 2f, size - 2f, backgroundPaint)
        }

        // 绘制原图标
        val srcRect = Rect(0, 0, originalBitmap.width, originalBitmap.height)
        val dstRect = Rect(6, 6, size - 6, size - 6)
        canvas.drawBitmap(originalBitmap, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG))

        // 添加边框（根据主题调整）
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = if (isDarkMode()) 0.5f else 1f
        borderPaint.color = getBorderColor()
        canvas.drawRect(2f, 2f, size - 2f, size - 2f, borderPaint)

        return outputBitmap
    }
    
    /**
     * 创建iOS风格图标 (带阴影和光泽效果)
     */
    private fun createiOSStyleIcon(originalBitmap: Bitmap): Bitmap {
        val size = STANDARD_SIZE
        val outputBitmap = Bitmap.createBitmap(size + 8, size + 8, Bitmap.Config.ARGB_8888) // 为阴影留出空间
        val canvas = Canvas(outputBitmap)
        
        // 绘制阴影（暗色模式下减少阴影）
        if (!isDarkMode()) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            shadowPaint.color = Color.parseColor("#40000000")
            shadowPaint.maskFilter = BlurMaskFilter(SHADOW_RADIUS, BlurMaskFilter.Blur.NORMAL)

            val shadowRect = RectF(
                4f + SHADOW_OFFSET,
                4f + SHADOW_OFFSET,
                size + SHADOW_OFFSET,
                size + SHADOW_OFFSET
            )
            canvas.drawRoundRect(shadowRect, CORNER_RADIUS, CORNER_RADIUS, shadowPaint)
        }

        // 绘制主体
        val mainRect = RectF(4f, 4f, size.toFloat(), size.toFloat())

        // 背景渐变（根据主题调整）
        val gradient = if (isDarkMode()) {
            // 暗色模式下使用透明渐变
            LinearGradient(
                0f, 4f, 0f, size.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        } else {
            // 浅色模式下使用白色渐变
            LinearGradient(
                0f, 4f, 0f, size.toFloat(),
                intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F5F5F5")),
                null,
                Shader.TileMode.CLAMP
            )
        }

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backgroundPaint.shader = gradient
        if (!isDarkMode()) {
            canvas.drawRoundRect(mainRect, CORNER_RADIUS, CORNER_RADIUS, backgroundPaint)
        }
        
        // 绘制原图标
        val srcRect = Rect(0, 0, originalBitmap.width, originalBitmap.height)
        val dstRect = Rect(8, 8, size - 4, size - 4)
        canvas.drawBitmap(originalBitmap, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG))
        
        // 添加高光效果（根据主题调整）
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val highlightColor = if (isDarkMode()) {
            Color.parseColor("#20FFFFFF") // 暗色模式下使用更淡的高光
        } else {
            Color.parseColor("#40FFFFFF")
        }
        val highlightGradient = LinearGradient(
            0f, 4f, 0f, size / 2f,
            intArrayOf(highlightColor, Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        highlightPaint.shader = highlightGradient
        canvas.drawRoundRect(mainRect, CORNER_RADIUS, CORNER_RADIUS, highlightPaint)

        // 边框（根据主题调整）
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = if (isDarkMode()) 0.5f else 1f
        borderPaint.color = getBorderColor()
        canvas.drawRoundRect(mainRect, CORNER_RADIUS, CORNER_RADIUS, borderPaint)
        
        return outputBitmap
    }
    
    /**
     * 智能裁剪图标 - 去除多余的透明边距
     */
    fun smartCropIcon(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var left = width
        var right = 0
        var top = height
        var bottom = 0
        
        // 找到非透明像素的边界
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                if (Color.alpha(pixel) > 50) { // 非透明像素
                    left = min(left, x)
                    right = maxOf(right, x)
                    top = min(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        
        // 如果没有找到非透明像素，返回原图
        if (left >= right || top >= bottom) {
            return bitmap
        }
        
        // 添加一些边距
        val margin = min(width, height) / 20
        left = maxOf(0, left - margin)
        right = min(width - 1, right + margin)
        top = maxOf(0, top - margin)
        bottom = min(height - 1, bottom + margin)
        
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }
    
    /**
     * 图标样式枚举
     */
    enum class IconStyle {
        CIRCLE,          // 圆形
        ROUNDED_SQUARE,  // 圆角方形
        SQUARE,          // 方形
        IOS_STYLE        // iOS风格 (带阴影和光泽)
    }
}
