package com.example.dalao

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import net.sourceforge.pinyin4j.PinyinHelper
import com.example.aifloatingball.AIEngine
import com.example.aifloatingball.AIEngineConfig
import com.example.aifloatingball.FloatingWindowService
import com.example.aifloatingball.R
import com.example.aifloatingball.view.LetterIndexBar

class SearchActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var engineListLayout: LinearLayout
    private lateinit var letterIndexBar: LetterIndexBar
    private val searchEngines = AIEngineConfig.engines

    companion object {
        private const val ACTION_SHOW_SEARCH = "com.example.aifloatingball.ACTION_SHOW_SEARCH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // 初始化视图
        searchInput = findViewById(R.id.search_input)
        engineListLayout = findViewById(R.id.engine_list_layout)
        letterIndexBar = findViewById(R.id.letter_index_bar)

        setupLetterIndexBar()
    }

    private fun setupLetterIndexBar() {
        letterIndexBar.onLetterSelectedListener = { _, letter ->
            showSearchEnginesByLetter(letter)
        }
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        engineListLayout.removeAllViews()
        
        // 添加字母标题
        val letterTitle = TextView(this).apply {
            text = letter.toString()
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }
        engineListLayout.addView(letterTitle)

        // 添加分隔线
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
        }
        engineListLayout.addView(divider)

        // 查找所有匹配该字母的搜索引擎
        val matchingEngines = searchEngines.filter { engine ->
            val firstChar = engine.name.first()
            if (firstChar.toString().matches(Regex("[\u4e00-\u9fa5]"))) {
                PinyinHelper.toHanyuPinyinStringArray(firstChar)?.firstOrNull()?.first() == letter.lowercaseChar()
            } else {
                firstChar.lowercaseChar() == letter.lowercaseChar()
            }
        }

        if (matchingEngines.isEmpty()) {
            // 如果没有匹配的搜索引擎，显示提示信息
            val noEngineText = TextView(this).apply {
                text = "没有以 $letter 开头的搜索引擎"
                textSize = 16f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(16, 32, 16, 32)
            }
            engineListLayout.addView(noEngineText)
        } else {
            // 添加匹配的搜索引擎
            matchingEngines.forEach { engine ->
                val engineItem = LayoutInflater.from(this).inflate(
                    R.layout.item_search_engine,
                    engineListLayout,
                    false
                )

                // 设置搜索引擎图标
                engineItem.findViewById<ImageView>(R.id.engine_icon)
                    .setImageResource(engine.iconResId)

                // 设置搜索引擎名称
                engineItem.findViewById<TextView>(R.id.engine_name)
                    .text = engine.name

                // 添加点击事件
                engineItem.setOnClickListener {
                    val intent = Intent(this, FloatingWindowService::class.java).apply {
                        action = ACTION_SHOW_SEARCH
                        putExtra("ENGINE_NAME", engine.name)
                        putExtra("ENGINE_URL", engine.url)
                        putExtra("ENGINE_ICON", engine.iconResId)
                        putExtra("SEARCH_QUERY", searchInput.text.toString())
                    }
                    startService(intent)
                    finish()
                }

                engineListLayout.addView(engineItem)

                // 在每个搜索引擎项之间添加分隔线
                if (engine != matchingEngines.last()) {
                    val itemDivider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        )
                        setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                    }
                    engineListLayout.addView(itemDivider)
                }
            }
        }
    }
} 