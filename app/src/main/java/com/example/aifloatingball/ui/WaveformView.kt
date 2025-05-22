package com.example.aifloatingball.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/**
 * 波形视图，用于显示语音输入的声音波形
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 绘制相关
    private val paint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#4CAF50")  // 默认为绿色
        style = Paint.Style.FILL
    }
    
    private val path = Path()
    
    // 波形参数
    private var amplitude = 0.1f  // 波形振幅 (0.0 - 1.0)
    private var phase = 0f        // 波形相位
    private var frequency = 1.5f  // 波形频率
    private var waveCount = 2     // 波浪数量
    
    // 动画相关
    private var animationRunning = true
    private val phaseStep = 0.05f
    
    // 更新振幅
    fun setAmplitude(value: Float) {
        this.amplitude = value.coerceIn(0.1f, 1.0f)
        invalidate()
    }
    
    // 设置波形颜色
    fun setWaveColor(color: Int) {
        paint.color = color
        invalidate()
    }
    
    // 启动/暂停动画
    fun setAnimationRunning(running: Boolean) {
        this.animationRunning = running
        if (running) {
            invalidate()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制波形
        drawWaveform(canvas)
        
        // 如果动画正在运行，继续更新相位并重绘
        if (animationRunning) {
            phase += phaseStep
            if (phase > 2 * Math.PI) {
                phase = 0f
            }
            invalidate()
        }
    }
    
    private fun drawWaveform(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2
        
        // 计算最大振幅
        val maxAmplitude = centerY * 0.8f * amplitude
        
        // 清除路径
        path.reset()
        
        // 移动到起点
        path.moveTo(0f, centerY)
        
        // 绘制正弦波
        val step = 1f  // 每个点的步长
        for (x in 0 until width.toInt() step step.toInt()) {
            val xRatio = x / width
            val y = centerY + sin(xRatio * 2 * Math.PI * waveCount + phase) * maxAmplitude
            path.lineTo(x.toFloat(), y.toFloat())
        }
        
        // 完成路径
        path.lineTo(width, centerY)
        path.lineTo(width, height)
        path.lineTo(0f, height)
        path.close()
        
        // 绘制路径
        canvas.drawPath(path, paint)
    }
} 