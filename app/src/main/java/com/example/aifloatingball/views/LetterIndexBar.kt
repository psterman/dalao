package com.example.aifloatingball.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.models.SearchEngine

/**
 * 字母索引栏控件
 * 显示搜索引擎的首字母，允许用户快速选择
 */
class LetterIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 字母表
    private val letters = arrayOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "I",
        "J", "K", "L", "M", "N", "O", "P", "Q", "R",
        "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    )

    // 绘制相关变量
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 38f
        color = Color.BLACK
    }
    private val textBounds = Rect()
    private var selectedIndex = -1
    private var cellHeight = 0f
    
    // 主题颜色
    private var textColor = Color.BLACK
    private var bgColor = Color.WHITE
    private var isDarkMode = false

    // 点击监听器
    var onLetterSelectedListener: OnLetterSelectedListener? = null

    // 定义接口
    interface OnLetterSelectedListener {
        fun onLetterSelected(view: View, letter: Char)
    }

    // 设置深色模式
    fun setDarkMode(darkMode: Boolean) {
        isDarkMode = darkMode
        textColor = if (darkMode) Color.WHITE else Color.BLACK
        invalidate()
    }

    // 设置主题颜色
    fun setThemeColors(textCol: Int, bgCol: Int) {
        textColor = textCol
        bgColor = bgCol
        textPaint.color = textColor
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellHeight = h.toFloat() / letters.size
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制背景
        canvas.drawColor(bgColor)
        
        // 获取控件宽度
        val width = width.toFloat()
        
        // 绘制字母
        for (i in letters.indices) {
            textPaint.color = if (i == selectedIndex) Color.BLUE else textColor
            textPaint.alpha = if (i == selectedIndex) 255 else 200
            
            val text = letters[i]
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            
            val x = width / 2 - textBounds.width() / 2
            val y = cellHeight * i + cellHeight / 2 + textBounds.height() / 2
            
            canvas.drawText(text, x, y, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val y = event.y
                // 计算当前触摸的字母索引
                val index = (y / cellHeight).toInt().coerceIn(0, letters.size - 1)
                if (index != selectedIndex) {
                    selectedIndex = index
                    // 通知回调
                    if (selectedIndex >= 0 && selectedIndex < letters.size) {
                        val letter = letters[selectedIndex][0]
                        onLetterSelectedListener?.onLetterSelected(this, letter)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // 为了兼容现有代码，添加以下方法
    fun setEngines(engines: List<com.example.aifloatingball.models.SearchEngine>) {
        // 这个方法在当前实现中不需要实际操作，仅为兼容性而存在
    }

    fun setOnLetterSelectedListener(listener: (com.example.aifloatingball.models.SearchEngine) -> Unit) {
        // 为了兼容新的实现，这里创建一个适配器
        this.onLetterSelectedListener = object : OnLetterSelectedListener {
            override fun onLetterSelected(view: View, letter: Char) {
                // 查找匹配的引擎
                val engine = findEngineByLetter(letter)
                engine?.let { listener(it) }
            }
        }
    }

    // 辅助方法，查找以指定字母开头的搜索引擎
    private fun findEngineByLetter(letter: Char): com.example.aifloatingball.models.SearchEngine? {
        val engines = getEnginesFromActivity()
        
        // 查找匹配的引擎
        return engines.firstOrNull { 
            it.name.firstOrNull()?.uppercaseChar() == letter.uppercaseChar() 
        }
    }

    // 辅助方法，从Activity获取引擎列表
    private fun getEnginesFromActivity(): List<com.example.aifloatingball.models.SearchEngine> {
        // 这里使用硬编码的方式返回一些引擎
        // 如果需要，您可以通过其他方式获取实际的引擎列表
        return listOf(
            com.example.aifloatingball.models.SearchEngine("google", "Google", "https://www.google.com"),
            com.example.aifloatingball.models.SearchEngine("baidu", "百度", "https://www.baidu.com"),
            com.example.aifloatingball.models.SearchEngine("bing", "Bing", "https://www.bing.com"),
            com.example.aifloatingball.models.SearchEngine("yandex", "Yandex", "https://yandex.com"),
            com.example.aifloatingball.models.SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com"),
            com.example.aifloatingball.models.SearchEngine("yahoo", "Yahoo", "https://search.yahoo.com"),
            com.example.aifloatingball.models.SearchEngine("sogou", "搜狗", "https://www.sogou.com")
        )
    }

    // 显示和隐藏方法
    fun show() {
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(250)
                .start()
        }
    }

    fun hide() {
        if (visibility == VISIBLE) {
            animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction { visibility = GONE }
                .start()
        }
    }
} 