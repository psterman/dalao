package com.example.aifloatingball.utils

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object BitmapUtils {
    fun compressBitmap(bitmap: Bitmap, quality: Int = 100): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }
} 