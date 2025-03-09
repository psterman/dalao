package com.example.dalao

import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.aifloatingball.FloatingWindowService
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import net.sourceforge.pinyin4j.PinyinHelper

class SearchActivity : AppCompatActivity() {
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private lateinit var settingsManager: SettingsManager
    private lateinit var searchInput: TextView
    private val searchEngines = mutableListOf<Any>()

    companion object {
        const val ACTION_SHOW_SEARCH = "com.example.aifloatingball.ACTION_SHOW_SEARCH"
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
        
        // 获取正确的文本颜色
        val textColorRes = if (isDarkMode) {
            R.color.engine_name_text_dark // 深色模式下的浅色文本
        } else {
            R.color.engine_name_text_light // 浅色模式下的深色文本
        }
        
        val textColor = ContextCompat.getColor(this, textColorRes)

        // Clear engine list
        previewEngineList.removeAllViews()

        // 显示搜索模式信息
        val isAIMode = settingsManager.isDefaultAIMode()
        val modeInfoText = TextView(this).apply {
            text = if (isAIMode) "当前模式: AI搜索" else "当前模式: 普通搜索"
            textSize = 14f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            
            // 设置背景颜色指示当前模式
            setBackgroundResource(if (isAIMode) R.color.ai_mode_indicator_bg else R.color.normal_mode_indicator_bg)
        }
        previewEngineList.addView(modeInfoText)
        
        // 添加分隔线
        val modeDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(ContextCompat.getColor(this@SearchActivity,
                if (isDarkMode) R.color.divider_dark else R.color.divider_light))
        }
        previewEngineList.addView(modeDivider)

        // 记录搜索引擎列表中的引擎类型，用于调试
        val aiCount = searchEngines.count { it is AISearchEngine }
        val normalCount = searchEngines.count { it is SearchEngine }
        Log.d("SearchActivity", "搜索引擎列表: AI引擎=${aiCount}个, 普通引擎=${normalCount}个, 总计=${searchEngines.size}个")
        Log.d("SearchActivity", "当前搜索模式: ${if (isAIMode) "AI模式" else "普通模式"}")

        // 查找所有匹配该字母的搜索引擎
        val matchingEngines = searchEngines.filter { engine ->
            val engineName = when (engine) {
                is AISearchEngine -> engine.name
                is SearchEngine -> engine.name
                else -> ""
            }
            
            if (engineName.isEmpty()) return@filter false
            
            val firstChar = engineName.first()
            if (firstChar.toString().matches(Regex("[\u4e00-\u9fa5]"))) {
                PinyinHelper.toHanyuPinyinStringArray(firstChar)?.firstOrNull()?.first() == letter.lowercaseChar()
            } else {
                firstChar.lowercaseChar() == letter.lowercaseChar()
            }
        }

        Log.d("SearchActivity", "匹配字母 '$letter' 的引擎数量: ${matchingEngines.size}")

        if (matchingEngines.isEmpty()) {
            // 如果没有匹配的搜索引擎，显示提示信息
            val noEngineText = TextView(this).apply {
                text = "没有以 $letter 开头的搜索引擎"
                textSize = 16f
                setTextColor(textColor)
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

                // 获取引擎信息
                val (engineName, engineUrl, engineIcon) = when (engine) {
                    is AISearchEngine -> Triple(engine.name, engine.url, engine.iconResId)
                    is SearchEngine -> Triple(engine.name, engine.url, engine.iconResId)
                    else -> Triple("未知引擎", "", R.drawable.ic_search)
                }

                // Set search engine icon with theme color
                engineItem.findViewById<ImageView>(R.id.engine_icon).apply {
                    setImageResource(engineIcon)
                    setColorFilter(textColor) // 使用正确的文本颜色
                }

                // Set search engine name with theme color
                engineItem.findViewById<TextView>(R.id.engine_name).apply {
                    // 在AI模式下显示引擎名称时添加"(AI)"标记
                    val displayName = if (engine is AISearchEngine) "$engineName (AI)" else engineName
                    text = displayName
                    setTextColor(textColor) // 使用正确的文本颜色
                }

                // 设置引擎描述文本（如果有）
                engineItem.findViewById<TextView>(R.id.engine_description)?.apply {
                    val description = when (engine) {
                        is AISearchEngine -> engine.description
                        is SearchEngine -> engine.description
                        else -> ""
                    }
                    text = description
                    visibility = if (description.isNotEmpty()) View.VISIBLE else View.GONE
                    setTextColor(textColor) // 使用正确的文本颜色
                }

                // 设置项目背景
                engineItem.setBackgroundColor(ContextCompat.getColor(this,
                    if (isDarkMode) R.color.engine_list_background_dark
                    else R.color.engine_list_background_light))

                // 添加点击事件
                engineItem.setOnClickListener {
                    val intent = Intent(this, FloatingWindowService::class.java).apply {
                        action = ACTION_SHOW_SEARCH
                        putExtra("ENGINE_NAME", engineName)
                        putExtra("ENGINE_URL", engineUrl)
                        putExtra("ENGINE_ICON", engineIcon)
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
                        setBackgroundColor(ContextCompat.getColor(this@SearchActivity,
                            if (isDarkMode) R.color.divider_dark else R.color.divider_light))
                    }
                    previewEngineList.addView(itemDivider)
                }
            }
        }
    }
} 