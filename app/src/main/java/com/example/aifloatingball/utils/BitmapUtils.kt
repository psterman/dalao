package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

object BitmapUtils {
    private const val TAG = "BitmapUtils"
    
    fun compressBitmap(bitmap: Bitmap, quality: Int = 100): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }
    
    /**
     * 将多个Bitmap组合成一个头像
     * @param bitmaps 要组合的bitmap列表
     * @param size 输出bitmap的大小
     */
    fun combineAvatars(bitmaps: List<Bitmap>, size: Int = 128): Bitmap {
        if (bitmaps.isEmpty()) {
            // 如果没有头像，返回一个默认图标
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }
        
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        when (bitmaps.size) {
            1 -> {
                // 单个头像：直接居中显示
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val srcRect = Rect(0, 0, bitmaps[0].width, bitmaps[0].height)
                val dstRect = Rect(0, 0, size, size)
                canvas.drawBitmap(bitmaps[0], srcRect, dstRect, paint)
            }
            2 -> {
                // 两个头像：左右各一个
                val halfSize = size / 2
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                
                // 左边
                val leftSrc = Rect(0, 0, bitmaps[0].width, bitmaps[0].height)
                val leftDst = Rect(0, 0, halfSize, size)
                canvas.drawBitmap(bitmaps[0], leftSrc, leftDst, paint)
                
                // 右边
                if (bitmaps.size > 1) {
                    val rightSrc = Rect(0, 0, bitmaps[1].width, bitmaps[1].height)
                    val rightDst = Rect(halfSize, 0, size, size)
                    canvas.drawBitmap(bitmaps[1], rightSrc, rightDst, paint)
                }
            }
            3, 4 -> {
                // 3-4个头像：2x2网格
                val halfSize = size / 2
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                
                // 左上
                val topLeftSrc = Rect(0, 0, bitmaps[0].width, bitmaps[0].height)
                val topLeftDst = Rect(0, 0, halfSize, halfSize)
                canvas.drawBitmap(bitmaps[0], topLeftSrc, topLeftDst, paint)
                
                // 右上
                if (bitmaps.size > 1) {
                    val topRightSrc = Rect(0, 0, bitmaps[1].width, bitmaps[1].height)
                    val topRightDst = Rect(halfSize, 0, size, halfSize)
                    canvas.drawBitmap(bitmaps[1], topRightSrc, topRightDst, paint)
                }
                
                // 左下
                if (bitmaps.size > 2) {
                    val bottomLeftSrc = Rect(0, 0, bitmaps[2].width, bitmaps[2].height)
                    val bottomLeftDst = Rect(0, halfSize, halfSize, size)
                    canvas.drawBitmap(bitmaps[2], bottomLeftSrc, bottomLeftDst, paint)
                }
                
                // 右下
                if (bitmaps.size > 3) {
                    val bottomRightSrc = Rect(0, 0, bitmaps[3].width, bitmaps[3].height)
                    val bottomRightDst = Rect(halfSize, halfSize, size, size)
                    canvas.drawBitmap(bitmaps[3], bottomRightSrc, bottomRightDst, paint)
                }
            }
            else -> {
                // 超过4个：显示前4个
                val halfSize = size / 2
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                
                for (i in 0 until minOf(4, bitmaps.size)) {
                    val src = Rect(0, 0, bitmaps[i].width, bitmaps[i].height)
                    val row = i / 2
                    val col = i % 2
                    val dst = Rect(col * halfSize, row * halfSize, (col + 1) * halfSize, (row + 1) * halfSize)
                    canvas.drawBitmap(bitmaps[i], src, dst, paint)
                }
            }
        }
        
        return output
    }
    
    /**
     * 从资源加载Bitmap，兼容VectorDrawable与BitmapDrawable。
     */
    fun loadBitmapFromResource(context: Context, resId: Int, fallbackSize: Int = 144): Bitmap? {
        return try {
            // 优先：若是位图资源，直接解码
            val direct = BitmapFactory.decodeResource(context.resources, resId)
            if (direct != null) return direct

            // 兼容：VectorDrawable/其他Drawable 转 Bitmap
            val drawable: Drawable = ContextCompat.getDrawable(context, resId) ?: return null
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else fallbackSize
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else fallbackSize
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap (vector compatible) from res $resId", e)
            null
        }
    }

    /**
     * 组合头像（通用宫格版），支持 1–9 个。
     * - 保持原图纵横比，不拉伸；按 Fit-Center 规则缩放到单元格内。
     * - 1：居中显示（带少许边距）
     * - 2：左右并列（1×2）
     * - 3–4：2×2；5–9：3×3（最多前9张）
     */
    fun combineAvatarsGrid(bitmaps: List<Bitmap>, size: Int = 128): Bitmap {
        if (bitmaps.isEmpty()) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val count = bitmaps.size
        if (count == 1) {
            val padding = (size * 0.08f).toInt() // 单图留一点边
            val cell = Rect(padding, padding, size - padding, size - padding)
            val dst = fitRect(cell, bitmaps[0].width, bitmaps[0].height)
            val src = Rect(0, 0, bitmaps[0].width, bitmaps[0].height)
            canvas.drawBitmap(bitmaps[0], src, dst, paint)
            return output
        }

        if (count == 2) {
            val half = size / 2
            val leftCell = Rect(0, 0, half, size)
            val rightCell = Rect(half, 0, size, size)
            val leftDst = fitRect(leftCell, bitmaps[0].width, bitmaps[0].height)
            val rightDst = fitRect(rightCell, bitmaps[1].width, bitmaps[1].height)
            canvas.drawBitmap(bitmaps[0], Rect(0,0,bitmaps[0].width, bitmaps[0].height), leftDst, paint)
            canvas.drawBitmap(bitmaps[1], Rect(0,0,bitmaps[1].width, bitmaps[1].height), rightDst, paint)
            return output
        }

        val grid = if (count <= 4) 2 else 3
        val cell = size / grid
        val gap = (size * 0.04f).toInt().coerceAtLeast(2)
        val maxDraw = minOf(count, grid * grid)

        for (i in 0 until maxDraw) {
            val row = i / grid
            val col = i % grid
            val left = col * cell + gap / 2
            val top = row * cell + gap / 2
            val right = (col + 1) * cell - gap / 2
            val bottom = (row + 1) * cell - gap / 2

            val cellRect = Rect(left, top, right, bottom)
            val dst = fitRect(cellRect, bitmaps[i].width, bitmaps[i].height)
            val src = Rect(0, 0, bitmaps[i].width, bitmaps[i].height)
            canvas.drawBitmap(bitmaps[i], src, dst, paint)
        }

        return output
    }

    /**
     * 将给定宽高的图片等比缩放到指定单元格内（Fit-Center）。
     */
    private fun fitRect(cell: Rect, bmpW: Int, bmpH: Int): Rect {
        val cellW = (cell.right - cell.left).toFloat()
        val cellH = (cell.bottom - cell.top).toFloat()
        if (bmpW <= 0 || bmpH <= 0 || cellW <= 0 || cellH <= 0) return cell

        val scale = minOf(cellW / bmpW, cellH / bmpH)
        val newW = (bmpW * scale).toInt()
        val newH = (bmpH * scale).toInt()
        val left = cell.left + ((cellW - newW) / 2f).toInt()
        val top = cell.top + ((cellH - newH) / 2f).toInt()
        return Rect(left, top, left + newW, top + newH)
    }
} 
