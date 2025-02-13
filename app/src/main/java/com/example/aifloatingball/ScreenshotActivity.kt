package com.example.aifloatingball

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotActivity : Activity() {
    companion object {
        private const val REQUEST_SCREENSHOT = 1001
        private const val VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        const val ACTION_SCREENSHOT_COMPLETED = "com.example.aifloatingball.SCREENSHOT_COMPLETED"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private val handler = Handler(Looper.getMainLooper())

    private val mediaProjectionCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d("ScreenshotActivity", "MediaProjection stopped")
                cleanupProjection()
                finish()
            }
        }
    } else null

    private val virtualDisplayCallback = object : VirtualDisplay.Callback() {
        override fun onStopped() {
            Log.d("ScreenshotActivity", "VirtualDisplay stopped")
            cleanupProjection()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // 获取屏幕参数
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenDensity = metrics.densityDpi
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels

            // 初始化 ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            saveScreenshot(image)
                            image.close()
                        }
                    } catch (e: Exception) {
                        Log.e("ScreenshotActivity", "保存截图失败", e)
                        Toast.makeText(this@ScreenshotActivity, "保存截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        // 发送广播通知服务恢复悬浮球状态
                        sendBroadcast(Intent(ACTION_SCREENSHOT_COMPLETED))
                        // 清理资源
                        cleanupProjection()
                        finish()
                    }
                }, handler)
            }

            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_SCREENSHOT)
        } catch (e: Exception) {
            Log.e("ScreenshotActivity", "启动截图失败", e)
            Toast.makeText(this, "启动截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
            sendBroadcast(Intent(ACTION_SCREENSHOT_COMPLETED))
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            if (requestCode == REQUEST_SCREENSHOT) {
                if (resultCode == RESULT_OK && data != null) {
                    val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                    
                    // 注册 MediaProjection 回调（Android 14及以上）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mediaProjectionCallback?.let { callback ->
                            mediaProjection?.registerCallback(callback, handler)
                        }
                    }
                    
                    createVirtualDisplay()
                } else {
                    Toast.makeText(this, "截图已取消", Toast.LENGTH_SHORT).show()
                    sendBroadcast(Intent(ACTION_SCREENSHOT_COMPLETED))
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenshotActivity", "处理截图结果失败", e)
            Toast.makeText(this, "处理截图结果失败: ${e.message}", Toast.LENGTH_SHORT).show()
            sendBroadcast(Intent(ACTION_SCREENSHOT_COMPLETED))
            finish()
        }
    }

    private fun createVirtualDisplay() {
        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "Screenshot",
                screenWidth, screenHeight, screenDensity,
                VIRTUAL_DISPLAY_FLAGS,
                imageReader?.surface,
                virtualDisplayCallback,
                handler
            )
        } catch (e: Exception) {
            Log.e("ScreenshotActivity", "创建虚拟显示失败", e)
            Toast.makeText(this, "创建虚拟显示失败: ${e.message}", Toast.LENGTH_SHORT).show()
            sendBroadcast(Intent(ACTION_SCREENSHOT_COMPLETED))
            cleanupProjection()
            finish()
        }
    }

    private fun saveScreenshot(image: Image) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // 创建保存目录
            val screenshotDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots")
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }

            // 生成文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Screenshot_$timestamp.png"
            val file = File(screenshotDir, fileName)

            // 保存文件
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 通知媒体库更新
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = android.net.Uri.fromFile(file)
            sendBroadcast(mediaScanIntent)

            Toast.makeText(this, "截图已保存至: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            
            // 发送广播通知服务恢复悬浮球状态
            sendBroadcast(Intent(ACTION_SCREENSHOT_COMPLETED))
            
            // 清理资源并结束活动
            Handler(Looper.getMainLooper()).postDelayed({
                cleanupProjection()
                finish()
            }, 500) // 延迟500ms确保广播能被接收
        } catch (e: Exception) {
            Log.e("ScreenshotActivity", "保存截图失败", e)
            Toast.makeText(this, "保存截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
            // 即使保存失败也发送广播恢复悬浮球状态
            sendBroadcast(Intent(ACTION_SCREENSHOT_COMPLETED))
            cleanupProjection()
            finish()
        }
    }

    private fun cleanupProjection() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            
            // 取消注册回调（Android 14及以上）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjectionCallback?.let { callback ->
                    mediaProjection?.unregisterCallback(callback)
                }
            }
            
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e("ScreenshotActivity", "清理资源失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupProjection()
    }
} 