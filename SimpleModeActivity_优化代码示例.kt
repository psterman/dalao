/**
 * SimpleModeActivity 优化代码示例
 * 
 * 核心优化：
 * 1. 使用ViewStub延迟加载非对话tab
 * 2. 延迟初始化非关键组件
 * 3. 优化UI更新方法
 */

package com.example.aifloatingball

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewStub
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimpleModeActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SimpleModeActivity"
    }
    
    // ==================== 布局引用 - 支持延迟加载 ====================
    
    // 对话页面 - 默认显示，立即加载
    private lateinit var chatLayout: LinearLayout
    
    // 其他页面 - 延迟加载，使用可空类型
    private var aiAssistantCenterLayout: View? = null
    private var taskSelectionLayout: View? = null
    private var stepGuidanceLayout: View? = null
    private var promptPreviewLayout: View? = null
    private var voiceLayout: View? = null
    private var appSearchLayout: View? = null
    private var browserLayout: View? = null
    private var settingsLayout: View? = null
    
    // ViewStub引用
    private var aiAssistantCenterStub: ViewStub? = null
    private var taskSelectionStub: ViewStub? = null
    private var stepGuidanceStub: ViewStub? = null
    private var promptPreviewStub: ViewStub? = null
    private var voiceLayoutStub: ViewStub? = null
    private var appSearchStub: ViewStub? = null
    private var browserLayoutStub: ViewStub? = null
    private var settingsLayoutStub: ViewStub? = null
    
    // 初始化标记
    private var isChatInitialized = false
    private var isBrowserInitialized = false
    private var isSettingsInitialized = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "onCreate开始 - 优化版本")
        
        INSTANCE = this
        
        // ==================== 第一阶段：最小化初始化 ====================
        settingsManager = SettingsManager.getInstance(this)
        applyTheme()
        
        // 使用优化后的布局（更小，加载更快）
        setContentView(R.layout.activity_simple_mode_optimized)
        
        val inflateTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "布局inflate耗时: ${inflateTime}ms")
        
        // 只初始化对话tab（默认显示的tab）
        chatLayout = findViewById(R.id.chat_layout)
        
        // 获取ViewStub引用（不会立即inflate，不消耗资源）
        aiAssistantCenterStub = findViewById(R.id.ai_assistant_center_stub)
        taskSelectionStub = findViewById(R.id.task_selection_stub)
        stepGuidanceStub = findViewById(R.id.step_guidance_stub)
        promptPreviewStub = findViewById(R.id.prompt_preview_stub)
        voiceLayoutStub = findViewById(R.id.voice_layout_stub)
        appSearchStub = findViewById(R.id.app_search_stub)
        browserLayoutStub = findViewById(R.id.browser_layout_stub)
        settingsLayoutStub = findViewById(R.id.settings_layout_stub)
        
        // 应用UI颜色（只更新已加载的View）
        updateUIColorsOptimized()
        
        // 设置底部导航栏（始终可见）
        setupBottomNavigation()
        
        // 初始化对话tab
        initializeChatTab()
        
        // 立即显示对话tab
        showChat()
        
        val initTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "onCreate完成，总耗时: ${initTime}ms (优化前通常需要1500-2000ms)")
        
        // ==================== 第二阶段：延迟初始化非关键组件 ====================
        // 延迟300ms初始化，确保UI先显示
        handler.postDelayed({
            initializeNonCriticalComponents()
        }, 300)
    }
    
    /**
     * 初始化对话tab（默认显示的tab，需要立即初始化）
     */
    private fun initializeChatTab() {
        try {
            setupChat()
            isChatInitialized = true
            Log.d(TAG, "对话tab初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化对话tab失败", e)
        }
    }
    
    /**
     * 延迟初始化非关键组件
     * 借鉴微信的做法：在UI显示后再初始化非关键功能
     */
    private fun initializeNonCriticalComponents() {
        try {
            // 在后台线程初始化非UI相关的管理器
            lifecycleScope.launch(Dispatchers.Default) {
                // 初始化统一群聊管理器
                unifiedGroupChatManager = UnifiedGroupChatManager.getInstance(this@SimpleModeActivity)
                
                // 初始化语音提示分支管理器
                promptBranchManager = VoicePromptBranchManager(this@SimpleModeActivity, this@SimpleModeActivity)
                
                // 初始化统一WebView管理器
                unifiedWebViewManager = UnifiedWebViewManager()
                
                // 回到主线程设置监听器
                withContext(Dispatchers.Main) {
                    unifiedGroupChatManager?.addDataChangeListener(this@SimpleModeActivity)
                }
            }
            
            // 延迟初始化TTS（可能不需要立即使用）
            handler.postDelayed({
                initializeTTS()
            }, 500)
            
            // 延迟注册广播接收器
            handler.postDelayed({
                registerBroadcastReceiver()
                setupApiKeySyncReceiver()
                setupAddAIContactReceiver()
                setupAIChatUpdateReceiver()
                setupLinkActionReceiver()
                setupGroupChatCreatedReceiver()
            }, 200)
            
            Log.d(TAG, "非关键组件延迟初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "延迟初始化非关键组件失败", e)
        }
    }
    
    /**
     * 显示对话tab - 优化版本
     */
    private fun showChat() {
        try {
            currentState = UIState.CHAT
            
            // 隐藏其他已加载的页面
            hideOtherLoadedPages()
            
            // 显示对话页面
            chatLayout.visibility = View.VISIBLE
            
            // 如果尚未初始化，进行初始化
            if (!isChatInitialized) {
                initializeChatTab()
            }
            
            // 更新Tab颜色
            updateTabColors()
            
            Log.d(TAG, "显示对话tab完成")
        } catch (e: Exception) {
            Log.e(TAG, "显示对话tab失败", e)
        }
    }
    
    /**
     * 显示浏览器 - 使用ViewStub延迟加载
     * 浏览器是最重的页面，延迟加载效果最明显
     */
    private fun showBrowser() {
        try {
            val startTime = System.currentTimeMillis()
            currentState = UIState.BROWSER
            
            // 隐藏其他页面
            chatLayout.visibility = View.GONE
            hideOtherLoadedPages()
            
            // 延迟加载浏览器布局
            if (browserLayout == null) {
                Log.d(TAG, "首次访问浏览器，延迟加载布局")
                browserLayout = browserLayoutStub?.inflate()
                
                // 初始化浏览器相关的View
                initializeBrowserViews(browserLayout)
                setupBrowserWebView()
                isBrowserInitialized = true
                
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "浏览器布局加载耗时: ${loadTime}ms")
            } else {
                browserLayout?.visibility = View.VISIBLE
            }
            
            updateTabColors()
            Log.d(TAG, "显示浏览器完成")
        } catch (e: Exception) {
            Log.e(TAG, "显示浏览器失败", e)
        }
    }
    
    /**
     * 显示设置页面 - 使用ViewStub延迟加载
     */
    private fun showSettings() {
        try {
            currentState = UIState.SETTINGS
            
            // 隐藏其他页面
            chatLayout.visibility = View.GONE
            hideOtherLoadedPages()
            
            // 延迟加载设置布局
            if (settingsLayout == null) {
                Log.d(TAG, "首次访问设置，延迟加载布局")
                settingsLayout = settingsLayoutStub?.inflate()
                
                // 初始化设置页面的View
                initializeSettingsViews(settingsLayout)
                setupSettingsPage()
                isSettingsInitialized = true
            } else {
                settingsLayout?.visibility = View.VISIBLE
            }
            
            updateTabColors()
            Log.d(TAG, "显示设置页面完成")
        } catch (e: Exception) {
            Log.e(TAG, "显示设置页面失败", e)
        }
    }
    
    /**
     * 隐藏其他已加载的页面
     */
    private fun hideOtherLoadedPages() {
        aiAssistantCenterLayout?.visibility = View.GONE
        taskSelectionLayout?.visibility = View.GONE
        stepGuidanceLayout?.visibility = View.GONE
        promptPreviewLayout?.visibility = View.GONE
        voiceLayout?.visibility = View.GONE
        appSearchLayout?.visibility = View.GONE
        browserLayout?.visibility = View.GONE
        settingsLayout?.visibility = View.GONE
    }
    
    /**
     * 优化后的UI颜色更新方法
     * 只更新可见的View，减少递归遍历
     */
    private fun updateUIColorsOptimized() {
        try {
            val isDarkMode = isDarkMode()
            val backgroundColor = ContextCompat.getColor(this, R.color.simple_mode_background_light)
            
            Log.d(TAG, "更新UI颜色 - 优化版本，只更新可见View")
            
            // 更新窗口颜色
            window.statusBarColor = backgroundColor
            window.navigationBarColor = backgroundColor
            
            // 只更新当前可见的布局
            when (currentState) {
                UIState.CHAT -> {
                    chatLayout.setBackgroundColor(backgroundColor)
                    updateChatPageColors()
                }
                UIState.BROWSER -> {
                    browserLayout?.setBackgroundColor(backgroundColor)
                    updateBrowserPageColors()
                }
                UIState.SETTINGS -> {
                    settingsLayout?.setBackgroundColor(backgroundColor)
                    // 更新设置页面颜色
                }
                // ... 其他状态
                else -> {
                    // 更新根布局
                    findViewById<View>(android.R.id.content)?.setBackgroundColor(backgroundColor)
                }
            }
            
            // 更新底部导航栏（始终可见）
            updateBottomNavigationColors()
            updateTabColors()
            
        } catch (e: Exception) {
            Log.e(TAG, "更新UI颜色失败", e)
        }
    }
    
    /**
     * 初始化浏览器View引用
     * 在ViewStub inflate后调用
     */
    private fun initializeBrowserViews(parent: View?) {
        if (parent == null) return
        
        try {
            // 从inflate后的布局中查找View
            browserWebViewContainer = parent.findViewById(R.id.browser_webview_container)
            browserHomeContent = parent.findViewById(R.id.browser_home_content)
            browserSearchInput = parent.findViewById(R.id.browser_search_input)
            browserBtnMenu = parent.findViewById(R.id.browser_btn_menu)
            browserProgressBar = parent.findViewById(R.id.browser_progress_bar)
            // ... 其他浏览器相关的View
            
            Log.d(TAG, "浏览器View引用初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化浏览器View引用失败", e)
        }
    }
    
    /**
     * 初始化设置页面View引用
     */
    private fun initializeSettingsViews(parent: View?) {
        if (parent == null) return
        
        try {
            displayModeSpinner = parent.findViewById(R.id.display_mode_spinner)
            themeModeSpinner = parent.findViewById(R.id.theme_mode_spinner)
            // ... 其他设置相关的View
            
            Log.d(TAG, "设置页面View引用初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化设置页面View引用失败", e)
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        
        try {
            // 保存当前状态
            outState.putString(KEY_CURRENT_STATE, currentState.name)
            
            // 保存已加载的页面状态
            outState.putBoolean("is_browser_loaded", browserLayout != null)
            outState.putBoolean("is_settings_loaded", settingsLayout != null)
            // ... 其他页面的加载状态
            
        } catch (e: Exception) {
            Log.e(TAG, "保存状态失败", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 清理ViewStub引用
        aiAssistantCenterStub = null
        taskSelectionStub = null
        stepGuidanceStub = null
        promptPreviewStub = null
        voiceLayoutStub = null
        appSearchStub = null
        browserLayoutStub = null
        settingsLayoutStub = null
        
        INSTANCE = null
    }
}

