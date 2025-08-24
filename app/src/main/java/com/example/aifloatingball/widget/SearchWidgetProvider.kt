package com.example.aifloatingball.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.aifloatingball.R
import com.example.aifloatingball.ChatActivity
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.SettingsManager

/**
 * 搜索小组件提供器
 * 支持三种功能：AI对话、应用搜索、网络搜索
 */
class SearchWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "SearchWidgetProvider"
        
        // 自定义Action
        const val ACTION_AI_CHAT = "com.example.aifloatingball.WIDGET_AI_CHAT"
        const val ACTION_APP_SEARCH = "com.example.aifloatingball.WIDGET_APP_SEARCH"
        const val ACTION_WEB_SEARCH = "com.example.aifloatingball.WIDGET_WEB_SEARCH"
        const val ACTION_INPUT_CLICK = "com.example.aifloatingball.WIDGET_INPUT_CLICK"
        
        // 参数键
        const val EXTRA_QUERY = "query"
        const val EXTRA_WIDGET_ID = "widget_id"
        
        // 默认搜索内容
        private const val DEFAULT_SEARCH_QUERY = "你好"
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called with ${appWidgetIds.size} widgets")
        Log.d(TAG, "设备信息: ${WidgetCompatibilityHelper.getDeviceInfo()}")

        // 检查小组件权限
        if (!WidgetCompatibilityHelper.checkWidgetPermissions(context)) {
            Log.w(TAG, "小组件权限检查失败")
        }

        // 为每个小组件实例更新UI
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "小组件已启用")
        Log.d(TAG, "设备品牌: ${WidgetCompatibilityHelper.getDeviceBrand()}")

        // 引导用户添加小组件
        WidgetCompatibilityHelper.guideUserToAddWidget(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "小组件已禁用")
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        Log.d(TAG, "onReceive: ${intent.action}")
        
        when (intent.action) {
            ACTION_AI_CHAT -> {
                val query = getQueryFromIntent(intent)
                handleAIChatAction(context, query)
            }
            ACTION_APP_SEARCH -> {
                val query = getQueryFromIntent(intent)
                handleAppSearchAction(context, query)
            }
            ACTION_WEB_SEARCH -> {
                val query = getQueryFromIntent(intent)
                handleWebSearchAction(context, query)
            }
            ACTION_INPUT_CLICK -> {
                handleInputClickAction(context)
            }
        }
    }
    
    /**
     * 更新小组件UI
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "Updating widget $appWidgetId")
        
        // 创建RemoteViews
        val views = RemoteViews(context.packageName, R.layout.enhanced_search_widget_layout)
        
        // 设置输入框点击事件（用于打开输入界面）
        val inputIntent = createWidgetIntent(context, ACTION_INPUT_CLICK, appWidgetId).apply {
            // 添加时间戳确保每次Intent都是唯一的
            putExtra("click_timestamp", System.currentTimeMillis())
            // 添加随机数确保在小米手机上的唯一性
            putExtra("random_id", kotlin.random.Random.nextInt())
        }
        val inputPendingIntent = PendingIntent.getBroadcast(
            context,
            // 使用更复杂的请求码确保唯一性，特别是在小米手机上
            (appWidgetId * 1000 + System.currentTimeMillis() % 10000).toInt(),
            inputIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.search_widget_input_container, inputPendingIntent)
        
        // 设置AI对话按钮点击事件
        val aiChatIntent = createWidgetIntent(context, ACTION_AI_CHAT, appWidgetId)
        val aiChatPendingIntent = PendingIntent.getBroadcast(
            context, 
            appWidgetId * 10 + 1, 
            aiChatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.ai_chat_button, aiChatPendingIntent)
        
        // 设置应用搜索按钮点击事件
        val appSearchIntent = createWidgetIntent(context, ACTION_APP_SEARCH, appWidgetId)
        val appSearchPendingIntent = PendingIntent.getBroadcast(
            context, 
            appWidgetId * 10 + 2, 
            appSearchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.app_search_button, appSearchPendingIntent)
        
        // 设置网络搜索按钮点击事件
        val webSearchIntent = createWidgetIntent(context, ACTION_WEB_SEARCH, appWidgetId)
        val webSearchPendingIntent = PendingIntent.getBroadcast(
            context, 
            appWidgetId * 10 + 3, 
            webSearchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.web_search_button, webSearchPendingIntent)
        
        // 更新小组件
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "Widget $appWidgetId updated successfully")
    }
    
    /**
     * 创建小组件Intent
     */
    private fun createWidgetIntent(context: Context, action: String, widgetId: Int): Intent {
        return Intent(context, SearchWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
    }
    
    /**
     * 从Intent中获取搜索查询
     */
    private fun getQueryFromIntent(intent: Intent): String {
        // 由于AppWidget的限制，我们使用默认查询或从SharedPreferences获取
        return intent.getStringExtra(EXTRA_QUERY) ?: DEFAULT_SEARCH_QUERY
    }

    /**
     * 处理AI对话按钮点击
     */
    private fun handleAIChatAction(context: Context, query: String) {
        Log.d(TAG, "handleAIChatAction: query='$query'")

        try {
            // 获取用户首选的AI引擎
            val settingsManager = SettingsManager.getInstance(context)
            val enabledAIEngines = settingsManager.getEnabledAIEngines()
            
            // 选择AI引擎的优先级：智谱AI (Custom) > DeepSeek (API) > 其他已启用的AI
            val preferredAINames = listOf(
                "智谱AI (Custom)",
                "DeepSeek (API)", 
                "ChatGPT (Custom)",
                "Claude (Custom)",
                "通义千问 (Custom)"
            )
            
            val selectedAI = preferredAINames.firstOrNull { enabledAIEngines.contains(it) }
                ?: enabledAIEngines.firstOrNull()
                ?: "DeepSeek" // 最后的备用选项
            
            Log.d(TAG, "选择的AI引擎: $selectedAI")
            
            // 映射AI名称到引擎键
            val engineKey = when {
                selectedAI.contains("智谱", ignoreCase = true) -> "智谱AI (Custom)"
                selectedAI.contains("ChatGPT", ignoreCase = true) -> "ChatGPT (Custom)"
                selectedAI.contains("Claude", ignoreCase = true) -> "Claude (Custom)"
                selectedAI.contains("通义千问", ignoreCase = true) -> "通义千问 (Custom)"
                selectedAI.contains("DeepSeek", ignoreCase = true) -> "DeepSeek (API)"
                else -> "DeepSeek (API)"
            }
            
            // 根据选择的AI创建联系人
            val aiContact = when {
                selectedAI.contains("智谱", ignoreCase = true) -> ChatContact(
                    id = "widget_zhipu",
                    name = "智谱AI",
                    type = ContactType.AI,
                    lastMessage = "",
                    lastMessageTime = System.currentTimeMillis(),
                    customData = mutableMapOf()
                )
                selectedAI.contains("ChatGPT", ignoreCase = true) -> ChatContact(
                    id = "widget_chatgpt",
                    name = "ChatGPT",
                    type = ContactType.AI,
                    lastMessage = "",
                    lastMessageTime = System.currentTimeMillis(),
                    customData = mutableMapOf()
                )
                selectedAI.contains("Claude", ignoreCase = true) -> ChatContact(
                    id = "widget_claude",
                    name = "Claude",
                    type = ContactType.AI,
                    lastMessage = "",
                    lastMessageTime = System.currentTimeMillis(),
                    customData = mutableMapOf()
                )
                selectedAI.contains("通义千问", ignoreCase = true) -> ChatContact(
                    id = "widget_qianwen",
                    name = "通义千问",
                    type = ContactType.AI,
                    lastMessage = "",
                    lastMessageTime = System.currentTimeMillis(),
                    customData = mutableMapOf()
                )
                else -> ChatContact(
                    id = "widget_deepseek",
                    name = "DeepSeek",
                    type = ContactType.AI,
                    lastMessage = "",
                    lastMessageTime = System.currentTimeMillis(),
                    customData = mutableMapOf()
                )
            }

            // 启动ChatActivity并传递AI联系人和初始消息
            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ChatActivity.EXTRA_CONTACT, aiContact)
                putExtra("auto_send_message", query)
                putExtra("source", "桌面小组件")
                putExtra("engine_key", engineKey) // 传递正确的引擎键
            }

            context.startActivity(intent)
            Log.d(TAG, "AI对话启动成功: ${aiContact.name} - $query")

        } catch (e: Exception) {
            Log.e(TAG, "启动AI对话失败", e)
            // 备用方案：直接启动SimpleModeActivity
            fallbackToSimpleMode(context, query, "ai_chat")
        }
    }

    /**
     * 处理应用搜索按钮点击
     */
    private fun handleAppSearchAction(context: Context, query: String) {
        Log.d(TAG, "handleAppSearchAction: query='$query'")

        try {
            // 启动SimpleModeActivity并切换到应用搜索页面
            val intent = Intent(context, SimpleModeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("search_query", query)
                putExtra("search_mode", "app_search")
                putExtra("source", "桌面小组件")
                putExtra("auto_switch_to_app_search", true)
            }

            context.startActivity(intent)
            Log.d(TAG, "应用搜索启动成功: $query")

        } catch (e: Exception) {
            Log.e(TAG, "启动应用搜索失败", e)
            // 备用方案：启动默认的SimpleModeActivity
            fallbackToSimpleMode(context, query, "app_search")
        }
    }

    /**
     * 处理网络搜索按钮点击
     */
    private fun handleWebSearchAction(context: Context, query: String) {
        Log.d(TAG, "handleWebSearchAction: query='$query'")

        try {
            // 启动DualFloatingWebViewService进行网络搜索
            val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("engine_key", "baidu") // 默认使用百度搜索
                putExtra("search_source", "桌面小组件")
                putExtra("window_count", 1) // 单窗口搜索
            }

            context.startService(intent)
            Log.d(TAG, "网络搜索启动成功: $query")

        } catch (e: Exception) {
            Log.e(TAG, "启动网络搜索失败", e)
            // 备用方案：启动SimpleModeActivity
            fallbackToSimpleMode(context, query, "web_search")
        }
    }

    /**
     * 处理输入框点击
     */
    private fun handleInputClickAction(context: Context) {
        Log.d(TAG, "handleInputClickAction - 时间戳: ${System.currentTimeMillis()}")

        try {
            // 启动SimpleModeActivity让用户输入搜索内容
            val intent = Intent(context, SimpleModeActivity::class.java).apply {
                // 使用CLEAR_TOP和NEW_TASK确保在小米手机上也能正常工作
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("source", "桌面小组件")
                putExtra("show_input_dialog", true)
                // 添加时间戳确保每次Intent都是唯一的
                putExtra("timestamp", System.currentTimeMillis())
                // 添加额外的标识符确保Intent唯一性
                putExtra("click_id", "input_${System.currentTimeMillis()}")
            }

            context.startActivity(intent)
            Log.d(TAG, "输入界面启动成功")

        } catch (e: Exception) {
            Log.e(TAG, "启动输入界面失败", e)
            // 备用方案：直接启动SimpleModeActivity
            try {
                val fallbackIntent = Intent(context, SimpleModeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("source", "桌面小组件_备用")
                }
                context.startActivity(fallbackIntent)
                Log.d(TAG, "备用启动成功")
            } catch (e2: Exception) {
                Log.e(TAG, "备用启动也失败", e2)
            }
        }
    }

    /**
     * 备用方案：启动SimpleModeActivity
     */
    private fun fallbackToSimpleMode(context: Context, query: String, mode: String) {
        try {
            val intent = Intent(context, SimpleModeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("search_query", query)
                putExtra("search_mode", mode)
                putExtra("source", "桌面小组件_备用")
            }

            context.startActivity(intent)
            Log.d(TAG, "备用方案启动成功: mode=$mode, query=$query")

        } catch (e: Exception) {
            Log.e(TAG, "备用方案也失败了", e)
        }
    }
}
