package com.example.aifloatingball.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.adapter.AIConfigAdapter
import com.example.aifloatingball.model.AIConfigItem
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * AI API配置页面
 * 提供独立的API密钥配置界面，与设置页面联动
 */
class AIApiConfigActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AIApiConfigActivity"
        const val EXTRA_SELECTED_AI = "selected_ai"
        const val REQUEST_CODE_API_SETTINGS = 1001
    }
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var adapter: AIConfigAdapter
    private lateinit var settingsManager: SettingsManager
    
    private val aiConfigItems = mutableListOf<AIConfigItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_api_config)
        
        settingsManager = SettingsManager.getInstance(this)
        
        initViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadAIConfigs()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        fabAdd = findViewById(R.id.fab_add)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "AI API配置"
        }
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = AIConfigAdapter(
            aiConfigItems,
            onConfigClick = { aiConfig ->
                openApiSettings(aiConfig.name)
            },
            onTestClick = { aiConfig ->
                testApiConnection(aiConfig)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupFab() {
        fabAdd.setOnClickListener {
            showAddCustomAIDialog()
        }
    }
    
    private fun loadAIConfigs() {
        aiConfigItems.clear()
        
        // 添加预设AI配置
        val presetAIs = listOf(
            AIConfigItem(
                name = "临时专线",
                displayName = "临时专线",
                description = "免费AI服务 (无需API密钥)",
                apiKeyKey = "",
                apiUrlKey = "",
                defaultApiUrl = "https://818233.xyz/",
                isConfigured = true // 临时专线总是显示为已配置
            ),
            AIConfigItem(
                name = "DeepSeek",
                displayName = "DeepSeek",
                description = "DeepSeek AI助手",
                apiKeyKey = "deepseek_api_key",
                apiUrlKey = "deepseek_api_url",
                defaultApiUrl = "https://api.deepseek.com/v1/chat/completions",
                isConfigured = settingsManager.getString("deepseek_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "ChatGPT",
                displayName = "ChatGPT",
                description = "OpenAI ChatGPT",
                apiKeyKey = "chatgpt_api_key",
                apiUrlKey = "chatgpt_api_url",
                defaultApiUrl = "https://api.openai.com/v1/chat/completions",
                isConfigured = settingsManager.getString("chatgpt_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "Claude",
                displayName = "Claude",
                description = "Anthropic Claude",
                apiKeyKey = "claude_api_key",
                apiUrlKey = "claude_api_url",
                defaultApiUrl = "https://api.anthropic.com/v1/messages",
                isConfigured = settingsManager.getString("claude_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "智谱AI",
                displayName = "智谱AI",
                description = "智谱AI GLM模型",
                apiKeyKey = "zhipu_ai_api_key",
                apiUrlKey = "zhipu_ai_api_url",
                defaultApiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                isConfigured = settingsManager.getString("zhipu_ai_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "通义千问",
                displayName = "通义千问",
                description = "阿里巴巴通义千问",
                apiKeyKey = "qianwen_api_key",
                apiUrlKey = "qianwen_api_url",
                defaultApiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                isConfigured = settingsManager.getString("qianwen_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "文心一言",
                displayName = "文心一言",
                description = "百度文心一言",
                apiKeyKey = "wenxin_api_key",
                apiUrlKey = "wenxin_api_url",
                defaultApiUrl = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions",
                isConfigured = settingsManager.getString("wenxin_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "Gemini",
                displayName = "Gemini",
                description = "Google Gemini",
                apiKeyKey = "gemini_api_key",
                apiUrlKey = "gemini_api_url",
                defaultApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
                isConfigured = settingsManager.getString("gemini_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "Kimi",
                displayName = "Kimi",
                description = "月之暗面 Kimi",
                apiKeyKey = "kimi_api_key",
                apiUrlKey = "kimi_api_url",
                defaultApiUrl = "https://api.moonshot.cn/v1/chat/completions",
                isConfigured = settingsManager.getString("kimi_api_key", "")?.isNotEmpty() == true
            ),
            AIConfigItem(
                name = "豆包Pro",
                displayName = "豆包Pro",
                description = "字节跳动豆包Pro AI",
                apiKeyKey = "doubao_api_key",
                apiUrlKey = "doubao_api_url",
                defaultApiUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                isConfigured = {
                    // 检查SharedPreferences中是否明确保存过这个key
                    val hasSavedKey = settingsManager.getSharedPreferences().contains("doubao_api_key")
                    val apiKey = settingsManager.getDoubaoApiKey()
                    // 如果明确保存过，且值不为空，则认为已配置（包括默认值）
                    val isConfigured = hasSavedKey && apiKey.isNotEmpty()
                    Log.d(TAG, "检查豆包Pro配置状态: hasSavedKey=$hasSavedKey, apiKey=${apiKey.take(10)}..., isConfigured=$isConfigured")
                    isConfigured
                }()
            )
        )
        
        aiConfigItems.addAll(presetAIs)
        
        // 加载自定义AI配置
        loadCustomAIConfigs()
        
        // 调试日志：确认豆包Pro是否在列表中
        Log.d(TAG, "加载的AI配置项数量: ${aiConfigItems.size}")
        aiConfigItems.forEach { item ->
            if (item.name.contains("豆包") || item.name.contains("doubao", ignoreCase = true)) {
                Log.d(TAG, "找到豆包配置项: ${item.name}, 显示名称: ${item.displayName}, 已配置: ${item.isConfigured}")
            }
        }
        
        adapter.notifyDataSetChanged()
    }
    
    private fun loadCustomAIConfigs() {
        // 从SharedPreferences加载自定义AI配置
        val customAICount = settingsManager.getInt("custom_ai_count", 0)
        
        for (i in 0 until customAICount) {
            val name = settingsManager.getString("custom_ai_${i}_name", "") ?: ""
            val apiKey = settingsManager.getString("custom_ai_${i}_api_key", "") ?: ""
            val apiUrl = settingsManager.getString("custom_ai_${i}_api_url", "") ?: ""
            
            if (name.isNotEmpty()) {
                aiConfigItems.add(
                    AIConfigItem(
                        name = name,
                        displayName = name,
                        description = "自定义AI助手",
                        apiKeyKey = "custom_ai_${i}_api_key",
                        apiUrlKey = "custom_ai_${i}_api_url",
                        defaultApiUrl = apiUrl,
                        isConfigured = apiKey.isNotEmpty(),
                        isCustom = true,
                        customIndex = i
                    )
                )
            }
        }
    }
    
    private fun openApiSettings(aiName: String) {
        if (aiName == "临时专线") {
            Toast.makeText(this, "临时专线是免费服务，无需配置API密钥", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 直接在当前界面显示API配置对话框
        showApiConfigDialog(aiName)
    }
    
    private fun showApiConfigDialog(aiName: String) {
        // 查找对应的AI配置
        val aiConfig = aiConfigItems.find { it.name == aiName }
        if (aiConfig == null) {
            Toast.makeText(this, "未找到${aiName}配置", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 创建带有 Material 主题的上下文
        val wrappedContext = android.view.ContextThemeWrapper(this, R.style.AppTheme_Dialog)
        val dialogLayout = LayoutInflater.from(wrappedContext).inflate(R.layout.dialog_api_key_config, null)
        val apiKeyInput = dialogLayout.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.api_key_input)
        val apiUrlInput = dialogLayout.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.api_url_input)
        val modelInput = dialogLayout.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.model_input)
        
        // 设置默认值
        val currentApiKey = if (aiName == "豆包Pro") {
            // 检查是否已经保存过（通过检查SharedPreferences中是否有这个key）
            val savedKey = settingsManager.getDoubaoApiKey()
            // 检查SharedPreferences中是否明确保存过这个key
            val hasSavedKey = settingsManager.getSharedPreferences().contains("doubao_api_key")
            if (hasSavedKey) {
                savedKey  // 如果已经保存过，显示保存的值（包括默认值）
            } else {
                ""  // 如果从未保存过，显示为空
            }
        } else {
            settingsManager.getString(aiConfig.apiKeyKey, "") ?: ""
        }
        val currentApiUrl = settingsManager.getString(aiConfig.apiUrlKey, "") ?: ""
        val currentModel = if (aiName == "豆包Pro") {
            settingsManager.getDoubaoModelId()
        } else {
            getDefaultModel(aiName)
        }
        
        apiKeyInput.setText(currentApiKey)
        apiUrlInput.setText(if (currentApiUrl.isNotEmpty()) currentApiUrl else aiConfig.defaultApiUrl)
        modelInput.setText(currentModel)
        
        // 设置提示文本
        apiKeyInput.hint = "请输入${aiName}的API密钥"
        apiUrlInput.hint = "请输入${aiName}的API URL"
        if (aiName == "豆包Pro") {
            modelInput.hint = "请输入模型ID（Endpoint ID）"
        } else {
            modelInput.hint = "请输入模型名称（可选）"
        }
        
        AlertDialog.Builder(wrappedContext)
            .setTitle("配置${aiName}")
            .setMessage(if (aiName == "豆包Pro") "请填写豆包Pro的API密钥、API URL和模型ID（Endpoint ID）" else "请填写${aiName}的API密钥和URL（可选）")
            .setView(dialogLayout)
            .setPositiveButton("确定") { _, _ ->
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val apiUrl = apiUrlInput.text?.toString()?.trim() ?: ""
                val model = modelInput.text?.toString()?.trim() ?: ""
                
                if (apiKey.isNotEmpty()) {
                    if (aiName == "豆包Pro") {
                        // 豆包Pro特殊处理：使用SettingsManager的专门方法
                        settingsManager.setDoubaoApiKey(apiKey)
                        if (apiUrl.isNotEmpty()) {
                            settingsManager.putString("doubao_api_url", apiUrl)
                        }
                        if (model.isNotEmpty()) {
                            settingsManager.setDoubaoModelId(model)
                        }
                        
                        // 验证保存是否成功
                        val savedKey = settingsManager.getDoubaoApiKey()
                        Log.d(TAG, "保存豆包Pro API密钥后验证: 输入=${apiKey.take(10)}..., 保存后读取=${savedKey.take(10)}..., 匹配=${savedKey == apiKey}")
                        
                        if (savedKey != apiKey) {
                            Log.e(TAG, "豆包Pro API密钥保存失败！输入值与保存值不匹配")
                            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                    } else {
                        // 其他AI使用通用方法
                        settingsManager.putString(aiConfig.apiKeyKey, apiKey)
                        settingsManager.putString(aiConfig.apiUrlKey, if (apiUrl.isNotEmpty()) apiUrl else aiConfig.defaultApiUrl)
                        if (model.isNotEmpty() && model != getDefaultModel(aiName)) {
                            // 保存自定义模型（如果有）
                            val modelKey = aiConfig.apiKeyKey.replace("_api_key", "_model")
                            settingsManager.putString(modelKey, model)
                        }
                    }
                    
                    Toast.makeText(this, "${aiName}配置成功", Toast.LENGTH_SHORT).show()
                    
                    // 刷新列表
                    loadAIConfigs()
                } else {
                    Toast.makeText(this, "请输入API密钥", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun testApiConnection(aiConfig: AIConfigItem) {
        if (!aiConfig.isConfigured) {
            Toast.makeText(this, "请先配置API密钥", Toast.LENGTH_SHORT).show()
            return
        }
        
        val isTempService = aiConfig.name == "临时专线"
        val apiKey = if (isTempService) "" else settingsManager.getString(aiConfig.apiKeyKey, "") ?: ""
        val apiUrl = settingsManager.getString(aiConfig.apiUrlKey, aiConfig.defaultApiUrl) ?: aiConfig.defaultApiUrl
        val model = getDefaultModel(aiConfig.name)
        
        if (!isTempService && apiKey.isEmpty()) {
            Toast.makeText(this, "API密钥为空，请先配置", Toast.LENGTH_SHORT).show()
            return
        }
        
        val apiTestManager = com.example.aifloatingball.manager.ApiTestManager(this)
        
        apiTestManager.testApiConnection(
            aiName = aiConfig.name,
            apiKey = apiKey,
            apiUrl = apiUrl,
            model = model,
            callback = object : com.example.aifloatingball.manager.ApiTestManager.TestCallback {
                override fun onTestStart() {
                    Toast.makeText(this@AIApiConfigActivity, "正在测试${aiConfig.displayName}连接...", Toast.LENGTH_SHORT).show()
                }
                
                override fun onTestSuccess(message: String) {
                    Toast.makeText(this@AIApiConfigActivity, "成功：$message", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "API测试成功: ${aiConfig.name} - $message")
                }
                
                override fun onTestFailure(error: String) {
                    Toast.makeText(this@AIApiConfigActivity, "失败：$error", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "API测试失败: ${aiConfig.name} - $error")
                }
            }
        )
    }
    
    private fun getDefaultModel(aiName: String): String {
        return when (aiName.lowercase()) {
            "临时专线" -> "gpt-oss-20b"
            "deepseek" -> "deepseek-chat"
            "chatgpt", "openai" -> "gpt-3.5-turbo"
            "claude" -> "claude-3-sonnet-20240229"
            "智谱ai", "智谱AI" -> "glm-4"
            "文心一言" -> "ernie-bot"
            "通义千问" -> "qwen-turbo"
            "gemini" -> "gemini-pro"
            "kimi" -> "moonshot-v1-8k"
            "豆包" -> "doubao-lite-4k"
            else -> "gpt-3.5-turbo"
        }
    }
    
    private fun showAddCustomAIDialog() {
        // 创建带有 Material 主题的上下文
        val wrappedContext = android.view.ContextThemeWrapper(this, R.style.AppTheme_Dialog)
        val dialogView = LayoutInflater.from(wrappedContext).inflate(R.layout.dialog_custom_ai_config, null)
        val dialog = AlertDialog.Builder(wrappedContext)
            .setTitle("添加自定义AI")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                // 处理保存逻辑
                val nameEditText = dialogView.findViewById<EditText>(R.id.name_input)
                val apiKeyEditText = dialogView.findViewById<EditText>(R.id.api_key_input)
                val apiUrlEditText = dialogView.findViewById<EditText>(R.id.api_url_input)
                val modelEditText = dialogView.findViewById<EditText>(R.id.model_input)
                
                val name = nameEditText.text.toString().trim()
                val apiKey = apiKeyEditText.text.toString().trim()
                val apiUrl = apiUrlEditText.text.toString().trim()
                val model = modelEditText.text.toString().trim()
                
                if (name.isNotEmpty() && apiKey.isNotEmpty() && apiUrl.isNotEmpty()) {
                    // 保存自定义AI配置
                    val customCount = settingsManager.getCustomAICount()
                    settingsManager.setCustomAICount(customCount + 1)
                    settingsManager.setCustomAIName(customCount, name)
                    settingsManager.setCustomAIApiKey(customCount, apiKey)
                    settingsManager.setCustomAIUrl(customCount, apiUrl)
                    settingsManager.setCustomAIModel(customCount, model)
                    
                    // 刷新列表
                    loadAIConfigs()
                    Toast.makeText(this, "自定义AI已添加", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_API_SETTINGS && resultCode == RESULT_OK) {
            // 刷新配置列表
            loadAIConfigs()
            
            // 如果有选中的AI，返回结果
            data?.getStringExtra(EXTRA_SELECTED_AI)?.let { selectedAI ->
                val resultIntent = Intent()
                resultIntent.putExtra(EXTRA_SELECTED_AI, selectedAI)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}
