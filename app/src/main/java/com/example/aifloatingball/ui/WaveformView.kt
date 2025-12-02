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
        style = Paint.Style.STROKE  // 使用描边模式，显示为框线
        strokeWidth = 2f  // 设置线宽
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
        
        // 计算最大振幅（用于框线波动，振幅较小）
        val maxAmplitude = height * 0.3f * amplitude
        
        // 清除路径
        path.reset()
        
        // 绘制波动线（从左上角到右上角，在框线位置）
        val step = 2f  // 每个点的步长
        var isFirstPoint = true
        for (x in 0 until width.toInt() step step.toInt()) {
            val xRatio = x / width
            // 使用正弦波生成波动效果
            val y = centerY + sin(xRatio * 2 * Math.PI * waveCount * 2 + phase).toFloat() * maxAmplitude
            
            if (isFirstPoint) {
                path.moveTo(x.toFloat(), y)
                isFirstPoint = false
            } else {
                path.lineTo(x.toFloat(), y)
            }
        }
        
        // 绘制路径（只绘制线条，不填充）
        canvas.drawPath(path, paint)
    }
} 