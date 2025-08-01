package com.example.aifloatingball.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.*
import com.example.aifloatingball.adapter.ContentAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

/**
 * 多平台内容视图
 * 支持切换不同平台的内容卡片
 */
class MultiPlatformContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "MultiPlatformContentView"
    }
    
    private lateinit var tabLayout: TabLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnManagePlatforms: MaterialButton
    private lateinit var rvCurrentPlatformContent: RecyclerView

    private var contentAdapter: ContentAdapter? = null
    private var onActionListener: OnActionListener? = null
    
    interface OnActionListener {
        fun onPlatformSelected(platform: ContentPlatform)
        fun onManagePlatformsClick()
        fun onAddCreatorClick(platform: ContentPlatform)
        fun onRefreshClick(platform: ContentPlatform)
        fun onContentClick(content: Content)
        fun onCreatorClick(creator: Creator)
    }
    
    init {
        initView()
    }
    
    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.view_multi_platform_content, this, true)

        tabLayout = findViewById(R.id.tab_layout)
        contentContainer = findViewById(R.id.content_container)
        layoutEmpty = findViewById(R.id.layout_empty)
        btnManagePlatforms = findViewById(R.id.btn_manage_platforms)
        rvCurrentPlatformContent = findViewById(R.id.rv_current_platform_content)

        setupContentRecyclerView()
        setupClickListeners()
    }
    
    private fun setupContentRecyclerView() {
        contentAdapter = ContentAdapter(context)
        rvCurrentPlatformContent.adapter = contentAdapter
        rvCurrentPlatformContent.layoutManager = LinearLayoutManager(context)

        contentAdapter?.setOnContentClickListener { content ->
            onActionListener?.onContentClick(content)
        }
    }
    
    private fun setupClickListeners() {
        btnManagePlatforms.setOnClickListener {
            onActionListener?.onManagePlatformsClick()
        }
    }
    
    fun setOnActionListener(listener: OnActionListener) {
        this.onActionListener = listener
    }
    
    private var currentPlatforms = listOf<ContentPlatform>()
    private var currentPlatform: ContentPlatform? = null
    private var platformContents = mutableMapOf<ContentPlatform, List<Content>>()

    /**
     * 设置支持的平台列表
     */
    fun setSupportedPlatforms(platforms: List<ContentPlatform>) {
        currentPlatforms = platforms
        if (platforms.isEmpty()) {
            showEmptyState()
        } else {
            showPlatformTabs(platforms)
            // 默认选择第一个平台
            if (platforms.isNotEmpty()) {
                currentPlatform = platforms[0]
                setupTabLayout()
            }
        }
    }

    private fun setupTabLayout() {
        tabLayout.removeAllTabs()
        currentPlatforms.forEach { platform ->
            val tab = tabLayout.newTab()
            tab.text = platform.displayName
            tab.setIcon(platform.iconRes)
            tabLayout.addTab(tab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: return
                if (position < currentPlatforms.size) {
                    currentPlatform = currentPlatforms[position]
                    onActionListener?.onPlatformSelected(currentPlatform!!)
                    updateCurrentPlatformView()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * 更新指定平台的内容
     */
    fun updatePlatformContents(platform: ContentPlatform, contents: List<Content>) {
        platformContents[platform] = contents
        if (platform == currentPlatform) {
            updateCurrentPlatformView()
        }
    }

    /**
     * 更新指定平台的创作者列表
     */
    fun updatePlatformCreators(platform: ContentPlatform, creators: List<Creator>) {
        // 暂时不处理创作者列表
    }

    /**
     * 显示指定平台的加载状态
     */
    fun showPlatformLoading(platform: ContentPlatform) {
        if (platform == currentPlatform) {
            // 显示加载状态
        }
    }

    /**
     * 切换到指定平台
     */
    fun switchToPlatform(platform: ContentPlatform) {
        val position = currentPlatforms.indexOf(platform)
        if (position >= 0) {
            tabLayout.selectTab(tabLayout.getTabAt(position))
        }
    }

    /**
     * 获取当前选中的平台
     */
    fun getCurrentPlatform(): ContentPlatform? {
        return currentPlatform
    }

    private fun updateCurrentPlatformView() {
        currentPlatform?.let { platform ->
            val contents = platformContents[platform] ?: emptyList()
            contentAdapter?.updateContents(contents.take(5)) // 只显示前5条

            if (contents.isEmpty()) {
                rvCurrentPlatformContent.visibility = View.GONE
                // 可以显示空状态
            } else {
                rvCurrentPlatformContent.visibility = View.VISIBLE
            }
        }
    }

    private fun showEmptyState() {
        tabLayout.visibility = View.GONE
        contentContainer.visibility = View.GONE
        layoutEmpty.visibility = View.VISIBLE
    }

    private fun showPlatformTabs(platforms: List<ContentPlatform>) {
        layoutEmpty.visibility = View.GONE
        tabLayout.visibility = View.VISIBLE
        contentContainer.visibility = View.VISIBLE
    }
}
