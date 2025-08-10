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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.AIContactListAdapter
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class AIContactListActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AIContactListActivity"
        const val EXTRA_AI_CONTACT = "extra_ai_contact"
    }

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var aiContactList: RecyclerView
    private lateinit var addCustomButton: MaterialButton

    private lateinit var aiContactAdapter: AIContactListAdapter
    private var allAIContacts = mutableListOf<ChatContact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_contact_list)

        initializeViews()
        setupAIContacts()
        setupListeners()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        titleText = findViewById(R.id.title_text)
        aiContactList = findViewById(R.id.ai_contact_list)
        addCustomButton = findViewById(R.id.add_custom_button)

        titleText.text = "AI联系人列表"
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
        aiContactAdapter.updateContacts(allAIContacts)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        addCustomButton.setOnClickListener {
            showAddCustomAIDialog()
        }
    }

    /**
     * 生成AI联系人列表
     */
    private fun generateAIContactsList(): MutableList<ChatContact> {
        val contacts = mutableListOf<ChatContact>()
        
        // 定义所有可用的AI助手
        val availableAIs = listOf(
            "DeepSeek" to "🚀 DeepSeek - 性能强劲，支持中文",
            "ChatGPT" to "🤖 ChatGPT - OpenAI的经典模型",
            "Claude" to "💡 Claude - Anthropic的智能助手",
            "Gemini" to "🌟 Gemini - Google的AI助手",
            "智谱AI" to "🧠 智谱AI - GLM-4大语言模型",
            "文心一言" to "📚 文心一言 - 百度的大语言模型",
            "通义千问" to "🎯 通义千问 - 阿里巴巴的AI",
            "讯飞星火" to "⚡ 讯飞星火 - 科大讯飞的AI",
            "Kimi" to "🌙 Kimi - Moonshot的长文本专家"
        )

        availableAIs.forEach { (aiName, description) ->
            val apiKey = getApiKeyForAI(aiName)
            val isConfigured = apiKey.isNotEmpty()
            
            val contact = ChatContact(
                id = "ai_${aiName.lowercase().replace(" ", "_")}",
                name = aiName,
                type = ContactType.AI,
                description = description,
                isOnline = isConfigured,
                lastMessage = if (isConfigured) "API已配置，可以开始对话" else "点击配置API密钥",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = isConfigured,
                customData = mapOf(
                    "api_url" to getDefaultApiUrl(aiName),
                    "api_key" to apiKey,
                    "model" to getDefaultModel(aiName),
                    "is_configured" to isConfigured.toString()
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
            val isConfigured = contact.customData["is_configured"] == "true"
            
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
        
        // 创建自定义布局
        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.dialog_api_key_config, null)
        val apiKeyInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_key_input)
        val apiUrlInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_url_input)
        val modelInput = dialogLayout.findViewById<TextInputEditText>(R.id.model_input)
        
        // 设置默认值
        apiUrlInput.setText(getDefaultApiUrl(aiName))
        modelInput.setText(getDefaultModel(aiName))
        
        // 设置提示文本
        apiKeyInput.hint = "请输入${aiName}的API密钥"
        
        AlertDialog.Builder(this)
            .setTitle("配置${aiName}")
            .setMessage("请填写${aiName}的API密钥以激活对话功能")
            .setView(dialogLayout)
            .setPositiveButton("确定") { _, _ ->
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val apiUrl = apiUrlInput.text?.toString()?.trim() ?: ""
                val model = modelInput.text?.toString()?.trim() ?: ""
                
                if (apiKey.isNotEmpty()) {
                    // 保存API密钥
                    saveApiKeyForAI(aiName, apiKey, apiUrl, model)
                    
                    // 更新联系人状态
                    updateContactApiKey(contact, apiKey, apiUrl, model)
                    
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
        
        // 创建自定义布局
        val dialogLayout = LayoutInflater.from(this).inflate(R.layout.dialog_api_key_config, null)
        val apiKeyInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_key_input)
        val apiUrlInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_url_input)
        val modelInput = dialogLayout.findViewById<TextInputEditText>(R.id.model_input)
        
        // 设置当前值
        apiKeyInput.setText(currentApiKey)
        apiUrlInput.setText(currentApiUrl)
        modelInput.setText(currentModel)
        
        // 设置提示文本
        apiKeyInput.hint = "请输入${aiName}的API密钥"
        
        AlertDialog.Builder(this)
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
                    
                    // 更新联系人状态
                    updateContactApiKey(contact, apiKey, apiUrl, model)
                    
                    Toast.makeText(this, "${aiName}配置已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请输入API密钥", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("删除配置") { _, _ ->
                // 删除API配置
                deleteApiKeyForAI(aiName)
                updateContactApiKey(contact, "", "", "")
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
        
        AlertDialog.Builder(this)
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
        
        AlertDialog.Builder(this)
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
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先配置API密钥", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "开始测试API连接...", Toast.LENGTH_SHORT).show()
        // TODO: 实现API连接测试逻辑
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
        
        AlertDialog.Builder(this)
            .setTitle("${contact.name}配置信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 打开与联系人的对话
     */
    private fun openChatWithContact(contact: ChatContact) {
        val isConfigured = contact.customData["is_configured"] == "true"
        if (!isConfigured) {
            Toast.makeText(this, "请先配置API密钥", Toast.LENGTH_SHORT).show()
            return
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
    private fun getApiKeyForAI(aiName: String): String {
        val settingsManager = SettingsManager.getInstance(this)
        val keyName = when (aiName.lowercase()) {
            "deepseek" -> "deepseek_api_key"
            "chatgpt" -> "chatgpt_api_key"
            "claude" -> "claude_api_key"
            "gemini" -> "gemini_api_key"
            "智谱ai" -> "zhipu_ai_api_key"
            "文心一言" -> "wenxin_api_key"
            "通义千问" -> "qianwen_api_key"
            "讯飞星火" -> "xinghuo_api_key"
            "kimi" -> "kimi_api_key"
            else -> "${aiName.lowercase()}_api_key"
        }
        return settingsManager.getString(keyName, "") ?: ""
    }

    private fun saveApiKeyForAI(aiName: String, apiKey: String, apiUrl: String, model: String) {
        val settingsManager = SettingsManager.getInstance(this)
        val keyName = when (aiName.lowercase()) {
            "deepseek" -> "deepseek_api_key"
            "chatgpt" -> "chatgpt_api_key"
            "claude" -> "claude_api_key"
            "gemini" -> "gemini_api_key"
            "智谱ai" -> "zhipu_ai_api_key"
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

    private fun deleteApiKeyForAI(aiName: String) {
        val settingsManager = SettingsManager.getInstance(this)
        val keyName = when (aiName.lowercase()) {
            "deepseek" -> "deepseek_api_key"
            "chatgpt" -> "chatgpt_api_key"
            "claude" -> "claude_api_key"
            "gemini" -> "gemini_api_key"
            "智谱ai" -> "zhipu_ai_api_key"
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

    private fun getDefaultApiUrl(aiName: String): String {
        return when (aiName.lowercase()) {
            "deepseek" -> "https://api.deepseek.com/v1/chat/completions"
            "chatgpt" -> "https://api.openai.com/v1/chat/completions"
            "claude" -> "https://api.anthropic.com/v1/messages"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
            "文心一言" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
            "通义千问" -> "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            "讯飞星火" -> "https://spark-api.xf-yun.com/v3.1/chat"
            "kimi" -> "https://api.moonshot.cn/v1/chat/completions"
            "智谱ai", "zhipu", "glm" -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
    }

    private fun getDefaultModel(aiName: String): String {
        return when (aiName.lowercase()) {
            "deepseek" -> "deepseek-chat"
            "chatgpt" -> "gpt-3.5-turbo"
            "claude" -> "claude-3-sonnet-20240229"
            "gemini" -> "gemini-pro"
            "文心一言" -> "ernie-bot-4"
            "通义千问" -> "qwen-turbo"
            "讯飞星火" -> "spark-v3.1"
            "kimi" -> "moonshot-v1-8k"
            "智谱ai", "zhipu", "glm" -> "glm-4"
            else -> "gpt-3.5-turbo"
        }
    }
}
