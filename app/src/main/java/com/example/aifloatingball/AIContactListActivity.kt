package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.activity.AIApiConfigActivity
import com.example.aifloatingball.adapter.AIContactListAdapter

import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.model.GroupChat
import com.example.aifloatingball.model.GroupMember
import com.example.aifloatingball.model.MemberType
import com.example.aifloatingball.model.MemberRole
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.manager.GroupChatManager
import com.example.aifloatingball.manager.UnifiedGroupChatManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class AIContactListActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AIContactListActivity"
        const val EXTRA_AI_CONTACT = "extra_ai_contact"
    }

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var menuButton: ImageButton
    private lateinit var aiContactList: RecyclerView
    private lateinit var addCustomButton: MaterialButton
    private lateinit var createGroupButton: MaterialButton

    private lateinit var aiContactAdapter: AIContactListAdapter
    private lateinit var groupChatManager: GroupChatManager
    private lateinit var unifiedGroupChatManager: UnifiedGroupChatManager
    private var allAIContacts = mutableListOf<ChatContact>()
    private var showOnlyConfiguredAIs = false // 默认显示所有AI，包括未配置的

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_contact_list)

        // 初始化GroupChatManager
        groupChatManager = GroupChatManager.getInstance(this)
        
        // 初始化UnifiedGroupChatManager
        unifiedGroupChatManager = UnifiedGroupChatManager.getInstance(this)
        
        initializeViews()
        setupAIContacts()
        setupListeners()
    }
    
    override fun onResume() {
        super.onResume()
        // 页面恢复时刷新联系人列表，确保显示最新的配置状态
        Log.d(TAG, "onResume: 刷新联系人列表")
        updateContactList()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        titleText = findViewById(R.id.title_text)
        menuButton = findViewById(R.id.menu_button)
        aiContactList = findViewById(R.id.ai_contact_list)
        addCustomButton = findViewById(R.id.add_custom_button)
        createGroupButton = findViewById(R.id.create_group_button)

        titleText.text = "AI联系人列表"

        // 设置标题栏菜单
        setupTitleBarMenu()
    }

    private fun setupAIContacts() {
        // 初始化AI联系人列表
        allAIContacts = generateAIContactsList()
        
        // 设置RecyclerView
        aiContactAdapter = AIContactListAdapter(
            onContactClick = { contact ->
                // 处理联系人点击事件
                openChatWithContact(contact)
            },
            onApiKeyClick = { contact ->
                // 处理API密钥配置点击事件
                showApiKeyConfigDialog(contact)
            },
            onContactLongClick = { contact ->
                // 处理联系人长按事件
                showContactOptionsDialog(contact)
                true
            }
        )

        aiContactList.apply {
            layoutManager = LinearLayoutManager(this@AIContactListActivity)
            adapter = aiContactAdapter
        }

        // 更新适配器
        updateContactList()
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        addCustomButton.setOnClickListener {
            // 跳转到AI API配置页面
            val intent = Intent(this, AIApiConfigActivity::class.java)
            startActivity(intent)
        }

        createGroupButton.setOnClickListener {
            // 跳转到群聊创建页面
            showCreateGroupDialog()
        }
    }

    /**
     * 设置标题栏菜单
     */
    private fun setupTitleBarMenu() {
        menuButton.setOnClickListener {
            showDisplayModeDialog()
        }
    }

    /**
     * 显示显示模式选择对话框
     */
    private fun showDisplayModeDialog() {
        val options = arrayOf(
            if (showOnlyConfiguredAIs) "✓ 只显示已配置API的AI" else "只显示已配置API的AI",
            if (!showOnlyConfiguredAIs) "✓ 显示所有AI" else "显示所有AI"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("显示模式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        showOnlyConfiguredAIs = true
                        updateContactList()
                    }
                    1 -> {
                        showOnlyConfiguredAIs = false
                        updateContactList()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 更新联系人列表
     */
    private fun updateContactList() {
        // 重新生成联系人列表，确保从SettingsManager读取最新值
        allAIContacts = generateAIContactsList()
        
        val filteredContacts = if (showOnlyConfiguredAIs) {
            allAIContacts.filter { contact ->
                contact.customData["is_configured"] == "true"
            }
        } else {
            allAIContacts
        }
        
        Log.d(TAG, "更新联系人列表: 总数=${allAIContacts.size}, 过滤后=${filteredContacts.size}, 显示模式=${if (showOnlyConfiguredAIs) "只显示已配置" else "显示所有"}")
        Log.d(TAG, "豆包Pro联系人: ${allAIContacts.find { it.name == "豆包Pro" }?.let { "存在, isConfigured=${it.customData["is_configured"]}, apiKey长度=${(it.customData["api_key"] as? String)?.length ?: 0}" } ?: "不存在"}")

        aiContactAdapter.updateContacts(filteredContacts)
    }

    /**
     * 生成AI联系人列表
     */
    private fun generateAIContactsList(): MutableList<ChatContact> {
        val contacts = mutableListOf<ChatContact>()
        
        // 定义所有可用的AI助手
        val availableAIs = listOf(
            "临时专线",
            "DeepSeek",
            "ChatGPT",
            "Claude",
            "Gemini",
            "智谱AI",
            "文心一言",
            "通义千问",
            "讯飞星火",
            "Kimi",
            "豆包Pro"
        )

        availableAIs.forEach { aiName ->
            val isTempService = aiName == "临时专线"
            // 获取原始API密钥（不排除默认值），用于保存到customData
            val rawApiKey = if (isTempService) "" else getRawApiKeyForAI(aiName)
            // 获取处理后的API密钥（排除默认值），用于判断是否已配置
            val apiKey = if (isTempService) "" else getApiKeyForAI(aiName)
            
            // 对于豆包Pro，额外检查SharedPreferences中是否明确保存过
            val isConfigured = if (aiName == "豆包Pro" && !isTempService) {
                val settingsManager = SettingsManager.getInstance(this)
                val hasSavedKey = settingsManager.getSharedPreferences().contains("doubao_api_key")
                val apiKeyNotEmpty = apiKey.isNotEmpty()
                val result = hasSavedKey && apiKeyNotEmpty
                Log.d(TAG, "🔍 豆包Pro配置检查: hasSavedKey=$hasSavedKey, apiKeyNotEmpty=$apiKeyNotEmpty, isConfigured=$result")
                result
            } else {
                isTempService || apiKey.isNotEmpty()
            }
            
            Log.d(TAG, "生成联系人: $aiName, rawApiKey长度: ${rawApiKey.length}, apiKey长度: ${apiKey.length}, isConfigured: $isConfigured")
            
            // 特别检查豆包Pro
            if (aiName == "豆包Pro") {
                Log.d(TAG, "🔍 豆包Pro详细信息: rawApiKey='${rawApiKey.take(20)}...', apiKey='${apiKey.take(20)}...', isConfigured=$isConfigured")
            }

            // 获取已保存的API URL和模型，如果没有则使用默认值
            val settingsManager = SettingsManager.getInstance(this)
            val apiUrl = when (aiName.lowercase()) {
                "豆包pro", "doubao" -> {
                    settingsManager.getString("doubao_api_url", "") ?: getDefaultApiUrl(aiName)
                }
                else -> {
                    val urlKey = when (aiName.lowercase()) {
                        "deepseek" -> "deepseek_api_url"
                        "chatgpt" -> "chatgpt_api_url"
                        "claude" -> "claude_api_url"
                        "gemini" -> "gemini_api_url"
                        "智谱ai", "智谱AI" -> "zhipu_ai_api_url"
                        "文心一言" -> "wenxin_api_url"
                        "通义千问" -> "qianwen_api_url"
                        "讯飞星火" -> "xinghuo_api_url"
                        "kimi" -> "kimi_api_url"
                        else -> "${aiName.lowercase()}_api_url"
                    }
                    settingsManager.getString(urlKey, "") ?: getDefaultApiUrl(aiName)
                }
            }
            
            val model = when (aiName.lowercase()) {
                "豆包pro", "doubao" -> {
                    val modelId = settingsManager.getDoubaoModelId()
                    if (modelId == "ep-Needs-Your-Endpoint-ID") getDefaultModel(aiName) else modelId
                }
                else -> {
                    val modelKey = when (aiName.lowercase()) {
                        "deepseek" -> "deepseek_model"
                        "chatgpt" -> "chatgpt_model"
                        "claude" -> "claude_model"
                        "gemini" -> "gemini_model"
                        "智谱ai", "智谱AI" -> "zhipu_ai_model"
                        "文心一言" -> "wenxin_model"
                        "通义千问" -> "qianwen_model"
                        "讯飞星火" -> "xinghuo_model"
                        "kimi" -> "kimi_model"
                        else -> "${aiName.lowercase()}_model"
                    }
                    settingsManager.getString(modelKey, "") ?: getDefaultModel(aiName)
                }
            }

            // 获取真实的最后对话内容
            val lastChatMessage = getLastChatMessage(aiName)
            val displayMessage = when {
                isTempService -> "免费AI服务，无需配置"
                !isConfigured -> "点击配置API密钥"
                lastChatMessage.isNotEmpty() -> lastChatMessage
                else -> "开始新对话"
            }
            
            val contact = ChatContact(
                id = "ai_${aiName.lowercase().replace(" ", "_")}",
                name = aiName,
                type = ContactType.AI,
                description = null, // 不显示描述
                isOnline = isConfigured,
                lastMessage = displayMessage,
                lastMessageTime = getLastChatTime(aiName),
                unreadCount = 0,
                isPinned = isConfigured,
                customData = mapOf(
                    "api_url" to apiUrl,
                    "api_key" to rawApiKey, // 保存原始API密钥（包括默认值）
                    "model" to model,
                    "is_configured" to isConfigured.toString(),
                    "is_temp_service" to isTempService.toString()
                )
            )
            contacts.add(contact)
        }

        return contacts
    }

    /**
     * 显示API密钥配置对话框
     */
    private fun showApiKeyConfigDialog(contact: ChatContact) {
        try {
            val aiName = contact.name
            val isTempService = contact.customData["is_temp_service"] == "true"
            val isConfigured = contact.customData["is_configured"] == "true"
            
            if (isTempService) {
                // 临时专线不需要配置API密钥
                Toast.makeText(this, "临时专线是免费服务，无需配置API密钥", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (isConfigured) {
                // 如果已配置，显示修改对话框
                showEditApiKeyDialog(contact)
            } else {
                // 如果未配置，显示添加对话框
                showAddApiKeyDialog(contact)
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示API密钥配置对话框失败", e)
            Toast.makeText(this, "显示配置对话框失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示添加API密钥对话框
     */
    private fun showAddApiKeyDialog(contact: ChatContact) {
        val aiName = contact.name
        
        // 创建带有 Material 主题的上下文
        val wrappedContext = android.view.ContextThemeWrapper(this, R.style.AppTheme_Dialog)
        val dialogLayout = LayoutInflater.from(wrappedContext).inflate(R.layout.dialog_api_key_config, null)
        val apiKeyInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_key_input)
        val apiUrlInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_url_input)
        val modelInput = dialogLayout.findViewById<TextInputEditText>(R.id.model_input)
        
        // 设置默认值
        apiUrlInput.setText(getDefaultApiUrl(aiName))
        val defaultModel = getDefaultModel(aiName)
        modelInput.setText(defaultModel)
        
        // 设置提示文本
        apiKeyInput.hint = "请输入${aiName}的API密钥"
        
        // 对于豆包Pro，需要特殊提示
        val dialogMessage = if (aiName == "豆包Pro") {
            "请填写豆包Pro的API密钥、API URL和模型ID（Endpoint ID）"
        } else {
            "请填写${aiName}的API密钥以激活对话功能"
        }
        
        MaterialAlertDialogBuilder(wrappedContext)
            .setTitle("配置${aiName}")
            .setMessage(dialogMessage)
            .setView(dialogLayout)
            .setPositiveButton("确定") { _, _ ->
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val apiUrl = apiUrlInput.text?.toString()?.trim() ?: ""
                val model = modelInput.text?.toString()?.trim() ?: ""
                
                if (apiKey.isNotEmpty()) {
                    // 保存API密钥
                    saveApiKeyForAI(aiName, apiKey, apiUrl, model)
                    
                    // 重新生成联系人列表，确保从SettingsManager读取最新值
                    allAIContacts = generateAIContactsList()
                    updateContactList()
                    
                    Toast.makeText(this, "${aiName}配置成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请输入API密钥", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示编辑API密钥对话框
     */
    private fun showEditApiKeyDialog(contact: ChatContact) {
        val aiName = contact.name
        val currentApiKey = contact.customData["api_key"] ?: ""
        val currentApiUrl = contact.customData["api_url"] ?: ""
        val currentModel = contact.customData["model"] ?: ""
        
        // 创建带有 Material 主题的上下文
        val wrappedContext = android.view.ContextThemeWrapper(this, R.style.AppTheme_Dialog)
        val dialogLayout = LayoutInflater.from(wrappedContext).inflate(R.layout.dialog_api_key_config, null)
        val apiKeyInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_key_input)
        val apiUrlInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_url_input)
        val modelInput = dialogLayout.findViewById<TextInputEditText>(R.id.model_input)
        
        // 设置当前值
        apiKeyInput.setText(currentApiKey)
        apiUrlInput.setText(currentApiUrl)
        modelInput.setText(currentModel)
        
        // 设置提示文本
        apiKeyInput.hint = "请输入${aiName}的API密钥"
        
        MaterialAlertDialogBuilder(wrappedContext)
            .setTitle("修改${aiName}配置")
            .setMessage("修改${aiName}的API配置")
            .setView(dialogLayout)
            .setPositiveButton("确定") { _, _ ->
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val apiUrl = apiUrlInput.text?.toString()?.trim() ?: ""
                val model = modelInput.text?.toString()?.trim() ?: ""
                
                if (apiKey.isNotEmpty()) {
                    // 保存API密钥
                    saveApiKeyForAI(aiName, apiKey, apiUrl, model)
                    
                    // 重新生成联系人列表，确保从SettingsManager读取最新值
                    allAIContacts = generateAIContactsList()
                    updateContactList()
                    
                    Toast.makeText(this, "${aiName}配置已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请输入API密钥", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("删除配置") { _, _ ->
                // 删除API配置
                deleteApiKeyForAI(aiName)
                
                // 重新生成联系人列表，确保从SettingsManager读取最新值
                allAIContacts = generateAIContactsList()
                updateContactList()
                
                Toast.makeText(this, "${aiName}配置已删除", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /**
     * 显示添加自定义AI对话框
     */
    private fun showAddCustomAIDialog() {
        // 创建自定义布局
        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.dialog_custom_ai_config, null)
        val nameInput = dialogLayout.findViewById<TextInputEditText>(R.id.name_input)
        val apiKeyInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_key_input)
        val apiUrlInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_url_input)
        val modelInput = dialogLayout.findViewById<TextInputEditText>(R.id.model_input)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("添加自定义AI")
            .setMessage("添加自定义AI助手")
            .setView(dialogLayout)
            .setPositiveButton("确定") { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val apiUrl = apiUrlInput.text?.toString()?.trim() ?: ""
                val model = modelInput.text?.toString()?.trim() ?: ""
                
                if (name.isNotEmpty() && apiKey.isNotEmpty() && apiUrl.isNotEmpty()) {
                    // 保存自定义AI
                    saveCustomAI(name, apiKey, apiUrl, model)
                    Toast.makeText(this, "自定义AI添加成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示联系人选项对话框
     */
    private fun showContactOptionsDialog(contact: ChatContact) {
        val options = arrayOf("测试API连接", "查看配置信息", "删除配置")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("${contact.name}选项")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> testApiConnection(contact)
                    1 -> showConfigInfo(contact)
                    2 -> deleteApiKeyForAI(contact.name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 测试API连接
     */
    private fun testApiConnection(contact: ChatContact) {
        val apiKey = contact.customData["api_key"] ?: ""
        val apiUrl = contact.customData["api_url"] ?: ""
        val model = contact.customData["model"] ?: ""
        
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先配置API密钥", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (apiUrl.isEmpty()) {
            Toast.makeText(this, "API地址未配置", Toast.LENGTH_SHORT).show()
            return
        }
        
        val apiTestManager = com.example.aifloatingball.manager.ApiTestManager(this)
        
        apiTestManager.testApiConnection(
            aiName = contact.name,
            apiKey = apiKey,
            apiUrl = apiUrl,
            model = model.ifEmpty { getDefaultModel(contact.name) },
            callback = object : com.example.aifloatingball.manager.ApiTestManager.TestCallback {
                override fun onTestStart() {
                    Toast.makeText(this@AIContactListActivity, "正在测试${contact.name}连接...", Toast.LENGTH_SHORT).show()
                }
                
                override fun onTestSuccess(message: String) {
                    Toast.makeText(this@AIContactListActivity, "✅ $message", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "API测试成功: ${contact.name} - $message")
                }
                
                override fun onTestFailure(error: String) {
                    Toast.makeText(this@AIContactListActivity, "❌ $error", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "API测试失败: ${contact.name} - $error")
                }
            }
        )
    }

    /**
     * 显示配置信息
     */
    private fun showConfigInfo(contact: ChatContact) {
        val apiUrl = contact.customData["api_url"] ?: ""
        val model = contact.customData["model"] ?: ""
        val isConfigured = contact.customData["is_configured"] == "true"
        
        val message = if (isConfigured) {
            "API地址: $apiUrl\n模型: $model\n状态: 已配置"
        } else {
            "状态: 未配置\n请点击配置按钮添加API密钥"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("${contact.name}配置信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 打开与联系人的对话
     */
    private fun openChatWithContact(contact: ChatContact) {
        // 实时检查配置状态，而不是只依赖缓存值
        val aiName = contact.name
        val isTempService = contact.customData["is_temp_service"] == "true"
        
        if (!isTempService) {
            // 对于豆包Pro，使用专门的方法检查
            val isConfigured = if (aiName == "豆包Pro") {
                val settingsManager = SettingsManager.getInstance(this)
                val apiKey = settingsManager.getDoubaoApiKey()
                val hasSavedKey = settingsManager.getSharedPreferences().contains("doubao_api_key")
                val result = settingsManager.isDoubaoApiKeyConfigured() || (hasSavedKey && apiKey.isNotEmpty())
                Log.d(TAG, "打开对话前检查豆包Pro配置: apiKey长度=${apiKey.length}, hasSavedKey=$hasSavedKey, isConfigured=$result")
                result
            } else {
                val apiKey = getApiKeyForAI(aiName)
                apiKey.isNotEmpty()
            }
            
            if (!isConfigured) {
                Toast.makeText(this, "请先配置API密钥", Toast.LENGTH_SHORT).show()
                // 刷新联系人列表，确保显示最新状态
                updateContactList()
                return
            }
        }
        
        // 返回结果给调用方
        val intent = Intent()
        intent.putExtra(EXTRA_AI_CONTACT, contact)
        setResult(RESULT_OK, intent)
        finish()
    }

    /**
     * 更新联系人的API密钥
     */
    private fun updateContactApiKey(contact: ChatContact, apiKey: String, apiUrl: String, model: String) {
        val isConfigured = apiKey.isNotEmpty()
        
        // 创建新的联系人实例，因为ChatContact是不可变的数据类
        val updatedContact = contact.copy(
            isOnline = isConfigured,
            lastMessage = if (isConfigured) "API已配置，可以开始对话" else "点击配置API密钥",
            isPinned = isConfigured,
            customData = contact.customData.toMutableMap().apply {
                put("api_key", apiKey)
                put("api_url", apiUrl)
                put("model", model)
                put("is_configured", isConfigured.toString())
            }
        )
        
        // 更新列表中的联系人
        val index = allAIContacts.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            allAIContacts[index] = updatedContact
        }
        
        // 更新适配器
        aiContactAdapter.updateContacts(allAIContacts)
    }

    /**
     * 保存自定义AI
     */
    private fun saveCustomAI(name: String, apiKey: String, apiUrl: String, model: String) {
        val contact = ChatContact(
            id = "custom_ai_${System.currentTimeMillis()}",
            name = name,
            type = ContactType.AI,
            description = "自定义AI助手",
            isOnline = true,
            lastMessage = "API已配置，可以开始对话",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            isPinned = true,
            customData = mapOf(
                "api_url" to apiUrl,
                "api_key" to apiKey,
                "model" to model,
                "is_configured" to "true"
            )
        )
        
        allAIContacts.add(contact)
        aiContactAdapter.updateContacts(allAIContacts)
        
        // 保存到系统设置
        saveApiKeyForAI(name, apiKey, apiUrl, model)
    }

    // 以下方法从SimpleModeActivity复制，用于API密钥管理
    
    /**
     * 获取原始API密钥，用于保存到customData
     */
    private fun getRawApiKeyForAI(aiName: String): String {
        val settingsManager = SettingsManager.getInstance(this)
        return when (aiName.lowercase()) {
            "deepseek" -> settingsManager.getDeepSeekApiKey()
            "chatgpt" -> settingsManager.getString("chatgpt_api_key", "") ?: ""
            "claude" -> settingsManager.getString("claude_api_key", "") ?: ""
            "gemini" -> settingsManager.getString("gemini_api_key", "") ?: ""
            "智谱ai", "智谱AI" -> settingsManager.getString("zhipu_ai_api_key", "") ?: ""
            "文心一言" -> settingsManager.getString("wenxin_api_key", "") ?: ""
            "通义千问" -> settingsManager.getString("qianwen_api_key", "") ?: ""
            "讯飞星火" -> settingsManager.getString("xinghuo_api_key", "") ?: ""
            "kimi" -> settingsManager.getString("kimi_api_key", "") ?: ""
            "豆包pro", "doubao" -> settingsManager.getDoubaoApiKey()
            else -> settingsManager.getString("${aiName.lowercase()}_api_key", "") ?: ""
        }
    }
    
    /**
     * 获取处理后的API密钥，用于判断是否已配置
     */
    private fun getApiKeyForAI(aiName: String): String {
        val settingsManager = SettingsManager.getInstance(this)
        val isDoubao = aiName.lowercase() in listOf("豆包pro", "doubao")
        
        // 对于豆包Pro，使用专门的方法判断
        if (isDoubao) {
            val apiKey = settingsManager.getDoubaoApiKey()
            val isConfigured = settingsManager.isDoubaoApiKeyConfigured()
            // 额外检查：如果SharedPreferences中明确保存过这个key，且值不为空，则认为已配置
            val hasSavedKey = settingsManager.getSharedPreferences().contains("doubao_api_key")
            val finalIsConfigured = isConfigured || (hasSavedKey && apiKey.isNotEmpty())
            Log.d(TAG, "getApiKeyForAI: 豆包Pro, apiKey长度=${apiKey.length}, isConfigured=$isConfigured, hasSavedKey=$hasSavedKey, finalIsConfigured=$finalIsConfigured")
            return if (finalIsConfigured) apiKey else ""
        }
        
        // 其他AI直接返回原始密钥
        val rawKey = getRawApiKeyForAI(aiName)
        Log.d(TAG, "getApiKeyForAI: $aiName, rawKey长度: ${rawKey.length}")
        return rawKey
    }

    private fun saveApiKeyForAI(aiName: String, apiKey: String, apiUrl: String, model: String) {
        val settingsManager = SettingsManager.getInstance(this)
        
        when (aiName.lowercase()) {
            "deepseek" -> {
                // 使用专门的DeepSeek方法
                settingsManager.setDeepSeekApiKey(apiKey)
                settingsManager.putString("deepseek_api_url", apiUrl)
                settingsManager.putString("deepseek_model", model)
                Log.d(TAG, "保存DeepSeek API配置")
            }
            "豆包pro", "doubao" -> {
                // 使用专门的豆包Pro方法
                settingsManager.setDoubaoApiKey(apiKey)
                if (apiUrl.isNotEmpty()) {
                    settingsManager.putString("doubao_api_url", apiUrl)
                }
                if (model.isNotEmpty()) {
                    settingsManager.setDoubaoModelId(model)
                }
                Log.d(TAG, "保存豆包Pro API配置: apiKey=${apiKey.take(10)}..., apiUrl=$apiUrl, model=$model")
            }
            else -> {
                // 其他AI使用通用方法
                val keyName = when (aiName.lowercase()) {
                    "chatgpt" -> "chatgpt_api_key"
                    "claude" -> "claude_api_key"
                    "gemini" -> "gemini_api_key"
                    "智谱ai", "智谱AI" -> "zhipu_ai_api_key"
                    "文心一言" -> "wenxin_api_key"
                    "通义千问" -> "qianwen_api_key"
                    "讯飞星火" -> "xinghuo_api_key"
                    "kimi" -> "kimi_api_key"
                    else -> "${aiName.lowercase()}_api_key"
                }
                
                // 保存API密钥
                settingsManager.putString(keyName, apiKey)
                
                // 保存API URL
                val urlKeyName = keyName.replace("_api_key", "_api_url")
                settingsManager.putString(urlKeyName, apiUrl)
                
                // 保存模型名称
                val modelKeyName = keyName.replace("_api_key", "_model")
                settingsManager.putString(modelKeyName, model)
                
                Log.d(TAG, "保存API配置: $keyName, $urlKeyName, $modelKeyName")
            }
        }
    }

    private fun deleteApiKeyForAI(aiName: String) {
        val settingsManager = SettingsManager.getInstance(this)
        
        when (aiName.lowercase()) {
            "豆包pro", "doubao" -> {
                // 使用专门的豆包Pro方法删除
                settingsManager.setDoubaoApiKey("")
                settingsManager.putString("doubao_api_url", "")
                settingsManager.setDoubaoModelId("ep-Needs-Your-Endpoint-ID")
                Log.d(TAG, "删除豆包Pro API配置")
            }
            else -> {
                val keyName = when (aiName.lowercase()) {
                    "deepseek" -> "deepseek_api_key"
                    "chatgpt" -> "chatgpt_api_key"
                    "claude" -> "claude_api_key"
                    "gemini" -> "gemini_api_key"
                    "智谱ai", "智谱AI" -> "zhipu_ai_api_key"
                    "文心一言" -> "wenxin_api_key"
                    "通义千问" -> "qianwen_api_key"
                    "讯飞星火" -> "xinghuo_api_key"
                    "kimi" -> "kimi_api_key"
                    else -> "${aiName.lowercase()}_api_key"
                }
                
                // 删除API密钥
                settingsManager.putString(keyName, "")
                
                // 删除API URL
                val urlKeyName = keyName.replace("_api_key", "_api_url")
                settingsManager.putString(urlKeyName, "")
                
                // 删除模型名称
                val modelKeyName = keyName.replace("_api_key", "_model")
                settingsManager.putString(modelKeyName, "")
                
                Log.d(TAG, "删除API配置: $keyName, $urlKeyName, $modelKeyName")
            }
        }
    }

    private fun getDefaultApiUrl(aiName: String): String {
        return when (aiName.lowercase()) {
            "临时专线" -> "https://818233.xyz/"
            "deepseek" -> "https://api.deepseek.com/v1/chat/completions"
            "chatgpt" -> "https://api.openai.com/v1/chat/completions"
            "claude" -> "https://api.anthropic.com/v1/messages"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
            "文心一言" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
            "通义千问" -> "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            "讯飞星火" -> "https://spark-api.xf-yun.com/v3.1/chat"
            "kimi" -> "https://api.moonshot.cn/v1/chat/completions"
            "智谱ai", "zhipu", "glm" -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            "豆包pro", "doubao" -> "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
    }

    private fun getDefaultModel(aiName: String): String {
        return when (aiName.lowercase()) {
            "临时专线" -> "gpt-oss-20b"
            "deepseek" -> "deepseek-chat"
            "chatgpt" -> "gpt-3.5-turbo"
            "claude" -> "claude-3-sonnet-20240229"
            "gemini" -> "gemini-pro"
            "文心一言" -> "ernie-bot-4"
            "通义千问" -> "qwen-turbo"
            "讯飞星火" -> "spark-v3.1"
            "kimi" -> "moonshot-v1-8k"
            "智谱ai", "zhipu", "glm" -> "glm-4"
            "豆包pro", "doubao" -> {
                // 尝试获取已保存的模型ID，如果没有则返回空字符串让用户输入
                val settingsManager = SettingsManager.getInstance(this)
                val modelId = settingsManager.getDoubaoModelId()
                if (modelId == "ep-Needs-Your-Endpoint-ID") "" else modelId
            }
            else -> "gpt-3.5-turbo"
        }
    }
    
    /**
     * 获取AI的最后对话消息
     */
    private fun getLastChatMessage(aiName: String): String {
        try {
            // 从ChatDataManager获取最后的消息
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            
            // 使用统一的ID生成方法
            val settingsManager = SettingsManager.getInstance(this)
            val contactId = settingsManager.generateAIContactId(aiName)
            
            // 获取对应的AI服务类型
            val serviceType = getAIServiceType(aiName)
            if (serviceType != null) {
                val messages = chatDataManager.getMessages(contactId, serviceType)
                if (messages.isNotEmpty()) {
                    val lastMessage = messages.last()
                    return lastMessage.content.take(50) + if (lastMessage.content.length > 50) "..." else ""
                }
            }
            
            // 如果ChatDataManager中没有数据，尝试从旧的存储中获取
            val sharedPrefs = getSharedPreferences("ai_chat_history", MODE_PRIVATE)
            return sharedPrefs.getString("${aiName}_last_message", "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "获取最后聊天消息失败", e)
            return ""
        }
    }
    
    /**
     * 获取AI的最后对话时间
     */
    private fun getLastChatTime(aiName: String): Long {
        try {
            // 从ChatDataManager获取最后的时间
            val chatDataManager = com.example.aifloatingball.data.ChatDataManager.getInstance(this)
            
            // 使用统一的ID生成方法
            val settingsManager = SettingsManager.getInstance(this)
            val contactId = settingsManager.generateAIContactId(aiName)
            
            // 获取对应的AI服务类型
            val serviceType = getAIServiceType(aiName)
            if (serviceType != null) {
                val messages = chatDataManager.getMessages(contactId, serviceType)
                if (messages.isNotEmpty()) {
                    return messages.last().timestamp
                }
            }
            
            // 如果ChatDataManager中没有数据，尝试从旧的存储中获取
            val sharedPrefs = getSharedPreferences("ai_chat_history", MODE_PRIVATE)
            return sharedPrefs.getLong("${aiName}_last_time", 0)
        } catch (e: Exception) {
            Log.e(TAG, "获取最后聊天时间失败", e)
            return 0
        }
    }
    
    /**
     * 根据AI名称获取对应的AIServiceType
     */
    private fun getAIServiceType(aiName: String): com.example.aifloatingball.manager.AIServiceType? {
        return when (aiName) {
            "DeepSeek" -> com.example.aifloatingball.manager.AIServiceType.DEEPSEEK
            "ChatGPT" -> com.example.aifloatingball.manager.AIServiceType.CHATGPT
            "Claude" -> com.example.aifloatingball.manager.AIServiceType.CLAUDE
            "Gemini" -> com.example.aifloatingball.manager.AIServiceType.GEMINI
            "智谱AI" -> com.example.aifloatingball.manager.AIServiceType.ZHIPU_AI
            "文心一言" -> com.example.aifloatingball.manager.AIServiceType.WENXIN
            "通义千问" -> com.example.aifloatingball.manager.AIServiceType.QIANWEN
            "讯飞星火" -> com.example.aifloatingball.manager.AIServiceType.XINGHUO
            "Kimi" -> com.example.aifloatingball.manager.AIServiceType.KIMI
            else -> null
        }
    }

    /**
     * 显示创建群聊对话框
     */
    private fun showCreateGroupDialog() {
        // 获取已配置的AI列表（包括临时专线）
        val configuredAIs = allAIContacts.filter { contact ->
            val apiKey = contact.customData["api_key"] ?: ""
            val isTempService = contact.customData["is_temp_service"] == "true"
            // 临时专线不需要API密钥，其他AI需要有效API密钥
            isTempService || apiKey.isNotEmpty()
        }

        if (configuredAIs.size < 2) {
            Toast.makeText(this, "至少需要配置2个AI才能创建群聊", Toast.LENGTH_SHORT).show()
            return
        }

        val aiNames = configuredAIs.map { it.name }.toTypedArray()
        val selectedAIs = BooleanArray(aiNames.size)

        MaterialAlertDialogBuilder(this)
            .setTitle("创建群聊")
            .setMultiChoiceItems(aiNames, selectedAIs) { _, which, isChecked ->
                selectedAIs[which] = isChecked
            }
            .setPositiveButton("创建") { _, _ ->
                val selectedContacts = mutableListOf<ChatContact>()
                for (i in selectedAIs.indices) {
                    if (selectedAIs[i]) {
                        selectedContacts.add(configuredAIs[i])
                    }
                }

                if (selectedContacts.size < 2) {
                    Toast.makeText(this, "请至少选择2个AI", Toast.LENGTH_SHORT).show()
                } else {
                    createGroupChat(selectedContacts)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 从ChatContact获取AIServiceType
     */
    private fun getAIServiceTypeFromContact(contact: ChatContact): AIServiceType? {
        // 特殊处理临时专线
        if (contact.customData["is_temp_service"] == "true") {
            return AIServiceType.TEMP_SERVICE
        }
        return com.example.aifloatingball.utils.AIServiceTypeUtils.getAIServiceTypeFromContact(contact)
    }

    /**
     * 创建群聊
     */
    private fun createGroupChat(selectedAIs: List<ChatContact>) {
        try {
            // 创建群聊名称
            val groupName = "群聊 (${selectedAIs.joinToString(", ") { it.name }})"
            
            // 使用UUID生成群聊ID，确保与UnifiedGroupChatManager兼容
             val groupId = java.util.UUID.randomUUID().toString()
             
             // 创建群聊成员列表，确保包含aiServiceType
             val groupMembers = selectedAIs.mapNotNull { ai ->
                 val aiServiceType = getAIServiceTypeFromContact(ai)
                 if (aiServiceType != null) {
                     GroupMember(
                         id = ai.id,
                         name = ai.name,
                         type = MemberType.AI,
                         aiServiceType = aiServiceType, // 关键修复：设置aiServiceType
                         role = MemberRole.MEMBER,
                         joinTime = System.currentTimeMillis()
                     )
                 } else {
                     Log.w(TAG, "无法识别AI服务类型: ${ai.name} (ID: ${ai.id})")
                     null
                 }
             }
             
             // 获取AI服务类型列表
             val aiServiceTypes = selectedAIs.mapNotNull { ai ->
                 getAIServiceTypeFromContact(ai)
             }
             
             // 使用UnifiedGroupChatManager创建群聊
             val groupChat = unifiedGroupChatManager.createGroupChat(
                 name = groupName,
                 description = "包含 ${selectedAIs.size} 个AI助手的群聊",
                 aiMembers = aiServiceTypes
             )
             
             Log.d(TAG, "通过UnifiedGroupChatManager创建群聊成功: ${groupChat.id}")
            
            // 创建群聊联系人并添加到联系人列表
            val groupContact = ChatContact(
                id = groupChat.id,
                name = groupChat.name,
                avatar = groupChat.avatar,
                type = ContactType.GROUP,
                description = groupChat.description,
                isOnline = true,
                lastMessage = groupChat.lastMessage,
                lastMessageTime = groupChat.lastMessageTime,
                unreadCount = groupChat.unreadCount,
                isPinned = groupChat.isPinned,
                isMuted = groupChat.isMuted,
                groupId = groupChat.id,
                memberCount = groupChat.members.size,
                aiMembers = groupChat.members.filter { it.type == MemberType.AI }.map { it.name },
                customData = mutableMapOf(
                    "group_chat_id" to groupChat.id,
                    "created_time" to System.currentTimeMillis().toString(),
                    "ai_service_types" to aiServiceTypes.joinToString(",") { it.name }
                )
            )
            
            // 添加到联系人列表（通过SimpleModeActivity的方法）
            // 这里需要通知SimpleModeActivity更新联系人列表
            val broadcastIntent = Intent("com.example.aifloatingball.GROUP_CHAT_CREATED")
            broadcastIntent.putExtra("group_contact", groupContact)
            sendBroadcast(broadcastIntent)
            
            // 跳转到群聊界面
            val chatIntent = Intent(this, ChatActivity::class.java)
            chatIntent.putExtra(ChatActivity.EXTRA_CONTACT, groupContact)
            startActivity(chatIntent)
            
            Toast.makeText(this, "群聊创建成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "创建群聊失败", e)
            Toast.makeText(this, "创建群聊失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 保存群聊联系人到本地存储
     */
    private fun saveGroupChatContact(groupContact: ChatContact) {
        try {
            val prefs = getSharedPreferences("chat_contacts", MODE_PRIVATE)
            val existingJson = prefs.getString("saved_contacts", null)
            
            val gson = com.google.gson.Gson()
            val categories: MutableList<com.example.aifloatingball.model.ContactCategory> = if (existingJson != null) {
                val type = object : com.google.gson.reflect.TypeToken<MutableList<com.example.aifloatingball.model.ContactCategory>>() {}.type
                val result = gson.fromJson<MutableList<com.example.aifloatingball.model.ContactCategory>>(existingJson, type)
                result?.toMutableList() ?: mutableListOf()
            } else {
                mutableListOf()
            }

            // 查找或创建全部分类
            var groupCategory = categories.find { it.name == "全部" }
            if (groupCategory == null) {
                groupCategory = com.example.aifloatingball.model.ContactCategory(
                    name = "全部",
                    contacts = mutableListOf()
                )
                categories.add(groupCategory)
            }

            // 添加群聊联系人
            groupCategory.contacts.add(groupContact)

            // 保存更新后的数据
            val updatedJson = gson.toJson(categories)
            prefs.edit().putString("saved_contacts", updatedJson).apply()
            
            Log.d(TAG, "群聊联系人已保存: ${groupContact.name}")
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊联系人失败", e)
            throw e
        }
    }
}
