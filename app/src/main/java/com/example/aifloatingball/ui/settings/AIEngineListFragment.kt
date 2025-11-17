package com.example.aifloatingball.ui.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.adapter.GenericSearchEngineAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.google.android.material.textfield.TextInputEditText

class AIEngineListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GenericSearchEngineAdapter<AISearchEngine>
    private lateinit var settingsManager: SettingsManager
    private var engines: MutableList<AISearchEngine> = mutableListOf()
    private var categoryName: String = ""
    private lateinit var disableAllButton: Button
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())
        arguments?.let {
            val engineList = it.getParcelableArrayList<AISearchEngine>("engines") ?: emptyList()
            engines.clear()
            engines.addAll(engineList)
            categoryName = it.getString("category", "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_ai_engine_list_with_button, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerViewSearchEngines)
        disableAllButton = rootView.findViewById(R.id.btnDisableAll)
        
        setupRecyclerView()
        setupDisableAllButton()
        
        return rootView
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        
        // 加载保存的排序顺序
        val savedOrder = settingsManager.getAIEngineOrder(categoryName, engines.map { it.name })
        val orderedEngines = mutableListOf<AISearchEngine>()
        // 先按保存的顺序排列
        savedOrder.forEach { engineName ->
            engines.find { it.name == engineName }?.let { orderedEngines.add(it) }
        }
        // 添加未在排序中的新引擎
        engines.forEach { engine ->
            if (!orderedEngines.any { it.name == engine.name }) {
                orderedEngines.add(engine)
            }
        }
        engines.clear()
        engines.addAll(orderedEngines)
        
        val enabledEngines = settingsManager.getEnabledAIEngines().toMutableSet()

        adapter = GenericSearchEngineAdapter(
            context = requireContext(),
            engines = engines,
            enabledEngines = enabledEngines,
            onEngineToggled = { engineName, isEnabled ->
                val allEnabledEngines = settingsManager.getEnabledAIEngines().toMutableSet()
                if (isEnabled) {
                    allEnabledEngines.add(engineName)
                } else {
                    allEnabledEngines.remove(engineName)
                }
                settingsManager.saveEnabledAIEngines(allEnabledEngines)
                requireContext().sendBroadcast(Intent(DualFloatingWebViewService.ACTION_UPDATE_AI_ENGINES))
            },
            onOrderChanged = { orderedList ->
                // 保存排序顺序
                settingsManager.saveAIEngineOrder(categoryName, orderedList.map { it.name })
            },
            onEngineClick = if (categoryName == "API对话") {
                { engine -> showApiConfigDialog(engine) }
            } else {
                null
            }
        )
        recyclerView.adapter = adapter
        
        // 设置拖拽排序
        setupDragAndDrop()
    }
    
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }
        }
        
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }
    
    private fun setupDisableAllButton() {
        disableAllButton.setOnClickListener {
            // 显示确认对话框
            AlertDialog.Builder(requireContext())
                .setTitle("确认关闭")
                .setMessage("确定要关闭当前分类下的所有AI引擎吗？")
                .setPositiveButton("确定") { _, _ ->
                    // 关闭当前分类下的所有引擎
                    val allEnabledEngines = settingsManager.getEnabledAIEngines().toMutableSet()
                    engines.forEach { engine ->
                        allEnabledEngines.remove(engine.name)
                    }
                    settingsManager.saveEnabledAIEngines(allEnabledEngines)
                    requireContext().sendBroadcast(Intent(DualFloatingWebViewService.ACTION_UPDATE_AI_ENGINES))
                    
                    // 更新适配器
                    adapter.updateEngines(engines)
                    Toast.makeText(requireContext(), "已关闭所有引擎", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    fun getEnabledEngines(): List<AISearchEngine> {
        return adapter.getEnabledEngines()
    }
    
    /**
     * 显示API配置对话框
     */
    private fun showApiConfigDialog(engine: AISearchEngine) {
        if (engine.name == "临时专线") {
            Toast.makeText(requireContext(), "临时专线是免费服务，无需配置API密钥", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 获取API配置键和默认值
        val apiConfig = getApiConfigForEngine(engine.name)
        if (apiConfig == null) {
            Toast.makeText(requireContext(), "未找到${engine.name}的API配置", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 创建带有 Material 主题的上下文
        val wrappedContext = android.view.ContextThemeWrapper(requireContext(), R.style.AppTheme_Dialog)
        val dialogLayout = LayoutInflater.from(wrappedContext).inflate(R.layout.dialog_api_key_config, null)
        val apiKeyInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_key_input)
        val apiUrlInput = dialogLayout.findViewById<TextInputEditText>(R.id.api_url_input)
        val modelInput = dialogLayout.findViewById<TextInputEditText>(R.id.model_input)
        
        // 设置当前值
        val currentApiKey = settingsManager.getString(apiConfig.apiKeyKey, "") ?: ""
        val currentApiUrl = settingsManager.getString(apiConfig.apiUrlKey, "") ?: ""
        val currentModel = settingsManager.getString(apiConfig.modelKey, "") ?: ""
        
        apiKeyInput.setText(currentApiKey)
        apiUrlInput.setText(if (currentApiUrl.isNotEmpty()) currentApiUrl else apiConfig.defaultApiUrl)
        modelInput.setText(if (currentModel.isNotEmpty()) currentModel else apiConfig.defaultModel)
        
        // 设置提示文本
        apiKeyInput.hint = "请输入${engine.name}的API密钥"
        apiUrlInput.hint = "请输入${engine.name}的API URL"
        modelInput.hint = "请输入模型名称（可选）"
        
        // 尝试从剪贴板自动粘贴API密钥
        val clipboardContent = getClipboardContent()
        if (clipboardContent != null && clipboardContent.isNotEmpty() && currentApiKey.isEmpty()) {
            // 如果剪贴板有内容且当前API密钥为空，自动填充
            apiKeyInput.setText(clipboardContent)
            // 将光标移到末尾
            apiKeyInput.setSelection(clipboardContent.length)
            Toast.makeText(requireContext(), "已自动粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
        }
        
        // 创建对话框
        val dialog = AlertDialog.Builder(wrappedContext)
            .setTitle("配置${engine.name}")
            .setMessage("请填写${engine.name}的API密钥和URL")
            .setView(dialogLayout)
            .setPositiveButton("确定") { _, _ ->
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val apiUrl = apiUrlInput.text?.toString()?.trim() ?: ""
                val model = modelInput.text?.toString()?.trim() ?: ""
                
                if (apiKey.isNotEmpty()) {
                    // 保存API配置
                    settingsManager.putString(apiConfig.apiKeyKey, apiKey)
                    settingsManager.putString(apiConfig.apiUrlKey, if (apiUrl.isNotEmpty()) apiUrl else apiConfig.defaultApiUrl)
                    if (model.isNotEmpty()) {
                        settingsManager.putString(apiConfig.modelKey, model)
                    }
                    
                    Toast.makeText(requireContext(), "${engine.name}配置成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "请输入API密钥", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
        
        // 对话框显示后，自动聚焦到API密钥输入框
        apiKeyInput.requestFocus()
    }
    
    /**
     * 获取剪贴板内容
     */
    private fun getClipboardContent(): String? {
        return try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && 
                clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0)?.coerceToText(requireContext())?.toString()
                    if (!text.isNullOrBlank()) {
                        text.trim()
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AIEngineListFragment", "获取剪贴板内容失败", e)
            null
        }
    }
    
    /**
     * 根据AI引擎名称获取API配置信息
     */
    private fun getApiConfigForEngine(engineName: String): ApiConfig? {
        return when {
            engineName.contains("DeepSeek", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "deepseek_api_key",
                apiUrlKey = "deepseek_api_url",
                modelKey = "deepseek_model",
                defaultApiUrl = "https://api.deepseek.com/v1/chat/completions",
                defaultModel = "deepseek-chat"
            )
            engineName.contains("ChatGPT", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "chatgpt_api_key",
                apiUrlKey = "chatgpt_api_url",
                modelKey = "chatgpt_model",
                defaultApiUrl = "https://api.openai.com/v1/chat/completions",
                defaultModel = "gpt-3.5-turbo"
            )
            engineName.contains("Claude", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "claude_api_key",
                apiUrlKey = "claude_api_url",
                modelKey = "claude_model",
                defaultApiUrl = "https://api.anthropic.com/v1/messages",
                defaultModel = "claude-3-sonnet-20240229"
            )
            engineName.contains("通义千问", ignoreCase = true) || engineName.contains("Qianwen", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "qianwen_api_key",
                apiUrlKey = "qianwen_api_url",
                modelKey = "qianwen_model",
                defaultApiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                defaultModel = "qwen-turbo"
            )
            engineName.contains("智谱", ignoreCase = true) || engineName.contains("Zhipu", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "zhipu_ai_api_key",
                apiUrlKey = "zhipu_ai_api_url",
                modelKey = "zhipu_ai_model",
                defaultApiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                defaultModel = "glm-4"
            )
            engineName.contains("文心一言", ignoreCase = true) || engineName.contains("Wenxin", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "wenxin_api_key",
                apiUrlKey = "wenxin_api_url",
                modelKey = "wenxin_model",
                defaultApiUrl = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions",
                defaultModel = "ernie-bot-4"
            )
            engineName.contains("Gemini", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "gemini_api_key",
                apiUrlKey = "gemini_api_url",
                modelKey = "gemini_model",
                defaultApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
                defaultModel = "gemini-pro"
            )
            engineName.contains("Kimi", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "kimi_api_key",
                apiUrlKey = "kimi_api_url",
                modelKey = "kimi_model",
                defaultApiUrl = "https://api.moonshot.cn/v1/chat/completions",
                defaultModel = "moonshot-v1-8k"
            )
            engineName.contains("讯飞星火", ignoreCase = true) || engineName.contains("Xinghuo", ignoreCase = true) -> ApiConfig(
                apiKeyKey = "xinghuo_api_key",
                apiUrlKey = "xinghuo_api_url",
                modelKey = "xinghuo_model",
                defaultApiUrl = "https://spark-api.xf-yun.com/v3.1/chat",
                defaultModel = "spark-v3.1"
            )
            else -> null
        }
    }
    
    /**
     * API配置数据类
     */
    private data class ApiConfig(
        val apiKeyKey: String,
        val apiUrlKey: String,
        val modelKey: String,
        val defaultApiUrl: String,
        val defaultModel: String
    )

    companion object {
        fun newInstance(engines: List<AISearchEngine>, category: String = ""): AIEngineListFragment {
            val fragment = AIEngineListFragment()
            val args = Bundle()
            args.putParcelableArrayList("engines", ArrayList(engines))
            args.putString("category", category)
            fragment.arguments = args
            return fragment
        }
    }
} 