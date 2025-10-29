package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
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
     * 从drawable资源加载Bitmap
     */
    fun loadBitmapFromResource(context: Context, resId: Int): Bitmap? {
        return try {
            BitmapFactory.decodeResource(context.resources, resId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from resource $resId", e)
            null
        }
    }
} 