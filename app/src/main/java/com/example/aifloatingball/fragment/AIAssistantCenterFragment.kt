package com.example.aifloatingball.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.manager.AIServiceType
import kotlinx.coroutines.*
import java.util.UUID

/**
 * AI助手中心Fragment基类
 */
abstract class AIAssistantCenterFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }
    
    abstract fun getLayoutResId(): Int
}

/**
 * 基础信息Fragment
 */
class BasicInfoFragment : AIAssistantCenterFragment() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var aiSearchModeSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    // 档案管理相关组件
    private lateinit var currentProfileName: TextView
    private lateinit var selectProfileButton: com.google.android.material.button.MaterialButton
    private lateinit var newProfileButton: com.google.android.material.button.MaterialButton
    private lateinit var manageProfilesButton: com.google.android.material.button.MaterialButton
    private lateinit var saveProfileButton: com.google.android.material.button.MaterialButton
    
    // 档案列表相关组件
    private lateinit var profilesRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyProfilesText: TextView
    private lateinit var profileListAdapter: com.example.aifloatingball.adapter.ProfileListAdapter
    
    // 档案编辑相关组件
    private lateinit var profileNameInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var identityInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var conversationStyleInput: AutoCompleteTextView
    private lateinit var answerFormatInput: AutoCompleteTextView
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_basic_info_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager.getInstance(requireContext())
        
        // 初始化组件
        initializeViews(view)
        
        // 设置档案列表
        setupProfileList()
        
        // 注册档案变更监听器
        registerProfileChangeListener()
        
        // 加载当前设置
        loadCurrentSettings()
        
        // 设置监听器
        setupListeners()
    }
    
    private fun initializeViews(view: android.view.View) {
        aiSearchModeSwitch = view.findViewById(R.id.ai_search_mode_switch)
        
        // 档案管理组件
        currentProfileName = view.findViewById(R.id.current_profile_name)
        selectProfileButton = view.findViewById(R.id.select_profile_button)
        newProfileButton = view.findViewById(R.id.new_profile_button)
        manageProfilesButton = view.findViewById(R.id.manage_profiles_button)
        saveProfileButton = view.findViewById(R.id.save_profile_button)
        
        // 档案列表组件
        profilesRecyclerView = view.findViewById(R.id.profiles_recycler_view)
        emptyProfilesText = view.findViewById(R.id.empty_profiles_text)
        
        // 档案编辑组件
        profileNameInput = view.findViewById(R.id.profile_name_input)
        identityInput = view.findViewById(R.id.identity_input)
        conversationStyleInput = view.findViewById(R.id.conversation_style_input)
        answerFormatInput = view.findViewById(R.id.answer_format_input)
    }
    
    private fun setupProfileList() {
        try {
            android.util.Log.d("AIAssistantCenter", "setupProfileList called")
            // 设置RecyclerView布局管理器
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                requireContext(),
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
            profilesRecyclerView.layoutManager = layoutManager
            
            // 加载档案列表
            loadProfileList()
            
            android.util.Log.d("AIAssistantCenter", "档案列表设置完成")
        } catch (e: Exception) {
            android.util.Log.e("AIAssistantCenter", "设置档案列表失败", e)
        }
    }
    
    private fun loadProfileList() {
        try {
            android.util.Log.d("AIAssistantCenter", "loadProfileList called")
            val profiles = settingsManager.getPromptProfiles()
            val currentProfileId = settingsManager.getActivePromptProfileId()
            
            android.util.Log.d("AIAssistantCenter", "Loaded profiles count: ${profiles.size}")
            android.util.Log.d("AIAssistantCenter", "Current active profile ID: $currentProfileId")
            
            // 打印所有档案信息
            profiles.forEachIndexed { index, profile ->
                android.util.Log.d("AIAssistantCenter", "Profile $index: ${profile.name} (ID: ${profile.id})")
            }
            
            // 如果没有档案，确保至少有一个默认档案
            val finalProfiles = if (profiles.isEmpty()) {
                android.util.Log.d("AIAssistantCenter", "No profiles found, creating default profile")
                val defaultProfile = com.example.aifloatingball.model.PromptProfile.DEFAULT
                settingsManager.savePromptProfile(defaultProfile)
                settingsManager.setActivePromptProfileId(defaultProfile.id)
                listOf(defaultProfile)
            } else {
                profiles
            }
            
            if (finalProfiles.isEmpty()) {
                // 显示空状态
                profilesRecyclerView.visibility = android.view.View.GONE
                emptyProfilesText.visibility = android.view.View.VISIBLE
                android.util.Log.d("AIAssistantCenter", "Still no profiles after creating default, showing empty text")
            } else {
                // 显示档案列表
                profilesRecyclerView.visibility = android.view.View.VISIBLE
                emptyProfilesText.visibility = android.view.View.GONE
                
                // 使用ProfileListAdapter，与布局文件item_prompt_profile匹配
                profileListAdapter = com.example.aifloatingball.adapter.ProfileListAdapter(
                    requireContext(),
                    finalProfiles,
                    currentProfileId
                ) { selectedProfile ->
                    // 档案选择回调
                    android.util.Log.d("AIAssistantCenter", "Profile selected: ${selectedProfile.name}")
                    settingsManager.setActivePromptProfileId(selectedProfile.id)
                    loadCurrentProfile()
                    loadProfileList() // 重新加载列表以更新选中状态
                    android.widget.Toast.makeText(
                        requireContext(),
                        "已切换到档案: ${selectedProfile.name}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                
                profilesRecyclerView.adapter = profileListAdapter
                android.util.Log.d("AIAssistantCenter", "ProfileListAdapter set with ${finalProfiles.size} items")
            }
        } catch (e: Exception) {
            android.util.Log.e("AIAssistantCenter", "加载档案列表失败", e)
            // 显示错误状态
            profilesRecyclerView.visibility = android.view.View.GONE
            emptyProfilesText.visibility = android.view.View.VISIBLE
            emptyProfilesText.text = "加载档案失败: ${e.localizedMessage}"
        }
    }
    
    private fun registerProfileChangeListener() {
        settingsManager.registerOnSettingChangeListener<List<com.example.aifloatingball.model.PromptProfile>>("prompt_profiles") { key, value ->
            android.util.Log.d("BasicInfoFragment", "档案列表已更新，重新加载档案")
            requireActivity().runOnUiThread {
                loadCurrentProfile()
                loadProfileList() // 同时重新加载档案列表
            }
        }
    }
    
    private fun loadCurrentSettings() {
        val isAiSearchModeEnabled = settingsManager.getIsAIMode()
        aiSearchModeSwitch.isChecked = isAiSearchModeEnabled
        
        // 加载当前档案
        loadCurrentProfile()
    }
    
    private fun loadCurrentProfile() {
        try {
            val profiles = settingsManager.getPromptProfiles()
            val activeProfileId = settingsManager.getActivePromptProfileId()
            val currentProfile = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()
            
            if (currentProfile != null) {
                currentProfileName.text = currentProfile.name
                profileNameInput.setText(currentProfile.name)
                identityInput.setText(currentProfile.persona)
                conversationStyleInput.setText(currentProfile.tone)
                answerFormatInput.setText(currentProfile.outputFormat)
                
                android.util.Log.d("BasicInfoFragment", "加载当前档案: ${currentProfile.name}")
            } else {
                currentProfileName.text = "默认画像"
                android.util.Log.w("BasicInfoFragment", "没有找到当前档案")
            }
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoFragment", "加载当前档案失败", e)
        }
    }
    
    private fun setupListeners() {
        // AI搜索模式开关
        aiSearchModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setIsAIMode(isChecked)
            android.util.Log.d("BasicInfoFragment", "AI搜索模式: $isChecked")
        }
        
        // 设置下拉菜单
        setupDropdowns()
        
        // 档案管理按钮
        selectProfileButton.setOnClickListener {
            showProfileSelector()
        }
        
        newProfileButton.setOnClickListener {
            showNewProfileDialog()
        }
        
        manageProfilesButton.setOnClickListener {
            openProfileManagement()
        }
        
        saveProfileButton.setOnClickListener {
            saveCurrentProfile()
        }
    }
    
    private fun setupDropdowns() {
        try {
            // 设置对话风格下拉菜单
            val conversationStyles = arrayOf(
                "友好、清晰、简洁",
                "专业、严谨、详细",
                "幽默、轻松、有趣",
                "直接、简洁、高效",
                "温和、耐心、细致"
            )
            val conversationStyleAdapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                conversationStyles
            )
            conversationStyleInput.setAdapter(conversationStyleAdapter)
            
            // 设置回答格式下拉菜单
            val answerFormats = arrayOf(
                "使用Markdown格式进行回复",
                "使用纯文本格式进行回复",
                "使用结构化格式进行回复",
                "使用列表格式进行回复",
                "使用表格格式进行回复"
            )
            val answerFormatAdapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                answerFormats
            )
            answerFormatInput.setAdapter(answerFormatAdapter)
            
            android.util.Log.d("BasicInfoFragment", "下拉菜单设置完成")
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoFragment", "设置下拉菜单失败", e)
        }
    }
    
    private fun showProfileSelector() {
        try {
            val profiles = settingsManager.getPromptProfiles()
            if (profiles.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "没有可用的档案，请先创建档案", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            val profileNames = profiles.map { it.name }.toTypedArray()
            val currentProfileId = settingsManager.getActivePromptProfileId()
            val currentIndex = profiles.indexOfFirst { it.id == currentProfileId }.coerceAtLeast(0)
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("选择档案")
                .setSingleChoiceItems(profileNames, currentIndex) { dialog, which ->
                    val selectedProfile = profiles[which]
                    settingsManager.setActivePromptProfileId(selectedProfile.id)
                    loadCurrentProfile()
                    android.widget.Toast.makeText(requireContext(), "已切换到档案: ${selectedProfile.name}", android.widget.Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
                
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoFragment", "显示档案选择器失败", e)
            android.widget.Toast.makeText(requireContext(), "显示档案选择器失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showNewProfileDialog() {
        try {
            val input = android.widget.EditText(requireContext()).apply {
                hint = "请输入档案名称"
                setPadding(32, 16, 32, 16)
            }
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("新建AI指令档案")
                .setMessage("请输入新档案的名称：")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        try {
                            val newProfile = com.example.aifloatingball.model.PromptProfile(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                persona = "一个乐于助人的通用AI助手",
                                tone = "友好、清晰、简洁",
                                formality = "适中",
                                responseLength = "适中",
                                outputFormat = "使用Markdown格式进行回复",
                                language = "中文",
                                description = "新建的AI助手档案"
                            )
                            
                            // 保存新档案
                            settingsManager.savePromptProfile(newProfile)
                            
                            // 设置为当前活跃档案
                            settingsManager.setActivePromptProfileId(newProfile.id)
                            
                            android.widget.Toast.makeText(requireContext(), "档案「$name」创建成功", android.widget.Toast.LENGTH_SHORT).show()
                            
                            // 重新加载档案列表
                            loadProfileList()
                            
                            android.util.Log.d("BasicInfoFragment", "新档案创建成功: $name")
                            
                        } catch (e: Exception) {
                            android.util.Log.e("BasicInfoFragment", "保存新档案失败", e)
                            android.widget.Toast.makeText(requireContext(), "保存档案失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(requireContext(), "请输入档案名称", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
                
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoFragment", "显示新建档案对话框失败", e)
            android.widget.Toast.makeText(requireContext(), "无法显示新建档案对话框", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openProfileManagement() {
        try {
            val intent = android.content.Intent(requireContext(), com.example.aifloatingball.MasterPromptSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoFragment", "打开档案管理失败", e)
            android.widget.Toast.makeText(requireContext(), "打开档案管理失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveCurrentProfile() {
        try {
            val profiles = settingsManager.getPromptProfiles()
            val activeProfileId = settingsManager.getActivePromptProfileId()
            val currentProfile = profiles.find { it.id == activeProfileId }
            
            if (currentProfile != null) {
                val updatedProfile = currentProfile.copy(
                    name = profileNameInput.text.toString().trim().ifEmpty { currentProfile.name },
                    persona = identityInput.text.toString().trim().ifEmpty { currentProfile.persona },
                    tone = conversationStyleInput.text.toString().trim().ifEmpty { currentProfile.tone },
                    outputFormat = answerFormatInput.text.toString().trim().ifEmpty { currentProfile.outputFormat }
                )
                
                // 保存更新后的档案
                settingsManager.savePromptProfile(updatedProfile)
                
                android.widget.Toast.makeText(requireContext(), "档案保存成功", android.widget.Toast.LENGTH_SHORT).show()
                android.util.Log.d("BasicInfoFragment", "档案保存成功: ${updatedProfile.name}")
            } else {
                android.widget.Toast.makeText(requireContext(), "没有找到当前档案", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoFragment", "保存档案失败", e)
            android.widget.Toast.makeText(requireContext(), "保存档案失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupSwitchListener() {
        aiSearchModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setIsAIMode(isChecked)
            android.widget.Toast.makeText(
                requireContext(),
                if (isChecked) "已启用默认AI搜索模式" else "已关闭默认AI搜索模式",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * 扩展配置Fragment
 */
class ExtendedConfigFragment : AIAssistantCenterFragment() {
    override fun getLayoutResId(): Int = R.layout.ai_assistant_extended_config_fragment
}

/**
 * AI行为Fragment
 */
class AIBehaviorFragment : AIAssistantCenterFragment() {
    override fun getLayoutResId(): Int = R.layout.ai_assistant_behavior_fragment
}


/**
 * Prompt社区Fragment
 */
class TaskFragment : AIAssistantCenterFragment() {
    // 搜索相关
    private lateinit var searchInput: android.widget.EditText
    private lateinit var searchButton: android.widget.ImageButton
    private lateinit var searchSuggestionsRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var searchSuggestionAdapter: com.example.aifloatingball.adapter.PromptSearchSuggestionAdapter
    
    // 顶部快捷入口
    private lateinit var hotPromptCard: androidx.cardview.widget.CardView
    private lateinit var latestPromptCard: androidx.cardview.widget.CardView
    private lateinit var myCollectionCard: androidx.cardview.widget.CardView
    
    // 分类导航
    private lateinit var categoryRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var categoryAdapter: com.example.aifloatingball.adapter.PromptCategoryAdapter
    
    // Prompt内容列表
    private lateinit var promptContentRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var promptAdapter: com.example.aifloatingball.adapter.PromptCommunityAdapter
    
    // 上传按钮
    private lateinit var uploadPromptButton: android.widget.ImageButton
    
    // 空状态提示
    private lateinit var emptyPromptText: android.widget.TextView
    
    // 当前筛选状态
    private var currentFilter: com.example.aifloatingball.model.FilterType = com.example.aifloatingball.model.FilterType.HOT
    private var selectedCategory: com.example.aifloatingball.model.PromptCategory? = null
    
    // 搜索历史
    private val searchHistory = mutableListOf<String>()
    
    // 高频场景快捷栏
    private lateinit var scenarioOfficeLayout: LinearLayout
    private lateinit var scenarioEducationLayout: LinearLayout
    private lateinit var scenarioLifeLayout: LinearLayout
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_prompt_community_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews(view)
        setupCategoryRecyclerView()
        setupPromptContentRecyclerView()
        setupSearchSuggestions()
        setupSearch()
        setupQuickFilters()
        setupUploadButton()
        setupHighFrequencyScenarios()
        
        // 加载初始数据
        loadPrompts(currentFilter)
    }
    
    private fun setupViews(view: android.view.View) {
        // 搜索相关
        searchInput = view.findViewById(R.id.prompt_search_input)
        searchButton = view.findViewById(R.id.prompt_search_button)
        searchSuggestionsRecyclerView = view.findViewById(R.id.search_suggestions_recycler_view)
        
        // 高频场景快捷栏
        scenarioOfficeLayout = view.findViewById(R.id.scenario_office)
        scenarioEducationLayout = view.findViewById(R.id.scenario_education)
        scenarioLifeLayout = view.findViewById(R.id.scenario_life)
        
        // 快捷入口卡片
        hotPromptCard = view.findViewById(R.id.hot_prompt_card)
        latestPromptCard = view.findViewById(R.id.latest_prompt_card)
        myCollectionCard = view.findViewById(R.id.my_collection_card)
        
        // 分类导航
        categoryRecyclerView = view.findViewById(R.id.category_recycler_view)
        
        // Prompt内容列表
        promptContentRecyclerView = view.findViewById(R.id.prompt_content_recycler_view)
        
        // 上传按钮
        uploadPromptButton = view.findViewById(R.id.upload_prompt_button)
        
        // 空状态提示
        emptyPromptText = view.findViewById(R.id.empty_prompt_text)
    }
    
    private fun setupCategoryRecyclerView() {
        // 只显示主要分类：功能分类、高频场景、热门推荐、我的内容
        val mainCategories = listOf(
            com.example.aifloatingball.model.PromptCategory.FUNCTIONAL,
            com.example.aifloatingball.model.PromptCategory.HIGH_FREQUENCY,
            com.example.aifloatingball.model.PromptCategory.POPULAR,
            com.example.aifloatingball.model.PromptCategory.MY_CONTENT
        )
        
        categoryAdapter = com.example.aifloatingball.adapter.PromptCategoryAdapter(mainCategories) { category ->
            selectedCategory = category
            // 显示分类筛选面板
            showCategoryFilterPanel(category)
        }
        
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(),
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )
        categoryRecyclerView.layoutManager = layoutManager
        categoryRecyclerView.adapter = categoryAdapter
    }
    
    private fun showCategoryFilterPanel(category: com.example.aifloatingball.model.PromptCategory) {
        val dialog = com.example.aifloatingball.dialog.CategoryFilterPanelDialog(
            requireContext(),
            category
        ) { selectedSubCategory ->
            // 用户选择了子分类
            selectedCategory = selectedSubCategory
            loadPromptsByCategory(selectedSubCategory)
            android.util.Log.d("TaskFragment", "选择了子分类: ${selectedSubCategory.displayName}")
        }
        dialog.show()
    }
    
    private fun setupPromptContentRecyclerView() {
        promptAdapter = com.example.aifloatingball.adapter.PromptCommunityAdapter(
            emptyList(),
            onItemClick = { prompt -> onPromptItemClick(prompt) },
            onLikeClick = { prompt -> onPromptLikeClick(prompt) },
            onCollectClick = { prompt -> onPromptCollectClick(prompt) },
            onCommentClick = { prompt -> onPromptCommentClick(prompt) },
            onShareClick = { prompt -> onPromptShareClick(prompt) }
        )
        
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        promptContentRecyclerView.layoutManager = layoutManager
        promptContentRecyclerView.adapter = promptAdapter
    }
    
    private fun setupSearchSuggestions() {
        searchSuggestionAdapter = com.example.aifloatingball.adapter.PromptSearchSuggestionAdapter(
            com.example.aifloatingball.data.PromptCommunityData.getHotKeywords()
        ) { suggestion ->
            // 点击搜索建议
            searchInput.setText(suggestion)
            hideSearchSuggestions()
            performSearch(suggestion)
        }
        
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        searchSuggestionsRecyclerView.layoutManager = layoutManager
        searchSuggestionsRecyclerView.adapter = searchSuggestionAdapter
    }
    
    private fun showSearchSuggestions() {
        searchSuggestionsRecyclerView.visibility = android.view.View.VISIBLE
    }
    
    private fun hideSearchSuggestions() {
        searchSuggestionsRecyclerView.visibility = android.view.View.GONE
    }
    
    private fun updateSearchSuggestions(query: String) {
        val suggestions = com.example.aifloatingball.data.PromptCommunityData.getSearchSuggestions(query)
        searchSuggestionAdapter.updateData(suggestions)
        
        if (suggestions.isNotEmpty()) {
            showSearchSuggestions()
        } else {
            hideSearchSuggestions()
        }
    }
    
    private fun setupSearch() {
        // 搜索框点击，显示搜索面板
        searchInput.setOnClickListener {
            showSearchPanel()
        }
        
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showSearchPanel()
            }
        }
        
        // 搜索按钮点击，显示搜索面板
        searchButton.setOnClickListener {
            showSearchPanel()
        }
    }
    
    private fun showSearchPanel() {
        val dialog = com.example.aifloatingball.dialog.PromptSearchPanelDialog(
            requireContext()
        ) { query ->
            performSearch(query)
        }
        dialog.show()
    }
    
    private fun setupQuickFilters() {
        hotPromptCard.setOnClickListener {
            // 显示热门Prompt面板
            val dialog = com.example.aifloatingball.dialog.PromptFilterPanelDialog(
                requireContext(),
                com.example.aifloatingball.dialog.PromptFilterPanelDialog.FilterPanelType.HOT
            ) { prompt ->
                // 点击Prompt时的处理
                showPromptDetail(prompt)
            }
            dialog.show()
            android.util.Log.d("TaskFragment", "打开热门Prompt面板")
        }
        
        latestPromptCard.setOnClickListener {
            // 显示新发布Prompt面板
            val dialog = com.example.aifloatingball.dialog.PromptFilterPanelDialog(
                requireContext(),
                com.example.aifloatingball.dialog.PromptFilterPanelDialog.FilterPanelType.LATEST
            ) { prompt ->
                // 点击Prompt时的处理
                showPromptDetail(prompt)
            }
            dialog.show()
            android.util.Log.d("TaskFragment", "打开新发布Prompt面板")
        }
        
        myCollectionCard.setOnClickListener {
            // 显示我的收藏Prompt面板
            val dialog = com.example.aifloatingball.dialog.PromptFilterPanelDialog(
                requireContext(),
                com.example.aifloatingball.dialog.PromptFilterPanelDialog.FilterPanelType.MY_COLLECTION
            ) { prompt ->
                // 点击Prompt时的处理
                showPromptDetail(prompt)
            }
            dialog.show()
            android.util.Log.d("TaskFragment", "打开我的收藏Prompt面板")
        }
    }
    
    
    private fun setupUploadButton() {
        uploadPromptButton.setOnClickListener {
            showUploadPromptDialog()
        }
    }
    
    /**
     * 设置高频场景快捷栏点击事件
     */
    private fun setupHighFrequencyScenarios() {
        // 职场办公
        scenarioOfficeLayout.setOnClickListener {
            selectedCategory = com.example.aifloatingball.model.PromptCategory.WORKPLACE_OFFICE
            loadPromptsByCategory(com.example.aifloatingball.model.PromptCategory.WORKPLACE_OFFICE)
            android.util.Log.d("TaskFragment", "点击职场办公")
        }
        
        // 教育学习
        scenarioEducationLayout.setOnClickListener {
            selectedCategory = com.example.aifloatingball.model.PromptCategory.EDUCATION_STUDY
            loadPromptsByCategory(com.example.aifloatingball.model.PromptCategory.EDUCATION_STUDY)
            android.util.Log.d("TaskFragment", "点击教育学习")
        }
        
        // 生活服务
        scenarioLifeLayout.setOnClickListener {
            selectedCategory = com.example.aifloatingball.model.PromptCategory.LIFE_SERVICE
            loadPromptsByCategory(com.example.aifloatingball.model.PromptCategory.LIFE_SERVICE)
            android.util.Log.d("TaskFragment", "点击生活服务")
        }
    }
    
    private fun loadPrompts(filterType: com.example.aifloatingball.model.FilterType) {
        val prompts = com.example.aifloatingball.data.PromptCommunityData.getPromptsByFilter(filterType)
        updatePromptList(prompts)
    }
    
    private fun loadPromptsByCategory(category: com.example.aifloatingball.model.PromptCategory) {
        val prompts = com.example.aifloatingball.data.PromptCommunityData.getPromptsByCategory(category)
        updatePromptList(prompts)
    }
    
    private fun updatePromptList(prompts: List<com.example.aifloatingball.model.PromptCommunityItem>) {
        if (prompts.isEmpty()) {
            emptyPromptText.visibility = android.view.View.VISIBLE
            promptContentRecyclerView.visibility = android.view.View.GONE
        } else {
            emptyPromptText.visibility = android.view.View.GONE
            promptContentRecyclerView.visibility = android.view.View.VISIBLE
            promptAdapter = com.example.aifloatingball.adapter.PromptCommunityAdapter(
                prompts,
                onItemClick = { prompt -> onPromptItemClick(prompt) },
                onLikeClick = { prompt -> onPromptLikeClick(prompt) },
                onCollectClick = { prompt -> onPromptCollectClick(prompt) },
                onCommentClick = { prompt -> onPromptCommentClick(prompt) },
                onShareClick = { prompt -> onPromptShareClick(prompt) }
            )
            promptContentRecyclerView.adapter = promptAdapter
        }
    }
    
    private fun onPromptItemClick(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        showPromptDetail(prompt)
    }
    
    private fun onPromptLikeClick(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        android.widget.Toast.makeText(requireContext(), "点赞功能开发中", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun onPromptCollectClick(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        android.widget.Toast.makeText(requireContext(), "收藏功能开发中", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun onPromptCommentClick(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        android.widget.Toast.makeText(requireContext(), "评论功能开发中", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun onPromptShareClick(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        sharePrompt(prompt)
    }
    
    private fun showPromptDetail(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(prompt.title)
            .setMessage(prompt.content)
            .setPositiveButton("使用此Prompt") { _, _ ->
                usePrompt(prompt)
            }
            .setNegativeButton("关闭", null)
            .create()
        dialog.show()
    }
    
    private fun usePrompt(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        android.widget.Toast.makeText(requireContext(), "Prompt已应用", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun sharePrompt(prompt: com.example.aifloatingball.model.PromptCommunityItem) {
        val shareText = "分享Prompt：${prompt.title}\n\n${prompt.content}\n\n来自Prompt社区"
        
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        
        val chooser = android.content.Intent.createChooser(shareIntent, "分享Prompt")
        startActivity(chooser)
    }
    
    private fun showUploadPromptDialog() {
        android.widget.Toast.makeText(requireContext(), "上传功能开发中", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun onTaskSelected(template: com.example.aifloatingball.model.PromptTemplate) {
        // 保留原有功能，以便向后兼容
        showKeywordInputDialog(template)
    }
    
    /**
     * 显示问题输入弹窗
     */
    private fun showKeywordInputDialog(template: com.example.aifloatingball.model.PromptTemplate) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("${template.icon} ${template.intentName}")
            .setMessage("请提出您的问题，AI专家们将一起讨论并为您提供解决方案：")
            .create()
        
        // 创建输入框
        val input = android.widget.EditText(requireContext()).apply {
            hint = "请输入您的问题..."
            setPadding(32, 16, 32, 16)
            minLines = 3
            maxLines = 5
        }
        
        // 设置对话框布局
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(input)
        }
        
        dialog.setView(layout)
        
        // 设置按钮
        dialog.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "创建专家群聊") { _, _ ->
            val question = input.text.toString().trim()
            if (question.isNotEmpty()) {
                createGroupChatForTask(template, question)
            } else {
                android.widget.Toast.makeText(requireContext(), "请输入您的问题", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.setButton(android.app.AlertDialog.BUTTON_NEGATIVE, "取消") { _, _ ->
            dialog.dismiss()
        }
        
        dialog.show()
        
        // 自动聚焦到输入框
        input.requestFocus()
    }
    
    /**
     * 为任务创建群聊
     */
    private fun createGroupChatForTask(template: com.example.aifloatingball.model.PromptTemplate, question: String) {
        try {
            // 获取已配置API的AI服务类型
            val availableAIServices = getAvailableAIServices()
            
            if (availableAIServices.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "没有可用的AI服务，请先配置API", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            // 创建群聊名称和描述
            val groupName = "${template.intentName} - 专家讨论"
            val groupDescription = "基于${template.intentName}任务的专家群聊，AI专家们将一起讨论您的问题"
            
            // 创建群聊
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            val groupChat = groupChatManager.createGroupChat(
                name = groupName,
                description = groupDescription,
                aiMembers = availableAIServices
            )
            
            // 发送用户问题到群聊（包含专家prompt）
            sendUserQuestionToGroup(groupChat, question, template)
            
            // 跳转到群聊界面
            val chatIntent = android.content.Intent(requireContext(), com.example.aifloatingball.ChatActivity::class.java)
            val groupContact = com.example.aifloatingball.model.ChatContact(
                id = groupChat.id,
                name = groupChat.name,
                avatar = groupChat.avatar,
                type = com.example.aifloatingball.model.ContactType.GROUP,
                description = groupChat.description,
                isOnline = true,
                lastMessage = "群聊已创建",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = false,
                isMuted = false,
                groupId = groupChat.id,
                memberCount = groupChat.members.size,
                aiMembers = groupChat.members.filter { it.type == com.example.aifloatingball.model.MemberType.AI }.map { it.name }
            )
            chatIntent.putExtra(com.example.aifloatingball.ChatActivity.EXTRA_CONTACT, groupContact)
            startActivity(chatIntent)
            
            android.widget.Toast.makeText(requireContext(), "专家群聊创建成功，AI专家们正在讨论您的问题", android.widget.Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "创建群聊失败", e)
            android.widget.Toast.makeText(requireContext(), "创建群聊失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 发送专家身份提示词到群聊
     */
    private fun sendExpertPromptToGroup(
        groupChat: com.example.aifloatingball.model.GroupChat,
        template: com.example.aifloatingball.model.PromptTemplate
    ) {
        try {
            val expertPrompt = generateExpertPrompt(template)
            
            // 创建系统消息
            val systemMessage = com.example.aifloatingball.model.GroupChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                content = expertPrompt,
                senderId = "system",
                senderName = "系统",
                senderType = com.example.aifloatingball.model.MemberType.AI,
                timestamp = System.currentTimeMillis(),
                messageType = com.example.aifloatingball.model.GroupMessageType.SYSTEM
            )
            
            // 添加到群聊消息列表
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            groupChatManager.addMessageToGroup(groupChat.id, systemMessage)
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "发送专家提示词失败", e)
        }
    }
    
    /**
     * 发送用户问题到群聊
     */
    private fun sendUserQuestionToGroup(
        groupChat: com.example.aifloatingball.model.GroupChat,
        question: String,
        template: com.example.aifloatingball.model.PromptTemplate
    ) {
        try {
            // 构建包含专家prompt的完整问题
            val expertPrompt = generateExpertPrompt(template)
            val contextualQuestion = buildString {
                appendLine(expertPrompt)
                appendLine()
                appendLine("用户问题：$question")
            }
            
            // 创建用户消息（包含专家prompt）
            val userMessage = com.example.aifloatingball.model.GroupChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                content = contextualQuestion,
                senderId = "user",
                senderName = "用户",
                senderType = com.example.aifloatingball.model.MemberType.USER,
                timestamp = System.currentTimeMillis(),
                messageType = com.example.aifloatingball.model.GroupMessageType.TEXT,
                metadata = mapOf("originalContent" to question)
            )
            
            // 添加到群聊消息列表
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            groupChatManager.addMessageToGroup(groupChat.id, userMessage)
            
            // 立即触发AI自动回复
            triggerAIAutoReplies(groupChat, contextualQuestion)
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "发送用户问题失败", e)
        }
    }
    
    /**
     * 获取已配置API的AI服务类型
     */
    private fun getAvailableAIServices(): List<AIServiceType> {
        val settingsManager = com.example.aifloatingball.SettingsManager.getInstance(requireContext())
        val availableServices = mutableListOf<AIServiceType>()
        
        // 检查各个AI服务的API配置
        if (settingsManager.getDeepSeekApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.DEEPSEEK)
        }
        
        if (settingsManager.getKimiApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.KIMI)
        }
        
        if (settingsManager.getString("zhipu_ai_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.ZHIPU_AI)
        }
        
        if (settingsManager.getString("chatgpt_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.CHATGPT)
        }
        
        if (settingsManager.getString("claude_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.CLAUDE)
        }
        
        if (settingsManager.getQianwenApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.QIANWEN)
        }
        
        if (settingsManager.getString("xinghuo_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.XINGHUO)
        }
        
        if (settingsManager.getWenxinApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.WENXIN)
        }
        
        if (settingsManager.getGeminiApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.GEMINI)
        }
        
        // 始终添加临时专线（无需API）
        availableServices.add(AIServiceType.TEMP_SERVICE)
        
        android.util.Log.d("TaskFragment", "可用AI服务: ${availableServices.map { it.name }}")
        return availableServices
    }
    
    /**
     * 触发AI自动回复
     */
    private fun triggerAIAutoReplies(
        groupChat: com.example.aifloatingball.model.GroupChat,
        question: String
    ) {
        try {
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            
            // 使用协程异步触发AI回复
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // 调用GroupChatManager的发送用户消息方法，这会自动触发AI回复
                    groupChatManager.sendUserMessage(groupChat.id, question)
                    android.util.Log.d("TaskFragment", "已触发AI自动回复")
                } catch (e: Exception) {
                    android.util.Log.e("TaskFragment", "触发AI自动回复失败", e)
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "触发AI自动回复失败", e)
        }
    }
    
    /**
     * 生成专家身份提示词
     */
    private fun generateExpertPrompt(
        template: com.example.aifloatingball.model.PromptTemplate
    ): String {
        return when (template.intentId) {
            "learn_understand" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以专业的角度一起讨论用户的问题。请从不同角度提供深入的分析和见解，相互补充和完善答案。
                
                💡 讨论要点：
                - 提供准确、专业的知识解释
                - 分享实际应用经验和案例
                - 提出不同的观点和解决方案
                - 相互补充和完善答案
                
                请开始专家讨论！
            """.trimIndent()
            
            "solve_problem" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以问题解决专家的身份一起分析用户的问题。请从不同角度提供解决方案，相互讨论和完善建议。
                
                💡 讨论要点：
                - 分析问题的根本原因
                - 提供多种解决方案
                - 分享相关经验和案例
                - 相互补充和完善建议
                
                请开始专家讨论！
            """.trimIndent()
            
            "create_content" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以内容创作专家的身份一起帮助用户。请从不同角度提供创意和指导，相互激发灵感。
                
                💡 讨论要点：
                - 提供创意灵感和方向
                - 分享创作技巧和经验
                - 讨论不同的表达方式
                - 相互补充和完善建议
                
                请开始专家讨论！
            """.trimIndent()
            
            "analyze_data" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以数据分析专家的身份一起分析用户的问题。请从不同角度提供分析方法和洞察。
                
                💡 讨论要点：
                - 提供专业的分析方法
                - 分享数据分析经验
                - 讨论不同的分析角度
                - 相互补充和完善见解
                
                请开始专家讨论！
            """.trimIndent()
            
            "translate_optimize" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以翻译润色专家的身份一起帮助用户。请从不同角度提供翻译和优化建议。
                
                💡 讨论要点：
                - 提供准确的翻译建议
                - 分享语言表达技巧
                - 讨论不同的表达方式
                - 相互补充和完善建议
                
                请开始专家讨论！
            """.trimIndent()
            
            else -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以专业的角度一起讨论用户的问题。请从不同角度提供深入的分析和见解，相互补充和完善答案。
                
                💡 讨论要点：
                - 提供专业、准确的分析
                - 分享相关经验和案例
                - 提出不同的观点和解决方案
                - 相互补充和完善答案
                
                请开始专家讨论！
            """.trimIndent()
        }
    }
    
    private fun performSearch(query: String) {
        // 隐藏搜索建议
        hideSearchSuggestions()
        
        // 隐藏键盘
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        
        // 添加到搜索历史
        if (query !in searchHistory) {
            searchHistory.add(0, query)
            // 限制历史记录数量
            if (searchHistory.size > 10) {
                searchHistory.removeAt(searchHistory.size - 1)
            }
        }
        
        // 执行搜索逻辑
        val results = com.example.aifloatingball.data.PromptCommunityData.searchPrompts(query)
        
        // 更新列表
        updatePromptList(results)
        
        // 更新筛选状态，清空分类选择
        selectedCategory = null
        categoryAdapter.setSelectedCategory(null)
        
        if (results.isEmpty()) {
            emptyPromptText.text = "未找到与「$query」相关的Prompt"
            android.util.Log.d("TaskFragment", "搜索「$query」未找到结果")
        } else {
            android.util.Log.d("TaskFragment", "搜索「$query」找到 ${results.size} 个结果")
        }
    }
}
