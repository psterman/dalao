package com.example.aifloatingball.manager

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.activity.ScreenCapturePermissionActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.service.ScreenTextCaptureService
import com.example.aifloatingball.ui.ScreenTextSelectionOverlay

/**
 * 屏幕文字识别管理器
 * 统一管理屏幕截图、文字识别、区域选择等功能
 */
class ScreenTextRecognitionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenTextRecognition"
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }
    
    // UI组件
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var selectionOverlay: ScreenTextSelectionOverlay? = null
    
    // 服务组件
    private var textCaptureService: ScreenTextCaptureService? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    
    // 状态管理
    private var isActive = false
    private var currentEngine = ScreenTextCaptureService.Companion.RecognitionEngine.ML_KIT

    // 权限请求广播接收器
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenCapturePermissionActivity.ACTION_PERMISSION_RESULT) {
                val resultCode = intent.getIntExtra(ScreenCapturePermissionActivity.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(ScreenCapturePermissionActivity.EXTRA_RESULT_DATA)
                handlePermissionResult(REQUEST_MEDIA_PROJECTION, resultCode, data)
            }
        }
    }

    // 回调接口
    interface TextRecognitionCallback {
        fun onTextExtracted(text: String)
        fun onError(error: String)
        fun onCancelled()
    }

    private var callback: TextRecognitionCallback? = null
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // 注册权限请求结果接收器
        val filter = IntentFilter(ScreenCapturePermissionActivity.ACTION_PERMISSION_RESULT)
        LocalBroadcastManager.getInstance(context).registerReceiver(permissionReceiver, filter)

        // 初始化文字捕获服务
        initTextCaptureService()
    }
    
    /**
     * 初始化文字捕获服务
     */
    private fun initTextCaptureService() {
        textCaptureService = ScreenTextCaptureService()
        textCaptureService?.setCallback(object : ScreenTextCaptureService.TextCaptureCallback {
            override fun onTextCaptured(text: String, boundingBoxes: List<ScreenTextCaptureService.TextBlock>) {
                Handler(Looper.getMainLooper()).post {
                    handleTextCaptured(text, boundingBoxes)
                }
            }
            
            override fun onError(error: String) {
                Handler(Looper.getMainLooper()).post {
                    handleError(error)
                }
            }
        })
    }
    
    /**
     * 设置回调
     */
    fun setCallback(callback: TextRecognitionCallback) {
        this.callback = callback
    }
    
    /**
     * 设置识别引擎
     */
    fun setRecognitionEngine(engine: ScreenTextCaptureService.Companion.RecognitionEngine) {
        this.currentEngine = engine
        Log.d(TAG, "切换识别引擎: $engine")
    }
    
    /**
     * 开始屏幕文字识别
     */
    fun startTextRecognition() {
        if (isActive) {
            Log.w(TAG, "文字识别已经在运行中")
            return
        }
        
        try {
            // 请求屏幕录制权限
            requestScreenCapturePermission()
        } catch (e: Exception) {
            Log.e(TAG, "启动文字识别失败", e)
            callback?.onError("启动失败: ${e.message}")
        }
    }
    
    /**
     * 停止屏幕文字识别
     */
    fun stopTextRecognition() {
        if (!isActive) return
        
        try {
            hideOverlay()
            stopTextCaptureService()
            isActive = false
            Log.d(TAG, "屏幕文字识别已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止文字识别失败", e)
        }
    }
    
    /**
     * 请求屏幕录制权限
     */
    private fun requestScreenCapturePermission() {
        try {
            val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
            if (captureIntent != null) {
                // 启动权限请求Activity
                val intent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                    putExtra("capture_intent", captureIntent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                callback?.onError("无法创建屏幕录制权限请求")
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求屏幕录制权限失败", e)
            callback?.onError("权限请求失败: ${e.message}")
        }
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startTextCaptureService(resultCode, data)
            } else {
                callback?.onError("用户拒绝了屏幕录制权限")
            }
        }
    }
    
    /**
     * 启动文字捕获服务
     */
    private fun startTextCaptureService(resultCode: Int, data: Intent) {
        try {
            val serviceIntent = Intent(context, ScreenTextCaptureService::class.java).apply {
                action = ScreenTextCaptureService.ACTION_START_CAPTURE
                putExtra("result_code", resultCode)
                putExtra("media_projection_data", data)
            }
            context.startService(serviceIntent)
            
            // 显示选择界面
            showSelectionOverlay()
            isActive = true
            
            Log.d(TAG, "文字捕获服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动文字捕获服务失败", e)
            callback?.onError("服务启动失败: ${e.message}")
        }
    }
    
    /**
     * 停止文字捕获服务
     */
    private fun stopTextCaptureService() {
        try {
            val serviceIntent = Intent(context, ScreenTextCaptureService::class.java).apply {
                action = ScreenTextCaptureService.ACTION_STOP_CAPTURE
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "停止文字捕获服务失败", e)
        }
    }
    
    /**
     * 显示选择覆盖层
     */
    private fun showSelectionOverlay() {
        try {
            hideOverlay() // 先隐藏之前的覆盖层
            
            // 创建覆盖层视图
            val inflater = LayoutInflater.from(context)
            overlayView = inflater.inflate(R.layout.screen_text_selection_overlay, null)
            
            // 初始化选择覆盖层
            selectionOverlay = overlayView?.findViewById(R.id.text_selection_overlay)
            selectionOverlay?.setCallback(object : ScreenTextSelectionOverlay.TextSelectionCallback {
                override fun onTextBlockSelected(textBlock: ScreenTextCaptureService.TextBlock) {
                    handleTextBlockSelected(textBlock)
                }
                
                override fun onManualAreaSelected(rect: RectF) {
                    handleManualAreaSelected(rect)
                }
                
                override fun onSelectionCancelled() {
                    stopTextRecognition()
                    callback?.onCancelled()
                }
            })
            
            // 设置控制按钮
            setupControlButtons()
            
            // 设置窗口参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            
            // 添加到窗口
            windowManager?.addView(overlayView, params)
            
            Log.d(TAG, "选择覆盖层已显示")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示选择覆盖层失败", e)
            callback?.onError("界面显示失败: ${e.message}")
        }
    }
    
    /**
     * 设置控制按钮
     */
    private fun setupControlButtons() {
        // 取消按钮
        overlayView?.findViewById<Button>(R.id.btn_cancel)?.setOnClickListener {
            stopTextRecognition()
            callback?.onCancelled()
        }
        
        // 手动选择按钮
        overlayView?.findViewById<Button>(R.id.btn_manual_selection)?.setOnClickListener {
            selectionOverlay?.enableManualSelectionMode()
        }
        
        // 引擎切换按钮
        overlayView?.findViewById<Button>(R.id.btn_switch_engine)?.setOnClickListener {
            switchRecognitionEngine()
        }
    }
    
    /**
     * 切换识别引擎
     */
    private fun switchRecognitionEngine() {
        currentEngine = when (currentEngine) {
            ScreenTextCaptureService.Companion.RecognitionEngine.ML_KIT -> {
                Toast.makeText(context, "切换到Tesseract引擎", Toast.LENGTH_SHORT).show()
                ScreenTextCaptureService.Companion.RecognitionEngine.TESSERACT
            }
            ScreenTextCaptureService.Companion.RecognitionEngine.TESSERACT -> {
                Toast.makeText(context, "切换到ML Kit引擎", Toast.LENGTH_SHORT).show()
                ScreenTextCaptureService.Companion.RecognitionEngine.ML_KIT
            }
        }
    }
    
    /**
     * 隐藏覆盖层
     */
    private fun hideOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
                selectionOverlay = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "隐藏覆盖层失败", e)
        }
    }
    
    /**
     * 处理文字识别结果
     */
    private fun handleTextCaptured(text: String, boundingBoxes: List<ScreenTextCaptureService.TextBlock>) {
        Log.d(TAG, "识别到文字: $text, 文本块数量: ${boundingBoxes.size}")
        
        // 更新选择覆盖层
        selectionOverlay?.setTextBlocks(boundingBoxes)
        
        // 如果只有一个文本块，自动选择
        if (boundingBoxes.size == 1) {
            handleTextBlockSelected(boundingBoxes[0])
        }
    }
    
    /**
     * 处理文本块选择
     */
    private fun handleTextBlockSelected(textBlock: ScreenTextCaptureService.TextBlock) {
        Log.d(TAG, "选择文本块: ${textBlock.text}")
        stopTextRecognition()
        callback?.onTextExtracted(textBlock.text)
    }
    
    /**
     * 处理手动区域选择
     */
    private fun handleManualAreaSelected(rect: RectF) {
        Log.d(TAG, "手动选择区域: $rect")
        // TODO: 实现对指定区域的文字识别
        Toast.makeText(context, "手动区域选择功能开发中", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 处理错误
     */
    private fun handleError(error: String) {
        Log.e(TAG, "文字识别错误: $error")
        stopTextRecognition()
        callback?.onError(error)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopTextRecognition()

        // 注销广播接收器
        try {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(permissionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }

        textCaptureService = null
        callback = null
    }
}
