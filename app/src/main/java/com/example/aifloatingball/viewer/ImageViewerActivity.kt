package com.example.aifloatingball.viewer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.aifloatingball.R
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 内置图片查看器
 * 支持缩放、拖拽等手势操作
 */
class ImageViewerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ImageViewerActivity"
        private const val EXTRA_IMAGE_URI = "image_uri"
        private const val EXTRA_IMAGE_PATH = "image_path"
        
        /**
         * 启动图片查看器
         */
        fun start(context: Activity, imageUri: String) {
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, imageUri)
            }
            context.startActivity(intent)
        }
        
        /**
         * 启动图片查看器（使用文件路径）
         */
        fun startWithPath(context: Activity, imagePath: String) {
            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_PATH, imagePath)
            }
            context.startActivity(intent)
        }
    }
    
    private lateinit var imageView: ImageView
    private lateinit var matrix: Matrix
    
    private var currentScale = 1f
    private var minScale = 0.5f
    private var maxScale = 5f
    
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    // iOS风格的缩放相关变量
    private var focusX = 0f
    private var focusY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var isZooming = false
    private var initialDistance = 0f
    private var initialScale = 1f
    private var initialFocusX = 0f
    private var initialFocusY = 0f
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏显示
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(R.layout.activity_image_viewer)
        
        initViews()
        loadImage()
    }
    
    private fun initViews() {
        imageView = findViewById(R.id.imageView)
        
        // 初始化矩阵
        matrix = Matrix()
        
        // 设置触摸监听器
        imageView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        
        // 设置点击监听器（双击重置缩放）
        imageView.setOnClickListener {
            resetScale()
        }
        
        // 初始化工具栏按钮
        initToolbarButtons()
    }
    
    private fun initToolbarButtons() {
        // 返回按钮
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // 分享按钮
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            shareImage()
        }
        
        // 旋转按钮
        findViewById<ImageButton>(R.id.btnRotate).setOnClickListener {
            rotateImage()
        }
        
        // 重置按钮
        findViewById<ImageButton>(R.id.btnReset).setOnClickListener {
            resetScale()
        }
        
        // 保存按钮
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener {
            saveImage()
        }
    }
    
    private fun loadImage() {
        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        
        scope.launch {
            try {
                val bitmap = when {
                    !imagePath.isNullOrEmpty() -> {
                        loadBitmapFromPath(imagePath)
                    }
                    !imageUri.isNullOrEmpty() -> {
                        loadBitmapFromUri(imageUri)
                    }
                    else -> {
                        Log.e(TAG, "没有提供图片URI或路径")
                        null
                    }
                }
                
                bitmap?.let {
                    imageView.setImageBitmap(it)
                    centerImage()
                    Log.d(TAG, "图片加载成功")
                } ?: run {
                    Toast.makeText(this@ImageViewerActivity, "图片加载失败", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "图片加载异常", e)
                Toast.makeText(this@ImageViewerActivity, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private suspend fun loadBitmapFromPath(imagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: $imagePath")
                return@withContext null
            }
            
            // 获取图片尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            
            // 计算合适的缩放比例
            val scale = calculateInSampleSize(options, 1920, 1080)
            
            // 加载图片
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            
            BitmapFactory.decodeFile(imagePath, loadOptions)
        } catch (e: Exception) {
            Log.e(TAG, "从路径加载图片失败", e)
            null
        }
    }
    
    private suspend fun loadBitmapFromUri(imageUri: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(imageUri)
            val inputStream: InputStream? = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (file.exists()) file.inputStream() else null
                }
                "content" -> {
                    contentResolver.openInputStream(uri)
                }
                else -> {
                    Log.e(TAG, "不支持的URI scheme: ${uri.scheme}")
                    null
                }
            }
            
            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "从URI加载图片失败", e)
            null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                lastFocusX = event.x
                lastFocusY = event.y
                isZooming = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 双指触摸开始
                initialDistance = getDistance(event)
                initialScale = currentScale
                initialFocusX = (event.getX(0) + event.getX(1)) / 2f
                initialFocusY = (event.getY(0) + event.getY(1)) / 2f
                isZooming = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isZooming && event.pointerCount == 2) {
                    // 双指缩放
                    val currentDistance = getDistance(event)
                    val scaleFactor = currentDistance / initialDistance
                    val newScale = initialScale * scaleFactor
                    
                    // 限制缩放范围
                    val clampedScale = newScale.coerceIn(minScale, maxScale)
                    
                    // 计算缩放中心点
                    val currentFocusX = (event.getX(0) + event.getX(1)) / 2f
                    val currentFocusY = (event.getY(0) + event.getY(1)) / 2f
                    
                    // 以手指中心为缩放点
                    matrix.postScale(clampedScale / currentScale, clampedScale / currentScale, currentFocusX, currentFocusY)
                    currentScale = clampedScale
                    
                    imageView.imageMatrix = matrix
                } else if (!isZooming && event.pointerCount == 1) {
                    // 单指拖拽
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    
                    matrix.postTranslate(deltaX, deltaY)
                    imageView.imageMatrix = matrix
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 双指触摸结束
                isZooming = false
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP -> {
                isZooming = false
                // 边界检查
                checkBounds()
            }
        }
        return true
    }
    
    /**
     * 计算两点之间的距离
     */
    private fun getDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
    
    /**
     * 检查图片边界，防止拖拽超出范围
     */
    private fun checkBounds() {
        val drawable = imageView.drawable ?: return
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val viewWidth = imageView.width
        val viewHeight = imageView.height
        
        if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return
        
        val scaledWidth = drawableWidth * currentScale
        val scaledHeight = drawableHeight * currentScale
        
        val values = FloatArray(9)
        matrix.getValues(values)
        val translateX = values[Matrix.MTRANS_X]
        val translateY = values[Matrix.MTRANS_Y]
        
        var newTranslateX = translateX
        var newTranslateY = translateY
        
        // 水平边界检查
        if (scaledWidth <= viewWidth) {
            // 图片宽度小于视图宽度，居中显示
            newTranslateX = (viewWidth - scaledWidth) / 2f
        } else {
            // 图片宽度大于视图宽度，限制拖拽范围
            val maxTranslateX = 0f
            val minTranslateX = viewWidth - scaledWidth
            newTranslateX = newTranslateX.coerceIn(minTranslateX, maxTranslateX)
        }
        
        // 垂直边界检查
        if (scaledHeight <= viewHeight) {
            // 图片高度小于视图高度，居中显示
            newTranslateY = (viewHeight - scaledHeight) / 2f
        } else {
            // 图片高度大于视图高度，限制拖拽范围
            val maxTranslateY = 0f
            val minTranslateY = viewHeight - scaledHeight
            newTranslateY = newTranslateY.coerceIn(minTranslateY, maxTranslateY)
        }
        
        // 应用边界修正
        if (newTranslateX != translateX || newTranslateY != translateY) {
            matrix.postTranslate(newTranslateX - translateX, newTranslateY - translateY)
            imageView.imageMatrix = matrix
        }
    }
    
    
    private fun resetScale() {
        currentScale = 1f
        matrix.reset()
        centerImage()
        imageView.imageMatrix = matrix
    }
    
    /**
     * 居中显示图片
     */
    private fun centerImage() {
        val drawable = imageView.drawable
        if (drawable == null) return
        
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val viewWidth = imageView.width
        val viewHeight = imageView.height
        
        if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return
        
        val scaleX = viewWidth.toFloat() / drawableWidth
        val scaleY = viewHeight.toFloat() / drawableHeight
        val scale = minOf(scaleX, scaleY)
        
        val scaledWidth = drawableWidth * scale
        val scaledHeight = drawableHeight * scale
        
        val translateX = (viewWidth - scaledWidth) / 2f
        val translateY = (viewHeight - scaledHeight) / 2f
        
        matrix.reset()
        matrix.postTranslate(translateX, translateY)
        matrix.postScale(scale, scale, translateX + scaledWidth / 2f, translateY + scaledHeight / 2f)
        
        imageView.imageMatrix = matrix
        currentScale = scale
    }
    
    /**
     * 分享图片
     */
    private fun shareImage() {
        try {
            val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
            val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
            
            val uri = when {
                !imagePath.isNullOrEmpty() -> {
                    val file = File(imagePath)
                    if (file.exists()) {
                        FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                    } else null
                }
                !imageUri.isNullOrEmpty() -> Uri.parse(imageUri)
                else -> null
            }
            
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "分享图片"))
            } else {
                Toast.makeText(this, "无法分享图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享图片失败", e)
            Toast.makeText(this, "分享图片失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 旋转图片
     */
    private fun rotateImage() {
        matrix.postRotate(90f, imageView.width / 2f, imageView.height / 2f)
        imageView.imageMatrix = matrix
        Toast.makeText(this, "图片已旋转", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 保存图片
     */
    private fun saveImage() {
        try {
            val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
            val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
            
            val sourcePath = when {
                !imagePath.isNullOrEmpty() -> imagePath
                !imageUri.isNullOrEmpty() -> {
                    val uri = Uri.parse(imageUri)
                    if (uri.scheme == "file") uri.path else null
                }
                else -> null
            }
            
            if (sourcePath != null) {
                val sourceFile = File(sourcePath)
                if (sourceFile.exists()) {
                    val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AIFloatingBall")
                    if (!picturesDir.exists()) {
                        picturesDir.mkdirs()
                    }
                    
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val destFile = File(picturesDir, "image_$timestamp.jpg")
                    
                    sourceFile.copyTo(destFile, overwrite = true)
                    Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "源文件不存在", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "无法保存图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存图片失败", e)
            Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
