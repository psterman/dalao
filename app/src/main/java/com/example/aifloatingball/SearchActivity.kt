package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import android.view.LayoutInflater
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.Color

class SearchActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var settingsManager: SettingsManager
    private var currentLayoutTheme: String = "fold"
    private val layoutThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.LAYOUT_THEME_CHANGED") {
                currentLayoutTheme = settingsManager.getLayoutTheme()
                setupViews()
            }
        }
    }
    private lateinit var letterIndexBar: com.example.aifloatingball.view.LetterIndexBar
    private lateinit var previewContainer: LinearLayout
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private var currentLetter: Char = 'A'
    private var sortedEngines: List<SearchEngine> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_search)

            settingsManager = SettingsManager.getInstance(this)
            currentLayoutTheme = settingsManager.getLayoutTheme()
            
            // 注册布局主题变更广播接收器
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    layoutThemeReceiver,
                    IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED"),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(
                    layoutThemeReceiver,
                    IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED")
                )
            }
        
            setupViews()
            setupClickListeners()

            // 检查是否有剪贴板文本传入
            val clipboardText = intent.getStringExtra("CLIPBOARD_TEXT")
            clipboardText?.let { text ->
                if (text.isNotBlank()) {
                    searchInput.setText(text)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error in onCreate", e)
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            // 初始化所有视图
            searchInput = findViewById(R.id.search_input) ?: throw IllegalStateException("搜索输入框未找到")
            searchButton = findViewById(R.id.btn_search) ?: throw IllegalStateException("搜索按钮未找到")
            closeButton = findViewById(R.id.btn_close) ?: throw IllegalStateException("关闭按钮未找到")
            letterIndexBar = findViewById(R.id.letter_index_bar) ?: throw IllegalStateException("字母索引栏未找到")
            previewContainer = findViewById(R.id.preview_container) ?: throw IllegalStateException("预览容器未找到")
            letterTitle = findViewById(R.id.letter_title) ?: throw IllegalStateException("字母标题未找到")
            previewEngineList = findViewById(R.id.preview_engine_list) ?: throw IllegalStateException("预览引擎列表未找到")

            // 初始化搜索引擎列表
            sortedEngines = settingsManager.getFilteredEngineOrder().map { aiEngine ->
                SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
            }.sortedWith(compareBy { 
                val firstChar = it.name.first().toString()
                when {
                    firstChar.matches(Regex("[A-Za-z]")) -> firstChar.uppercase()
                    firstChar.matches(Regex("[\u4e00-\u9fa5]")) -> {
                        try {
                            java.text.Collator.getInstance(java.util.Locale.CHINESE)
                                .getCollationKey(firstChar).sourceString
                        } catch (e: Exception) {
                            firstChar
                        }
                    }
                    else -> firstChar
                }
            })

            setupLetterIndexLayout()
            // 显示初始字母的搜索引擎列表
            showSearchEnginesByLetter(currentLetter)

        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error setting up views", e)
            Toast.makeText(this, "视图初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                // 启动悬浮窗服务并传入搜索内容
                val selectedEngine = sortedEngines.firstOrNull()
                selectedEngine?.let { engine ->
                    val intent = Intent(this, FloatingWindowService::class.java).apply {
                        putExtra("ENGINE_NAME", engine.name)
                        putExtra("ENGINE_URL", engine.url)
                        putExtra("ENGINE_ICON", engine.iconResId)
                        putExtra("SEARCH_QUERY", query)
                    }
                    startService(intent)
                    finish()
                }
            }
        }

        closeButton.setOnClickListener {
            finish()
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchButton.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun setupLetterIndexLayout() {
        letterIndexBar.onLetterSelectedListener = { _, letter ->
            currentLetter = letter
            showSearchEnginesByLetter(letter)
            // 震动反馈
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        // 更新字母标题
        letterTitle.text = letter.toString()

        // 清空搜索引擎列表
        previewEngineList.removeAllViews()

        // 查找所有匹配该字母的搜索引擎
        val matchingEngines = sortedEngines.filter { engine ->
            val firstChar = engine.name.first()
            when {
                firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                    firstChar.toString().uppercase() == letter.toString()
                firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                    try {
                        val format = HanyuPinyinOutputFormat().apply {
                            toneType = HanyuPinyinToneType.WITHOUT_TONE
                            caseType = HanyuPinyinCaseType.UPPERCASE
                            vCharType = HanyuPinyinVCharType.WITH_V
                        }
                        val pinyin = PinyinHelper.toHanyuPinyinStringArray(firstChar, format)
                        pinyin?.firstOrNull()?.firstOrNull()?.toString()?.uppercase() == letter.toString()
                    } catch (e: Exception) {
                        false
                    }
                }
                else -> false
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

                engineItem.findViewById<ImageView>(R.id.engine_icon)?.setImageResource(engine.iconResId)
                engineItem.findViewById<TextView>(R.id.engine_name)?.text = engine.name

                // 添加点击事件
                engineItem.setOnClickListener {
                    val query = searchInput.text.toString().trim()
                    val intent = Intent(this, FloatingWindowService::class.java).apply {
                        putExtra("ENGINE_NAME", engine.name)
                        putExtra("ENGINE_URL", engine.url)
                        putExtra("ENGINE_ICON", engine.iconResId)
                        putExtra("SEARCH_QUERY", query)
                    }
                    startService(intent)
                    finish()
                }

                previewEngineList.addView(engineItem)

                // 在每个搜索引擎项之间添加分隔线
                if (engine != matchingEngines.last()) {
                    val divider = View(this).apply {
                        setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        ).apply {
                            setMargins(16, 0, 16, 0)
                        }
                    }
                    previewEngineList.addView(divider)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(layoutThemeReceiver)
    }

    data class SearchEngine(
        val name: String,
        val url: String,
        val iconResId: Int
    )
} 