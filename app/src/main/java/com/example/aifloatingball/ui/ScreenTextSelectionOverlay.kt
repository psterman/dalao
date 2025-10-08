package com.example.aifloatingball.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.aifloatingball.service.ScreenTextCaptureService

/**
 * 屏幕文字选择覆盖层
 * 显示识别到的文本区域，支持点击选择
 */
class ScreenTextSelectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), View.OnLongClickListener {
    
    companion object {
        private const val TAG = "TextSelectionOverlay"
    }
    
    // 绘制相关
    private val textBlockPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    
    private val selectedBlockPaint = Paint().apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    // 数据
    private var textBlocks = listOf<ScreenTextCaptureService.TextBlock>()
    private var selectedBlock: ScreenTextCaptureService.TextBlock? = null
    private var isManualSelectionMode = false
    private var manualSelectionRect: RectF? = null
    private var selectionStartX = 0f
    private var selectionStartY = 0f
    
    // 回调接口
    interface TextSelectionCallback {
        fun onTextBlockSelected(textBlock: ScreenTextCaptureService.TextBlock)
        fun onManualAreaSelected(rect: RectF)
        fun onSelectionCancelled()
    }
    
    private var callback: TextSelectionCallback? = null

    init {
        // 设置长按监听器
        setOnLongClickListener(this)
    }

    /**
     * 设置识别到的文本块
     */
    fun setTextBlocks(blocks: List<ScreenTextCaptureService.TextBlock>) {
        this.textBlocks = blocks
        invalidate()
        Log.d(TAG, "设置文本块数量: ${blocks.size}")
    }
    
    /**
     * 设置回调
     */
    fun setCallback(callback: TextSelectionCallback) {
        this.callback = callback
    }
    
    /**
     * 启用手动选择模式
     */
    fun enableManualSelectionMode() {
        isManualSelectionMode = true
        manualSelectionRect = null
        invalidate()
        Log.d(TAG, "启用手动选择模式")
    }
    
    /**
     * 禁用手动选择模式
     */
    fun disableManualSelectionMode() {
        isManualSelectionMode = false
        manualSelectionRect = null
        invalidate()
        Log.d(TAG, "禁用手动选择模式")
    }
    
    /**
     * 清除选择
     */
    fun clearSelection() {
        selectedBlock = null
        manualSelectionRect = null
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制半透明背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        if (isManualSelectionMode) {
            drawManualSelectionMode(canvas)
        } else {
            drawAutoDetectedBlocks(canvas)
        }
        
        // 绘制提示文字
        drawInstructions(canvas)
    }
    
    /**
     * 绘制自动检测的文本块
     */
    private fun drawAutoDetectedBlocks(canvas: Canvas) {
        for (block in textBlocks) {
            val rect = block.boundingBox
            val paint = if (block == selectedBlock) selectedBlockPaint else textBlockPaint
            
            // 绘制边框
            canvas.drawRect(rect, paint)
            
            // 绘制文本预览（如果文本不太长）
            if (block.text.length <= 20) {
                val centerX = rect.centerX().toFloat()
                val centerY = rect.centerY().toFloat()
                
                // 绘制文本背景
                val textBounds = Rect()
                textPaint.getTextBounds(block.text, 0, block.text.length, textBounds)
                val bgRect = RectF(
                    centerX - textBounds.width() / 2 - 8,
                    centerY - textBounds.height() / 2 - 8,
                    centerX + textBounds.width() / 2 + 8,
                    centerY + textBounds.height() / 2 + 8
                )
                canvas.drawRoundRect(bgRect, 8f, 8f, backgroundPaint)
                
                // 绘制文本
                canvas.drawText(block.text, centerX, centerY + textBounds.height() / 2, textPaint)
            }
        }
    }
    
    /**
     * 绘制手动选择模式
     */
    private fun drawManualSelectionMode(canvas: Canvas) {
        // 绘制选择矩形
        manualSelectionRect?.let { rect ->
            canvas.drawRect(rect, selectedBlockPaint)
        }
        
        // 绘制网格线辅助选择
        drawGridLines(canvas)
    }
    
    /**
     * 绘制网格线
     */
    private fun drawGridLines(canvas: Canvas) {
        val gridPaint = Paint().apply {
            color = Color.parseColor("#40FFFFFF")
            strokeWidth = 1f
        }
        
        val gridSize = 100f
        
        // 垂直线
        var x = gridSize
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += gridSize
        }
        
        // 水平线
        var y = gridSize
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += gridSize
        }
    }
    
    /**
     * 绘制操作说明
     */
    private fun drawInstructions(canvas: Canvas) {
        val instructions = if (isManualSelectionMode) {
            "拖拽选择文字区域"
        } else {
            "点击文字区域选择 • 长按切换手动模式"
        }
        
        val instructionPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // 绘制说明背景
        val textBounds = Rect()
        instructionPaint.getTextBounds(instructions, 0, instructions.length, textBounds)
        val bgRect = RectF(
            width / 2f - textBounds.width() / 2 - 16,
            50f,
            width / 2f + textBounds.width() / 2 + 16,
            50f + textBounds.height() + 16
        )
        canvas.drawRoundRect(bgRect, 12f, 12f, backgroundPaint)
        
        // 绘制说明文字
        canvas.drawText(instructions, width / 2f, 50f + textBounds.height(), instructionPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectionStartX = event.x
                selectionStartY = event.y
                
                if (isManualSelectionMode) {
                    manualSelectionRect = RectF(event.x, event.y, event.x, event.y)
                } else {
                    // 检查是否点击了文本块
                    val clickedBlock = findTextBlockAt(event.x, event.y)
                    if (clickedBlock != null) {
                        selectedBlock = clickedBlock
                        callback?.onTextBlockSelected(clickedBlock)
                        Log.d(TAG, "选择文本块: ${clickedBlock.text}")
                    }
                }
                invalidate()
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isManualSelectionMode && manualSelectionRect != null) {
                    manualSelectionRect = RectF(
                        minOf(selectionStartX, event.x),
                        minOf(selectionStartY, event.y),
                        maxOf(selectionStartX, event.x),
                        maxOf(selectionStartY, event.y)
                    )
                    invalidate()
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                if (isManualSelectionMode && manualSelectionRect != null) {
                    val rect = manualSelectionRect!!
                    if (rect.width() > 50 && rect.height() > 20) {
                        callback?.onManualAreaSelected(rect)
                        Log.d(TAG, "手动选择区域: $rect")
                    }
                }
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    override fun onLongClick(v: View?): Boolean {
        if (!isManualSelectionMode) {
            enableManualSelectionMode()
            return true
        }
        return false
    }
    
    /**
     * 查找指定位置的文本块
     */
    private fun findTextBlockAt(x: Float, y: Float): ScreenTextCaptureService.TextBlock? {
        for (block in textBlocks) {
            if (block.boundingBox.contains(x.toInt(), y.toInt())) {
                return block
            }
        }
        return null
    }
}
