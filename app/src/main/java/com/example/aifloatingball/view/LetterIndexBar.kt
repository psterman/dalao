package com.example.aifloatingball.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import com.example.aifloatingball.model.SearchEngine
import android.graphics.Color
import android.util.Log

class LetterIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = ('A'..'Z').toList()
    private var selectedIndex = -1
    private var lastAnimatedIndex = -1
    private var textColor: Int = 0
    private var accentColor: Int = 0
    private var selectedBackgroundColor: Int = Color.LTGRAY
    private var isDarkMode: Boolean = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterRects = mutableListOf<RectF>()
    private val letterScales = FloatArray(26) { 1.0f }
    private val letterOffsets = FloatArray(26) { 0f }
    private val scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 250
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animator ->
            val progress = animator.animatedValue as Float
            
            letterScales.fill(1.0f)
            letterOffsets.fill(0f)
            
            if (selectedIndex in letterScales.indices) {
                // 增大选中字母的放大效果
                letterScales[selectedIndex] = 1.0f + (1.5f * progress)
                
                // 基础参数设置
                val maxCompression = 0.6f // 最大压缩比例
                val maxOffset = 150f // 最大偏移距离
                
                // 计算效果的函数
                fun calculateEffect(distance: Int, totalLetters: Int, currentIndex: Int): Triple<Float, Float, Float> {
                    // 基础衰减因子
                    val baseFactor = exp(-distance.toFloat() * 0.2f)
                    
                    // 计算边缘加成系数
                    val edgeFactor = when {
                        currentIndex == 0 || currentIndex == totalLetters - 1 -> 2.0f // 最边缘
                        currentIndex == 1 || currentIndex == totalLetters - 2 -> 1.5f // 次边缘
                        currentIndex == 2 || currentIndex == totalLetters - 3 -> 1.2f // 第三层
                        else -> 1.0f
                    }
                    
                    // 计算与选中字母的相对位置系数
                    val selectedDistanceFactor = 1.0f - (distance.toFloat() / totalLetters)
                    
                    // 综合考虑的压缩系数
                    val compressionFactor = baseFactor * edgeFactor * (1.0f + selectedDistanceFactor)
                    
                    // 计算缩放值
                    val scale = 1.0f - (maxCompression * progress * compressionFactor)
                    
                    // 计算偏移值，边缘位置偏移更大
                    val offset = maxOffset * progress * compressionFactor
                    
                    return Triple(scale, offset, edgeFactor)
                }
                
                // 处理上半部分字母
                for (i in 0 until selectedIndex) {
                    val distance = selectedIndex - i
                    val (scale, offset, edgeFactor) = calculateEffect(distance, letters.size, i)
                    
                    // 应用效果
                    letterScales[i] = scale
                    letterOffsets[i] = -offset * edgeFactor // 边缘位置偏移更大
                }
                
                // 处理下半部分字母
                for (i in (selectedIndex + 1) until letters.size) {
                    val distance = i - selectedIndex
                    val (scale, offset, edgeFactor) = calculateEffect(distance, letters.size, i)
                    
                    // 应用效果
                    letterScales[i] = scale
                    letterOffsets[i] = offset * edgeFactor // 边缘位置偏移更大
                }
                
                // 增强连锁反应
                for (i in letters.indices) {
                    if (i != selectedIndex) {
                        val prevScale = if (i > 0) letterScales[i - 1] else 1.0f
                        val nextScale = if (i < letters.size - 1) letterScales[i + 1] else 1.0f
                        val neighborEffect = (prevScale + nextScale) * 0.25f // 增加相邻影响
                        letterScales[i] = letterScales[i] * 0.5f + neighborEffect
                    }
                }
                
                // 额外增强边缘效果
                if (selectedIndex < letters.size / 2) {
                    // 选中点在上半部分，增强底部压缩
                    for (i in (letters.size - 3) until letters.size) {
                        letterScales[i] *= 0.7f
                        letterOffsets[i] *= 1.5f
                    }
                } else {
                    // 选中点在下半部分，增强顶部压缩
                    for (i in 0..2) {
                        letterScales[i] *= 0.7f
                        letterOffsets[i] *= 1.5f
                    }
                }
            }
            invalidate()
        }
    }

    var engines: List<SearchEngine> = emptyList()
        set(value) {
            field = value
            // 当设置了新的引擎列表时，记录日志
            Log.d("LetterIndexBar", "设置了${value.size}个搜索引擎")
            invalidate()
        }

    var onLetterSelectedListener: OnLetterSelectedListener? = null
    var onLetterTouchEnd: (() -> Unit)? = null
    var onTouchPositionChanged: ((Float, Float) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = resources.getDimensionPixelSize(R.dimen.letter_index_text_size).toFloat()
        updateColors()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLetterRects()
    }

    private fun calculateLetterRects() {
        letterRects.clear()
        val letterHeight = height / letters.size
        val letterWidth = width.toFloat()

        letters.forEachIndexed { index, _ ->
            val top = letterHeight * index
            val bottom = top + letterHeight
            letterRects.add(RectF(0f, top.toFloat(), letterWidth, bottom.toFloat()))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 先绘制背景
        if (isDarkMode) {
            setBackgroundColor(ContextCompat.getColor(context, R.color.letter_index_background_dark))
        } else {
            setBackgroundColor(ContextCompat.getColor(context, R.color.letter_index_background_light))
        }

        letters.forEachIndexed { index, letter ->
            val rect = letterRects[index]
            val scale = letterScales[index]
            val offset = letterOffsets[index]
            
            canvas.save()
            
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            
            // 如果是选中的字母，绘制背景
            if (index == selectedIndex) {
                paint.style = Paint.Style.FILL
                paint.color = accentColor
                canvas.drawCircle(centerX, centerY, paint.textSize * 0.7f, paint)
            }
            
            canvas.translate(0f, offset)
            canvas.scale(scale, scale, centerX, centerY)
            
            paint.style = Paint.Style.FILL
            val textY = centerY + (paint.textSize / 3)
            paint.color = if (index == selectedIndex) accentColor else textColor
            canvas.drawText(letter.toString(), centerX, textY, paint)
            
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val index = (event.y / height * letters.size).toInt()
                    .coerceIn(0, letters.size - 1)
                
                if (index != selectedIndex) {
                    selectedIndex = index
                    if (lastAnimatedIndex != index) {
                        startScaleAnimation()
                        lastAnimatedIndex = index
                    }
                    invalidate()
                    onLetterSelectedListener?.onLetterSelected(this, letters[index])
                }
                onTouchPositionChanged?.invoke(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                onLetterTouchEnd?.invoke()
                selectedIndex = -1
                lastAnimatedIndex = -1
                letterScales.fill(1.0f)
                letterOffsets.fill(0f)
                scaleAnimator.cancel()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startScaleAnimation() {
        scaleAnimator.cancel()
        scaleAnimator.start()
    }

    fun getSelectedLetter(): Char {
        return if (selectedIndex in letters.indices) {
            letters[selectedIndex]
        } else {
            'A'
        }
    }

    fun setThemeColors(textColor: Int, accentColor: Int) {
        this.textColor = textColor
        this.accentColor = accentColor
        invalidate()
    }

    fun setDarkMode(isDark: Boolean) {
        this.isDarkMode = isDark
        updateColors()
    }

    private fun updateColors() {
        textColor = context.getColor(if (isDarkMode) R.color.letter_text_dark else R.color.letter_text_light)
        accentColor = context.getColor(if (isDarkMode) R.color.letter_accent_dark else R.color.letter_accent_light)
        invalidate()
    }

    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(color)
        invalidate()
    }

    interface OnLetterSelectedListener {
        fun onLetterSelected(view: View, letter: Char)
    }
} 