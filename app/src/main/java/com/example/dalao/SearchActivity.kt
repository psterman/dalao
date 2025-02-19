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
import com.example.aifloatingball.SettingsManager

class SearchActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var previewEngineList: LinearLayout
    private lateinit var letterIndexBar: LetterIndexBar
    private lateinit var letterTitle: TextView
    private lateinit var settingsManager: SettingsManager
    private val searchEngines = mutableListOf<AIEngine>()

    companion object {
        private const val ACTION_SHOW_SEARCH = "com.example.aifloatingball.ACTION_SHOW_SEARCH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // 初始化视图
        searchInput = findViewById(R.id.search_input)
        previewEngineList = findViewById(R.id.preview_engine_list)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)

        // 初始化设置管理器和搜索引擎列表
        settingsManager = SettingsManager.getInstance(this)
        searchEngines.clear()
        searchEngines.addAll(settingsManager.getFilteredEngineOrder())

        setupLetterIndexBar()
        showSearchEnginesByLetter('A') // 显示初始字母的搜索引擎列表
    }

    private fun setupLetterIndexBar() {
        letterIndexBar.onLetterSelectedListener = { _, letter ->
            showSearchEnginesByLetter(letter)
        }
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        // 更新字母标题
        letterTitle.text = letter.toString()
        
        // 清空搜索引擎列表
        previewEngineList.removeAllViews()

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
            previewEngineList.addView(noEngineText)
        } else {
            // 添加匹配的搜索引擎
            matchingEngines.forEach { engine ->
                val engineItem = LayoutInflater.from(this).inflate(
                    R.layout.item_search_engine,
                    previewEngineList,
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

                previewEngineList.addView(engineItem)

                // 在每个搜索引擎项之间添加分隔线
                if (engine != matchingEngines.last()) {
                    val itemDivider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        )
                        setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                    }
                    previewEngineList.addView(itemDivider)
                }
            }
        }
    }
} 