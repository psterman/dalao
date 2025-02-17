package com.example.aifloatingball.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R

class LetterIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = ('A'..'Z').toList()
    private var selectedIndex = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = resources.getDimension(R.dimen.letter_text_size)
    }

    var onLetterSelectedListener: ((Int, Char) -> Unit)? = null
    var onLetterTouchEnd: (() -> Unit)? = null
    var onTouchPositionChanged: ((Float, Float) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val letterHeight = height / letters.size
        val centerX = width / 2f

        letters.forEachIndexed { index, letter ->
            paint.color = if (index == selectedIndex) {
                ContextCompat.getColor(context, R.color.accent)
            } else {
                ContextCompat.getColor(context, R.color.primary)
            }

            val y = letterHeight * (index + 0.5f) + (paint.textSize / 2)
            canvas.drawText(letter.toString(), centerX, y, paint)
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
                    invalidate()
                    onLetterSelectedListener?.invoke(index, letters[index])
                }
                onTouchPositionChanged?.invoke(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                onLetterTouchEnd?.invoke()
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun getSelectedLetter(): Char {
        return if (selectedIndex in letters.indices) {
            letters[selectedIndex]
        } else {
            'A'
        }
    }
} 