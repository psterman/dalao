package com.example.aifloatingball.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import kotlinx.coroutines.*
import java.util.UUID

/**
 * 基础信息子页面Fragment - 档案管理功能
 */
class BasicInfoSubpageFragment : Fragment() {
    private lateinit var settingsManager: SettingsManager
    
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
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.basic_info_subpage, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
    
    private fun initializeViews(view: View) {
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
            android.util.Log.d("BasicInfoSubpage", "setupProfileList called")
            // 设置RecyclerView布局管理器
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                requireContext(),
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
            profilesRecyclerView.layoutManager = layoutManager
            
            // 加载档案列表
            loadProfileList()
            
            android.util.Log.d("BasicInfoSubpage", "档案列表设置完成")
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoSubpage", "设置档案列表失败", e)
        }
    }
    
    private fun loadProfileList() {
        try {
            android.util.Log.d("BasicInfoSubpage", "loadProfileList called")
            val profiles = settingsManager.getPromptProfiles()
            val currentProfileId = settingsManager.getActivePromptProfileId()
            
            android.util.Log.d("BasicInfoSubpage", "Loaded profiles count: ${profiles.size}")
            android.util.Log.d("BasicInfoSubpage", "Current active profile ID: $currentProfileId")
            
            // 打印所有档案信息
            profiles.forEachIndexed { index, profile ->
                android.util.Log.d("BasicInfoSubpage", "Profile $index: ${profile.name} (ID: ${profile.id})")
            }
            
            // 如果没有档案，确保至少有一个默认档案
            val finalProfiles = if (profiles.isEmpty()) {
                android.util.Log.d("BasicInfoSubpage", "No profiles found, creating default profile")
                val defaultProfile = com.example.aifloatingball.model.PromptProfile.DEFAULT
                settingsManager.savePromptProfile(defaultProfile)
                settingsManager.setActivePromptProfileId(defaultProfile.id)
                listOf(defaultProfile)
            } else {
                profiles
            }
            
            if (finalProfiles.isEmpty()) {
                // 显示空状态
                profilesRecyclerView.visibility = View.GONE
                emptyProfilesText.visibility = View.VISIBLE
                android.util.Log.d("BasicInfoSubpage", "Still no profiles after creating default, showing empty text")
            } else {
                // 显示档案列表
                profilesRecyclerView.visibility = View.VISIBLE
                emptyProfilesText.visibility = View.GONE
                
                // 使用ProfileListAdapter，与布局文件item_prompt_profile匹配
                profileListAdapter = com.example.aifloatingball.adapter.ProfileListAdapter(
                    requireContext(),
                    finalProfiles,
                    currentProfileId
                ) { selectedProfile ->
                    // 档案选择回调
                    android.util.Log.d("BasicInfoSubpage", "Profile selected: ${selectedProfile.name}")
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
                android.util.Log.d("BasicInfoSubpage", "ProfileListAdapter set with ${finalProfiles.size} items")
            }
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoSubpage", "加载档案列表失败", e)
            // 显示错误状态
            profilesRecyclerView.visibility = View.GONE
            emptyProfilesText.visibility = View.VISIBLE
            emptyProfilesText.text = "加载档案失败: ${e.localizedMessage}"
        }
    }
    
    private fun registerProfileChangeListener() {
        settingsManager.registerOnSettingChangeListener<List<com.example.aifloatingball.model.PromptProfile>>("prompt_profiles") { key, value ->
            android.util.Log.d("BasicInfoSubpage", "档案列表已更新，重新加载档案")
            requireActivity().runOnUiThread {
                loadCurrentProfile()
                loadProfileList() // 同时重新加载档案列表
            }
        }
    }
    
    private fun loadCurrentSettings() {
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
                
                android.util.Log.d("BasicInfoSubpage", "加载当前档案: ${currentProfile.name}")
            } else {
                currentProfileName.text = "默认画像"
                android.util.Log.w("BasicInfoSubpage", "没有找到当前档案")
            }
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoSubpage", "加载当前档案失败", e)
        }
    }
    
    private fun setupListeners() {
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
            android.util.Log.e("BasicInfoSubpage", "显示档案选择器失败", e)
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
                            
                            android.util.Log.d("BasicInfoSubpage", "新档案创建成功: $name")
                            
                        } catch (e: Exception) {
                            android.util.Log.e("BasicInfoSubpage", "保存新档案失败", e)
                            android.widget.Toast.makeText(requireContext(), "保存档案失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(requireContext(), "请输入档案名称", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
                
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoSubpage", "显示新建档案对话框失败", e)
            android.widget.Toast.makeText(requireContext(), "无法显示新建档案对话框", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openProfileManagement() {
        try {
            val intent = android.content.Intent(requireContext(), com.example.aifloatingball.MasterPromptSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoSubpage", "打开档案管理失败", e)
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
                android.util.Log.d("BasicInfoSubpage", "档案保存成功: ${updatedProfile.name}")
            } else {
                android.widget.Toast.makeText(requireContext(), "没有找到当前档案", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("BasicInfoSubpage", "保存档案失败", e)
            android.widget.Toast.makeText(requireContext(), "保存档案失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
