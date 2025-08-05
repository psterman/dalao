package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.PresetRSSAdapter
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class AddRSSContactActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AddRSSContactActivity"
        const val EXTRA_RSS_CONTACT = "extra_rss_contact"
    }

    private lateinit var searchInput: TextInputEditText
    private lateinit var searchButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var presetRSSList: RecyclerView
    private lateinit var customNameInput: TextInputEditText
    private lateinit var customDescriptionInput: TextInputEditText
    private lateinit var customRSSUrlInput: TextInputEditText
    private lateinit var addCustomButton: MaterialButton

    private lateinit var presetRSSAdapter: PresetRSSAdapter
    private var allPresetRSS = mutableListOf<PresetRSS>()
    private var filteredPresetRSS = mutableListOf<PresetRSS>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_rss_contact)

        initializeViews()
        setupPresetRSS()
        setupListeners()
    }

    private fun initializeViews() {
        searchInput = findViewById(R.id.search_input)
        searchButton = findViewById(R.id.search_button)
        backButton = findViewById(R.id.back_button)
        saveButton = findViewById(R.id.save_button)
        presetRSSList = findViewById(R.id.preset_rss_list)
        customNameInput = findViewById(R.id.custom_name_input)
        customDescriptionInput = findViewById(R.id.custom_description_input)
        customRSSUrlInput = findViewById(R.id.custom_rss_url_input)
        addCustomButton = findViewById(R.id.add_custom_button)

        // 设置RecyclerView
        presetRSSAdapter = PresetRSSAdapter(
            onRSSClick = { presetRSS ->
                addPresetRSSToContacts(presetRSS)
            }
        )
        presetRSSList.layoutManager = LinearLayoutManager(this)
        presetRSSList.adapter = presetRSSAdapter
    }

    private fun setupPresetRSS() {
        // 预设RSS源列表 - 按分类组织
        allPresetRSS = mutableListOf(
            // 科技新闻
            PresetRSS("科技新闻", "最新科技资讯和产品评测", "https://www.cnbeta.com/backend.php"),
            PresetRSS("36氪", "创业公司新闻和投资动态", "https://36kr.com/feed"),
            PresetRSS("虎嗅", "商业科技资讯", "https://www.huxiu.com/rss/0.xml"),
            PresetRSS("爱范儿", "科技生活方式", "https://www.ifanr.com/feed"),
            PresetRSS("少数派", "数字生活指南", "https://sspai.com/feed"),
            
            // 编程开发
            PresetRSS("阮一峰的网络日志", "编程学习资源和教程", "https://www.ruanyifeng.com/blog/atom.xml"),
            PresetRSS("掘金", "开发者社区", "https://juejin.cn/rss"),
            PresetRSS("InfoQ", "软件开发资讯", "https://www.infoq.cn/feed"),
            PresetRSS("开源中国", "开源软件资讯", "https://www.oschina.net/news/rss"),
            PresetRSS("GitHub Trending", "GitHub热门项目", "https://github.com/trending.atom"),
            
            // 人工智能
            PresetRSS("机器之心", "AI技术发展和应用", "https://www.jiqizhixin.com/rss"),
            PresetRSS("AI研习社", "人工智能学习社区", "https://ai.yanxishe.com/rss"),
            PresetRSS("量子位", "AI科技资讯", "https://www.qbitai.com/feed"),
            PresetRSS("AI前线", "人工智能前沿资讯", "https://www.infoq.cn/feed/ai"),
            
            // 设计创意
            PresetRSS("Behance", "UI/UX设计灵感和趋势", "https://www.behance.net/feeds/projects"),
            PresetRSS("Dribbble", "设计师作品展示", "https://dribbble.com/shots/popular.rss"),
            PresetRSS("站酷", "设计师交流平台", "https://www.zcool.com.cn/rss"),
            PresetRSS("UI中国", "UI设计资讯", "https://www.ui.cn/rss"),
            
            // 产品管理
            PresetRSS("人人都是产品经理", "产品管理和用户体验", "https://www.woshipm.com/feed"),
            PresetRSS("产品经理社区", "产品经理学习平台", "https://www.pmcaff.com/rss"),
            PresetRSS("产品壹佰", "产品经理资讯", "https://www.chanpin100.com/rss"),
            
            // 创业投资
            PresetRSS("创业邦", "创业资讯和投资动态", "https://www.cyzone.cn/rss"),
            PresetRSS("投资界", "投资资讯", "https://www.pedaily.cn/rss"),
            PresetRSS("IT桔子", "创业公司数据库", "https://www.itjuzi.com/rss"),
            
            // 开发者工具
            PresetRSS("Product Hunt", "开发工具和效率提升", "https://www.producthunt.com/feed"),
            PresetRSS("V2EX", "程序员社区", "https://www.v2ex.com/feed/tab/tech.xml"),
            PresetRSS("SegmentFault", "开发者社区", "https://segmentfault.com/feed"),
            
            // 科技媒体
            PresetRSS("钛媒体", "科技商业媒体", "https://www.tmtpost.com/rss.xml"),
            PresetRSS("雷锋网", "科技资讯", "https://www.leiphone.com/rss"),
            PresetRSS("极客公园", "科技资讯", "https://www.geekpark.net/rss"),
            
            // 学术研究
            PresetRSS("arXiv", "学术论文预印本", "http://export.arxiv.org/rss/cs.AI"),
            PresetRSS("Papers With Code", "机器学习论文", "https://paperswithcode.com/rss"),
            PresetRSS("Google AI Blog", "Google AI研究", "https://ai.googleblog.com/feeds/posts/default"),
            
            // 行业资讯
            PresetRSS("TechCrunch", "科技创业资讯", "https://techcrunch.com/feed"),
            PresetRSS("The Verge", "科技评测", "https://www.theverge.com/rss/index.xml"),
            PresetRSS("Wired", "科技文化", "https://www.wired.com/feed/rss"),
            PresetRSS("MIT Technology Review", "MIT科技评论", "https://www.technologyreview.com/feed/")
        )
        filteredPresetRSS = allPresetRSS.toMutableList()
        presetRSSAdapter.updateRSS(filteredPresetRSS)
    }

    private fun setupListeners() {
        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 保存按钮
        saveButton.setOnClickListener {
            // 这里可以保存当前选择的状态
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        // 搜索功能
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString() ?: "")
            }
        })

        searchButton.setOnClickListener {
            performSearch(searchInput.text?.toString() ?: "")
        }

        // 添加自定义RSS按钮
        addCustomButton.setOnClickListener {
            addCustomRSSToContacts()
        }
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            filteredPresetRSS = allPresetRSS.toMutableList()
        } else {
            // 支持按分类搜索
            val categoryKeywords = mapOf(
                "科技" to listOf("科技新闻", "36氪", "虎嗅", "爱范儿", "少数派", "钛媒体", "雷锋网", "极客公园"),
                "编程" to listOf("阮一峰", "掘金", "InfoQ", "开源中国", "GitHub", "V2EX", "SegmentFault"),
                "AI" to listOf("机器之心", "AI研习社", "量子位", "AI前线", "arXiv", "Papers With Code", "Google AI"),
                "设计" to listOf("Behance", "Dribbble", "站酷", "UI中国"),
                "产品" to listOf("人人都是产品经理", "产品经理社区", "产品壹佰"),
                "创业" to listOf("创业邦", "投资界", "IT桔子", "TechCrunch"),
                "工具" to listOf("Product Hunt", "开发者工具"),
                "学术" to listOf("arXiv", "Papers With Code", "Google AI Blog", "MIT Technology Review"),
                "媒体" to listOf("The Verge", "Wired", "MIT Technology Review")
            )
            
            filteredPresetRSS = allPresetRSS.filter { rss ->
                val nameMatch = rss.name.contains(query, ignoreCase = true)
                val descMatch = rss.description.contains(query, ignoreCase = true)
                val categoryMatch = categoryKeywords.any { (category, keywords) ->
                    category.contains(query, ignoreCase = true) && keywords.any { keyword ->
                        rss.name.contains(keyword, ignoreCase = true)
                    }
                }
                
                nameMatch || descMatch || categoryMatch
            }.toMutableList()
        }
        presetRSSAdapter.updateRSS(filteredPresetRSS)
    }

    private fun addPresetRSSToContacts(presetRSS: PresetRSS) {
        val contact = ChatContact(
            id = "rss_${System.currentTimeMillis()}",
            name = presetRSS.name,
            type = ContactType.RSS,
            description = presetRSS.description,
            isOnline = true,
            customData = mapOf("rss_url" to presetRSS.rssUrl)
        )

        // 返回结果给SimpleModeActivity
        val intent = Intent()
        intent.putExtra(EXTRA_RSS_CONTACT, contact)
        setResult(RESULT_OK, intent)
        
        Toast.makeText(this, "已添加 ${presetRSS.name}", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "添加预设RSS: ${presetRSS.name}")
    }

    private fun addCustomRSSToContacts() {
        val name = customNameInput.text?.toString()?.trim()
        val description = customDescriptionInput.text?.toString()?.trim()
        val rssUrl = customRSSUrlInput.text?.toString()?.trim()

        if (name.isNullOrEmpty()) {
            Toast.makeText(this, "请输入RSS源名称", Toast.LENGTH_SHORT).show()
            return
        }

        if (rssUrl.isNullOrEmpty()) {
            Toast.makeText(this, "请输入RSS源地址", Toast.LENGTH_SHORT).show()
            return
        }

        val contact = ChatContact(
            id = "rss_custom_${System.currentTimeMillis()}",
            name = name,
            type = ContactType.RSS,
            description = description ?: "自定义RSS源",
            isOnline = true,
            customData = mapOf("rss_url" to rssUrl)
        )

        // 返回结果给SimpleModeActivity
        val intent = Intent()
        intent.putExtra(EXTRA_RSS_CONTACT, contact)
        setResult(RESULT_OK, intent)

        Toast.makeText(this, "已添加自定义RSS源", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "添加自定义RSS: $name")
    }

    /**
     * 预设RSS源数据类
     */
    data class PresetRSS(
        val name: String,
        val description: String,
        val rssUrl: String
    )
} 