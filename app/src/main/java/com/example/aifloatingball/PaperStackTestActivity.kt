package com.example.aifloatingball

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.webview.PaperStackWebViewManager

/**
 * 纸堆WebView测试Activity
 * 用于测试和演示纸堆WebView功能
 */
class PaperStackTestActivity : AppCompatActivity() {
    
    private lateinit var paperStackManager: PaperStackWebViewManager
    private lateinit var paperStackContainer: FrameLayout
    private lateinit var paperCountText: TextView
    private lateinit var addPaperButton: Button
    private lateinit var closeAllPapersButton: Button
    private lateinit var hintText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paper_stack_test)
        
        initViews()
        setupPaperStackManager()
        setupClickListeners()
    }
    
    private fun initViews() {
        paperStackContainer = findViewById(R.id.paper_stack_container)
        paperCountText = findViewById(R.id.paper_count_text)
        addPaperButton = findViewById(R.id.btn_add_paper)
        closeAllPapersButton = findViewById(R.id.btn_close_all_papers)
        hintText = findViewById(R.id.paper_stack_hint)
    }
    
    private fun setupPaperStackManager() {
        paperStackManager = PaperStackWebViewManager(this, paperStackContainer)
        
        // 设置监听器
        paperStackManager.setOnTabCreatedListener { tab ->
            android.util.Log.d("PaperStackTest", "标签页创建完成: ${tab.title}")
        }
        
        paperStackManager.setOnTabSwitchedListener { tab, index ->
            updatePaperCountText()
            android.util.Log.d("PaperStackTest", "切换到标签页: $index, 标题: ${tab.title}")
        }
        
        // 添加第一个标签页
        addNewTab()
    }
    
    private fun setupClickListeners() {
        addPaperButton.setOnClickListener {
            addNewTab()
        }
        
        closeAllPapersButton.setOnClickListener {
            closeAllTabs()
        }
    }
    
    private fun addNewTab() {
        val testUrls = listOf(
            "https://www.baidu.com",
            "https://www.google.com",
            "https://www.bing.com",
            "https://www.sogou.com",
            "https://www.360.cn"
        )
        
        val testTitles = listOf(
            "百度",
            "谷歌",
            "必应",
            "搜狗",
            "360搜索"
        )
        
        val urlIndex = paperStackManager.getTabCount() % testUrls.size
        val newTab = paperStackManager.addTab(testUrls[urlIndex], testTitles[urlIndex])
        
        if (newTab != null) {
            updatePaperCountText()
            hideHint()
            android.util.Log.d("PaperStackTest", "添加新标签页，当前数量: ${paperStackManager.getTabCount()}")
        }
    }
    
    private fun closeAllTabs() {
        paperStackManager.cleanup()
        showHint()
        updatePaperCountText()
        android.util.Log.d("PaperStackTest", "关闭所有标签页")
    }
    
    private fun updatePaperCountText() {
        val count = paperStackManager.getTabCount()
        paperCountText.text = "标签页数量: $count"
    }
    
    private fun showHint() {
        hintText.visibility = View.VISIBLE
    }
    
    private fun hideHint() {
        hintText.visibility = View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        paperStackManager.cleanup()
    }
}
