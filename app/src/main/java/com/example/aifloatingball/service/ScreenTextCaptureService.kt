package com.example.aifloatingball.service

import android.app.Service
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
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 屏幕文字识别服务
 * 支持ML Kit和Tesseract两种识别引擎
 */
class ScreenTextCaptureService : Service() {
    
    companion object {
        private const val TAG = "ScreenTextCapture"
        const val ACTION_START_CAPTURE = "start_capture"
        const val ACTION_STOP_CAPTURE = "stop_capture"
        
        // 识别引擎类型
        enum class RecognitionEngine {
            ML_KIT,      // Google ML Kit (默认)
            TESSERACT    // Tesseract OCR (备用)
        }
    }
    
    // MediaProjection相关
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // ML Kit文字识别器
    private var mlKitRecognizer: TextRecognizer? = null
    
    // 屏幕参数
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    // 回调接口
    interface TextCaptureCallback {
        fun onTextCaptured(text: String, boundingBoxes: List<TextBlock>)
        fun onError(error: String)
    }
    
    // 文本块数据类
    data class TextBlock(
        val text: String,
        val boundingBox: android.graphics.Rect,
        val confidence: Float = 0f
    )
    
    private var callback: TextCaptureCallback? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenTextCaptureService创建")
        
        // 初始化ML Kit识别器
        initMLKitRecognizer()
        
        // 获取屏幕参数
        initScreenParameters()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val data = intent.getParcelableExtra<Intent>("media_projection_data")
                val resultCode = intent.getIntExtra("result_code", -1)
                if (data != null && resultCode != -1) {
                    startScreenCapture(resultCode, data)
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopScreenCapture()
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        mlKitRecognizer?.close()
        Log.d(TAG, "ScreenTextCaptureService销毁")
    }
    
    /**
     * 初始化ML Kit识别器
     */
    private fun initMLKitRecognizer() {
        try {
            // 使用Latin文字识别器（支持英文、数字等）
            mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Log.d(TAG, "ML Kit识别器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit识别器初始化失败", e)
        }
    }
    
    /**
     * 初始化屏幕参数
     */
    private fun initScreenParameters() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi
        
        Log.d(TAG, "屏幕参数: ${screenWidth}x${screenHeight}, 密度: $screenDensity")
    }
    
    /**
     * 开始屏幕捕获
     */
    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            // 创建ImageReader
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 1)
            
            // 设置图像可用监听器
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    processImage(image)
                    image.close()
                }
            }, null)
            
            // 创建虚拟显示
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            
            Log.d(TAG, "屏幕捕获开始")
            
        } catch (e: Exception) {
            Log.e(TAG, "开始屏幕捕获失败", e)
            callback?.onError("屏幕捕获失败: ${e.message}")
        }
    }
    
    /**
     * 停止屏幕捕获
     */
    private fun stopScreenCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            Log.d(TAG, "屏幕捕获停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止屏幕捕获失败", e)
        }
    }
    
    /**
     * 处理捕获的图像
     */
    private fun processImage(image: Image) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 将Image转换为Bitmap
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    // 使用ML Kit识别文字
                    recognizeTextWithMLKit(bitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理图像失败", e)
                withContext(Dispatchers.Main) {
                    callback?.onError("图像处理失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // 如果有padding，需要裁剪
            if (rowPadding != 0) {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image转Bitmap失败", e)
            null
        }
    }
    
    /**
     * 使用ML Kit识别文字
     */
    private fun recognizeTextWithMLKit(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        mlKitRecognizer?.process(inputImage)
            ?.addOnSuccessListener { visionText ->
                val textBlocks = mutableListOf<TextBlock>()
                
                for (block in visionText.textBlocks) {
                    val boundingBox = block.boundingBox ?: continue
                    textBlocks.add(
                        TextBlock(
                            text = block.text,
                            boundingBox = boundingBox,
                            confidence = 1.0f // ML Kit不提供置信度，默认为1.0
                        )
                    )
                }
                
                val fullText = visionText.text
                Log.d(TAG, "ML Kit识别完成，文本块数量: ${textBlocks.size}")
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback?.onTextCaptured(fullText, textBlocks)
                }
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "ML Kit识别失败", e)
                CoroutineScope(Dispatchers.Main).launch {
                    callback?.onError("文字识别失败: ${e.message}")
                }
            }
    }
    
    /**
     * 设置回调
     */
    fun setCallback(callback: TextCaptureCallback) {
        this.callback = callback
    }
    
    /**
     * 单次截图识别（用于手动选择区域）
     */
    fun captureScreenOnce() {
        // 触发一次图像捕获
        // 这个方法会在用户长按悬浮球时调用
    }
}
