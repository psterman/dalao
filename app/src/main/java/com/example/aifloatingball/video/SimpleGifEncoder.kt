package com.example.aifloatingball.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import java.io.FileOutputStream
import java.util.zip.Deflater

/**
 * 简单的GIF编码器
 * 用于将Bitmap序列编码为GIF文件
 * 使用简化的GIF格式实现
 * 
 * @author AI Floating Ball
 */
class SimpleGifEncoder {
    companion object {
        private const val TAG = "SimpleGifEncoder"
    }
    
    private var out: FileOutputStream? = null
    private var width = 0
    private var height = 0
    private var delay = 10 // 帧延迟（1/100秒）
    private var repeat = 0 // 循环次数（0表示不循环）
    private var started = false
    private val frames = mutableListOf<Bitmap>()
    
    /**
     * 开始GIF编码
     */
    fun start(filePath: String): Boolean {
        return try {
            out = FileOutputStream(filePath)
            started = false
            frames.clear()
            true
        } catch (e: Exception) {
            Log.e(TAG, "创建GIF文件失败", e)
            false
        }
    }
    
    /**
     * 设置帧延迟
     */
    fun setDelay(ms: Int) {
        delay = (ms / 10).coerceAtLeast(1) // GIF以1/100秒为单位，最小1
    }
    
    /**
     * 设置循环次数
     */
    fun setRepeat(repeat: Int) {
        this.repeat = repeat
    }
    
    /**
     * 设置质量（暂不使用）
     */
    fun setQuality(quality: Int) {
        // 质量参数暂不使用
    }
    
    /**
     * 添加帧
     */
    fun addFrame(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled) {
            return false
        }
        
        try {
            if (!started) {
                width = bitmap.width
                height = bitmap.height
                started = true
            }
            
            // 如果尺寸不匹配，调整大小
            val frame = if (bitmap.width != width || bitmap.height != height) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            }
            
            frames.add(frame)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "添加帧失败", e)
            return false
        }
    }
    
    /**
     * 完成GIF编码
     */
    fun finish() {
        try {
            if (frames.isEmpty()) {
                Log.w(TAG, "没有帧数据，无法生成GIF")
                return
            }
            
            // 写入GIF头部
            writeHeader()
            
            // 写入每一帧
            val frameCount = frames.size
            for ((index, frame) in frames.withIndex()) {
                writeImage(frame, index == 0)
            }
            
            // GIF结束符
            out?.write(0x3b)
            out?.flush()
            out?.close()
            out = null
            
            // 清理帧数据
            frames.forEach { it.recycle() }
            frames.clear()
            
            Log.d(TAG, "GIF编码完成，共${frameCount}帧")
        } catch (e: Exception) {
            Log.e(TAG, "完成GIF编码失败", e)
            // 清理帧数据
            frames.forEach { it.recycle() }
            frames.clear()
        }
    }
    
    /**
     * 写入GIF头部
     */
    private fun writeHeader() {
        try {
            // GIF签名 "GIF89a"
            out?.write("GIF89a".toByteArray())
            
            // 逻辑屏幕描述符
            writeShort(width)
            writeShort(height)
            out?.write(0xf7) // 全局颜色表标志：有颜色表，256色，颜色分辨率8位
            out?.write(0) // 背景色索引
            out?.write(0) // 像素宽高比
            
            // 全局颜色表（256色，RGB各1字节）
            writeGlobalColorTable()
            
            // 应用扩展（NETSCAPE2.0，用于循环）
            if (repeat >= 0) {
                out?.write(0x21) // 扩展引入符
                out?.write(0xff) // 应用扩展标签
                out?.write(11) // 应用标识符长度
                out?.write("NETSCAPE2.0".toByteArray())
                out?.write(3) // 数据子块大小
                out?.write(1)
                writeShort(repeat)
                out?.write(0) // 块终止符
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入GIF头部失败", e)
            throw e
        }
    }
    
    /**
     * 写入全局颜色表（简化版：使用固定256色调色板）
     */
    private fun writeGlobalColorTable() {
        try {
            // 创建256色调色板（6x6x6 RGB立方体 + 灰度）
            val colorTable = mutableListOf<Int>()
            
            // 添加基本颜色
            for (r in 0..5) {
                for (g in 0..5) {
                    for (b in 0..5) {
                        if (colorTable.size < 256) {
                            val color = ((r * 51) shl 16) or ((g * 51) shl 8) or (b * 51)
                            colorTable.add(color)
                        }
                    }
                }
            }
            
            // 填充到256色
            while (colorTable.size < 256) {
                val gray = (colorTable.size * 255 / 256)
                colorTable.add((gray shl 16) or (gray shl 8) or gray)
            }
            
            // 写入颜色表
            for (i in 0 until 256) {
                val color = colorTable[i]
                out?.write((color shr 16) and 0xFF)
                out?.write((color shr 8) and 0xFF)
                out?.write(color and 0xFF)
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入全局颜色表失败", e)
            throw e
        }
    }
    
    /**
     * 写入图像数据
     */
    private fun writeImage(bitmap: Bitmap, isFirstFrame: Boolean) {
        try {
            // 图形控制扩展（帧延迟）
            out?.write(0x21) // 扩展引入符
            out?.write(0xf9) // 图形控制扩展标签
            out?.write(4) // 块大小
            out?.write(if (isFirstFrame) 0x04 else 0x05) // 处置方法：保留前一帧或替换
            writeShort(delay)
            out?.write(0) // 透明色索引（无透明）
            out?.write(0) // 块终止符
            
            // 图像分隔符
            out?.write(0x2c) // 图像分隔符
            writeShort(0) // 左边界
            writeShort(0) // 上边界
            writeShort(bitmap.width)
            writeShort(bitmap.height)
            out?.write(0) // 无局部颜色表，不使用交错
            
            // 图像数据
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            // 量化像素到颜色表
            val indices = quantizePixels(pixels)
            
            // 写入LZW编码数据
            writeLZWData(indices)
            
        } catch (e: Exception) {
            Log.e(TAG, "写入图像数据失败", e)
            throw e
        }
    }
    
    /**
     * 量化像素到颜色表索引
     */
    private fun quantizePixels(pixels: IntArray): ByteArray {
        val indices = ByteArray(pixels.size)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // 量化到6x6x6调色板
            val rIndex = (r / 51).coerceIn(0, 5)
            val gIndex = (g / 51).coerceIn(0, 5)
            val bIndex = (b / 51).coerceIn(0, 5)
            
            // 计算在调色板中的索引（6x6x6 = 216色）
            val index = (rIndex * 36 + gIndex * 6 + bIndex).coerceIn(0, 255)
            indices[i] = index.toByte()
        }
        
        return indices
    }
    
    /**
     * 写入LZW编码数据（简化版：使用基本LZW压缩）
     */
    private fun writeLZWData(indices: ByteArray) {
        try {
            // LZW最小代码大小（8位，对应256色）
            val minCodeSize = 8
            out?.write(minCodeSize)
            
            // 简化的LZW编码实现
            val lzwEncoder = LZWEncoder(minCodeSize, indices)
            lzwEncoder.encode(out!!)
            
        } catch (e: Exception) {
            Log.e(TAG, "写入LZW数据失败", e)
            throw e
        }
    }
    
    /**
     * 简化的LZW编码器
     */
    private class LZWEncoder(private val minCodeSize: Int, private val data: ByteArray) {
        private var bitBuffer = 0
        private var bitCount = 0
        
        fun encode(out: FileOutputStream) {
            val clearCode = 1 shl minCodeSize
            val endCode = clearCode + 1
            val maxCode = 4095 // 最大代码值
            
            var codeSize = minCodeSize + 1
            var nextCode = endCode + 1
            
            // 写入清除码
            writeCode(out, clearCode, codeSize)
            
            // 简化的字典编码
            val dictionary = mutableMapOf<String, Int>()
            var currentString = ""
            
            for (byte in data) {
                val char = byte.toInt() and 0xFF
                val charStr = char.toChar().toString()
                val newString = currentString + charStr
                
                if (dictionary.containsKey(newString) || newString.length == 1) {
                    currentString = newString
                } else {
                    // 输出当前字符串的代码
                    val code = if (currentString.length == 1) {
                        currentString[0].code
                    } else {
                        dictionary[currentString] ?: currentString[0].code
                    }
                    
                    writeCode(out, code, codeSize)
                    
                    // 添加到字典
                    if (nextCode <= maxCode && currentString.isNotEmpty()) {
                        dictionary[newString] = nextCode++
                        if (nextCode >= (1 shl codeSize) && codeSize < 12) {
                            codeSize++
                        }
                    }
                    
                    currentString = charStr
                }
            }
            
            // 输出最后一个字符串
            if (currentString.isNotEmpty()) {
                val code = if (currentString.length == 1) {
                    currentString[0].code
                } else {
                    dictionary[currentString] ?: currentString[0].code
                }
                writeCode(out, code, codeSize)
            }
            
            // 写入结束码
            writeCode(out, endCode, codeSize)
            
            // 刷新剩余的位
            flushBits(out)
            
            // 块终止符
            out.write(0)
        }
        
        private fun writeCode(out: FileOutputStream, code: Int, codeSize: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            
            while (bitCount >= 8) {
                out.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer shr 8
                bitCount -= 8
            }
        }
        
        private fun flushBits(out: FileOutputStream) {
            if (bitCount > 0) {
                out.write(bitBuffer and 0xFF)
                bitBuffer = 0
                bitCount = 0
            }
        }
    }
    
    /**
     * 写入16位整数（小端序）
     */
    private fun writeShort(value: Int) {
        out?.write(value and 0xFF)
        out?.write((value shr 8) and 0xFF)
    }
}

