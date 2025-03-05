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

        // Initialize views
        searchInput = findViewById(R.id.search_input)
        previewEngineList = findViewById(R.id.preview_engine_list)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)

        // Initialize settings manager and search engine list
        settingsManager = SettingsManager.getInstance(this)
        searchEngines.clear()
        searchEngines.addAll(settingsManager.getFilteredEngineOrder())

        // Apply theme
        applyTheme()

        setupLetterIndexBar()
        showSearchEnginesByLetter('A') // Show initial letter's search engine list
    }

    private fun applyTheme() {
        val isDarkMode = when (settingsManager.getThemeMode()) {
            "dark" -> true
            "light" -> false
            else -> resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        val layoutTheme = settingsManager.getLayoutTheme()
        
        // Apply theme colors based on layout theme
        val backgroundColor = when (layoutTheme) {
            "fold" -> if (isDarkMode) R.color.fold_background_dark else R.color.fold_background_light
            "material" -> if (isDarkMode) R.color.material_background_dark else R.color.material_background_light
            "glass" -> if (isDarkMode) R.color.glass_background_dark else R.color.glass_background_light
            else -> if (isDarkMode) R.color.fold_background_dark else R.color.fold_background_light
        }

        val textColor = when (layoutTheme) {
            "fold" -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
            "material" -> if (isDarkMode) R.color.material_text_dark else R.color.material_text_light
            "glass" -> if (isDarkMode) R.color.glass_text_dark else R.color.glass_text_light
            else -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
        }

        // Apply colors to views
        letterTitle.setTextColor(ContextCompat.getColor(this, textColor))
        letterIndexBar.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        previewEngineList.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))

        // Update letter index bar theme
        letterIndexBar.setDarkMode(isDarkMode)
        letterIndexBar.setThemeColors(
            ContextCompat.getColor(this, textColor),
            ContextCompat.getColor(this, backgroundColor)
        )
    }

    // Add method to update theme
    private fun updateTheme() {
        applyTheme()
        // Refresh the current letter's engine list to apply new colors
        letterTitle.text?.firstOrNull()?.let { letter ->
            showSearchEnginesByLetter(letter)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update theme when activity resumes
        updateTheme()
    }

    private fun setupLetterIndexBar() {
        letterIndexBar.onLetterSelectedListener = object : com.example.aifloatingball.view.LetterIndexBar.OnLetterSelectedListener {
            override fun onLetterSelected(view: View, letter: Char) {
                updateEngineList(letter)
            }
        }
    }

    private fun updateEngineList(letter: Char) {
        // Update letter title
        letterTitle.text = letter.toString()
        letterTitle.visibility = View.VISIBLE
        
        // Show engines for the selected letter
        showSearchEnginesByLetter(letter)
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        // Update letter title
        letterTitle.text = letter.toString()
        
        // Get theme colors
        val isDarkMode = when (settingsManager.getThemeMode()) {
            "dark" -> true
            "light" -> false
            else -> resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        
        val layoutTheme = settingsManager.getLayoutTheme()
        val textColor = when (layoutTheme) {
            "fold" -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
            "material" -> if (isDarkMode) R.color.material_text_dark else R.color.material_text_light
            "glass" -> if (isDarkMode) R.color.glass_text_dark else R.color.glass_text_light
            else -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
        }

        // Clear engine list
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

                // Set search engine icon with theme color
                engineItem.findViewById<ImageView>(R.id.engine_icon).apply {
                    setImageResource(engine.iconResId)
                    setColorFilter(ContextCompat.getColor(context, textColor))
                }

                // Set search engine name with theme color
                engineItem.findViewById<TextView>(R.id.engine_name).apply {
                    text = engine.name
                    setTextColor(ContextCompat.getColor(context, textColor))
                }

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