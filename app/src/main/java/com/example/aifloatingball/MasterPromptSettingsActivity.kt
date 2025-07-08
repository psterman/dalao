package com.example.aifloatingball

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.ProfileAdapter
import com.example.aifloatingball.model.PromptProfile
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID
import android.util.Log
import android.app.AlertDialog

class MasterPromptSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var profilesRecyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter

    private lateinit var editProfileName: TextInputEditText
    private lateinit var editPersona: TextInputEditText
    private lateinit var editTone: TextInputEditText
    private lateinit var editOutputFormat: TextInputEditText
    private lateinit var editCustomInstructions: TextInputEditText
    
    // 基础UI字段
    // 注意：扩展字段的UI控件在当前布局中可能不存在

    private var profiles: MutableList<PromptProfile> = mutableListOf()
    private var activeProfile: PromptProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_prompt_settings)
        settingsManager = SettingsManager.getInstance(this)

        setupToolbar()
        setupViews()
        setupRecyclerView()
        loadProfiles()
        
        // 添加数据追溯调试功能
        debugShowAllSavedData()
        
        // 同时显示原始存储数据
        debugShowRawStorageData()
    }
    
    // 显示原始存储数据
    private fun debugShowRawStorageData() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val allData = prefs.all
        
        Log.d("AI_RAW_DATA", "=== 原始存储数据追溯 ===")
        Log.d("AI_RAW_DATA", "SharedPreferences中总共有 ${allData.size} 个键值对")
        
        // 检查所有可能包含配置数据的键
        val configKeys = listOf(
            "prompt_profiles",
            "prompt_profile", 
            "ai_profiles",
            "master_prompt_profiles",
            "user_profiles",
            "saved_profiles"
        )
        
        var foundAnyData = false
        configKeys.forEach { key ->
            val data = prefs.getString(key, null)
            if (data != null) {
                foundAnyData = true
                Log.d("AI_RAW_DATA", "发现键: '$key'")
                Log.d("AI_RAW_DATA", "数据长度: ${data.length} 字符")
                Log.d("AI_RAW_DATA", "内容预览: ${data.take(200)}...")
                
                // 尝试分析JSON结构
                try {
                    if (data.contains("\"name\"") && data.contains("\"persona\"")) {
                        Log.d("AI_RAW_DATA", "✅ 检测到有效的配置文件JSON结构")
                        
                        // 计算配置数量
                        val profileCount = data.split("\"name\"").size - 1
                        Log.d("AI_RAW_DATA", "预估配置文件数量: $profileCount")
                        
                        // 提取配置名称
                        val namePattern = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        val names = namePattern.findAll(data).map { it.groupValues[1] }.toList()
                        Log.d("AI_RAW_DATA", "提取到的配置名称: ${names.joinToString(", ")}")
                        
                        // 提取角色人设
                        val personaPattern = "\"persona\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        val personas = personaPattern.findAll(data).map { it.groupValues[1] }.toList()
                        Log.d("AI_RAW_DATA", "角色人设列表: ${personas.joinToString(", ")}")
                        
                        // 检查是否有新的扩展字段
                        if (data.contains("\"expertise\"")) {
                            Log.d("AI_RAW_DATA", "包含专业领域字段")
                        }
                        if (data.contains("\"creativity\"")) {
                            Log.d("AI_RAW_DATA", "包含创造力设置")
                        }
                        if (data.contains("\"temperature\"")) {
                            Log.d("AI_RAW_DATA", "包含AI模型参数设置")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AI_RAW_DATA", "分析JSON时出错: ${e.message}")
                }
            }
        }
        
        // 如果没有找到配置键，查看所有包含"prompt"或"profile"的键
        if (!foundAnyData) {
            Log.d("AI_RAW_DATA", "未找到预期的配置键，检查所有相关键...")
            allData.forEach { (key, value) ->
                if (key.contains("prompt", ignoreCase = true) || 
                    key.contains("profile", ignoreCase = true) ||
                    key.contains("ai", ignoreCase = true)) {
                    Log.d("AI_RAW_DATA", "可能相关的键: $key = $value")
                }
            }
        }
        
        // 检查个别偏好设置字段
        val individualKeys = listOf(
            "prompt_persona", "prompt_tone", "prompt_output_format", 
            "prompt_custom_instructions", "prompt_gender", "prompt_occupation"
        )
        
        Log.d("AI_RAW_DATA", "--- 检查个别配置字段 ---")
        individualKeys.forEach { key ->
            val value = prefs.getString(key, null)
            if (value != null) {
                Log.d("AI_RAW_DATA", "$key: $value")
            }
        }
        
        Log.d("AI_RAW_DATA", "=== 原始数据追溯完成 ===")
    }
    
    // 新增调试方法 - 追溯历史AI配置数据
    private fun debugShowAllSavedData() {
        Log.d("AI_CONFIG_DEBUG", "=== AI指令中心历史数据追溯 ===")
        
        // 使用多种方法获取配置数据
        val profiles1 = settingsManager.getAllPromptProfiles()
        val profiles2 = settingsManager.getPromptProfiles()
        
        Log.d("AI_CONFIG_DEBUG", "方法1 (getAllPromptProfiles): ${profiles1.size} 个配置")
        Log.d("AI_CONFIG_DEBUG", "方法2 (getPromptProfiles): ${profiles2.size} 个配置")
        
        // 选择包含更多数据的结果
        val profiles = if (profiles1.size >= profiles2.size) profiles1 else profiles2
        val totalProfiles = profiles.size
        
        Log.d("AI_CONFIG_DEBUG", "最终使用: $totalProfiles 个保存的配置档案")
        
        if (profiles.isEmpty()) {
            Log.d("AI_CONFIG_DEBUG", "未找到任何历史配置数据")
            // 检查是否有个别字段的历史数据
            checkIndividualPromptSettings()
            showDataRecoveryDialog("未找到历史数据", "没有发现以往保存的AI指令中心配置档案。\n\n可能原因：\n1. 首次使用应用\n2. 数据被清理或重置\n3. 配置保存在其他位置")
            return
        }
        
        val debugInfo = StringBuilder()
        debugInfo.append("🔍 发现 $totalProfiles 个历史配置:\n\n")
        
        profiles.forEachIndexed { index, profile ->
            Log.d("AI_CONFIG_DEBUG", "--- 档案 ${index + 1}: ${profile.name} ---")
            Log.d("AI_CONFIG_DEBUG", "ID: ${profile.id}")
            Log.d("AI_CONFIG_DEBUG", "名称: ${profile.name}")
            Log.d("AI_CONFIG_DEBUG", "角色设定: ${profile.persona}")
            Log.d("AI_CONFIG_DEBUG", "语调风格: ${profile.tone}")
            Log.d("AI_CONFIG_DEBUG", "输出格式: ${profile.outputFormat}")
            Log.d("AI_CONFIG_DEBUG", "自定义指令: ${profile.customInstructions}")
            
            // 检查是否有新的扩展字段
            profile.expertise?.let { Log.d("AI_CONFIG_DEBUG", "专业领域: $it") }
            profile.language?.let { Log.d("AI_CONFIG_DEBUG", "语言: $it") }
            profile.creativity?.let { Log.d("AI_CONFIG_DEBUG", "创造力: $it") }
            profile.formality?.let { Log.d("AI_CONFIG_DEBUG", "正式程度: $it") }
            profile.responseLength?.let { Log.d("AI_CONFIG_DEBUG", "回复长度: $it") }
            Log.d("AI_CONFIG_DEBUG", "包含推理: ${profile.reasoning}")
            Log.d("AI_CONFIG_DEBUG", "包含示例: ${profile.examples}")
            profile.codeStyle?.let { Log.d("AI_CONFIG_DEBUG", "代码风格: $it") }
            profile.temperature?.let { Log.d("AI_CONFIG_DEBUG", "温度参数: $it") }
            profile.topP?.let { Log.d("AI_CONFIG_DEBUG", "Top-P参数: $it") }
            profile.maxTokens?.let { Log.d("AI_CONFIG_DEBUG", "最大Token: $it") }
            profile.tags?.let { Log.d("AI_CONFIG_DEBUG", "标签: ${it.joinToString(", ")}") }
            profile.description?.let { Log.d("AI_CONFIG_DEBUG", "描述: $it") }
            profile.icon?.let { Log.d("AI_CONFIG_DEBUG", "图标: $it") }
            profile.color?.let { Log.d("AI_CONFIG_DEBUG", "颜色: $it") }
            
            // 判断配置类型
            val configType = when {
                !profile.expertise.isNullOrEmpty() || profile.creativity != null -> "💼 专业配置"
                profile.name == "默认" -> "🏠 默认配置"
                else -> "📝 基础配置"
            }
            
            // 添加到用户友好的显示信息
            debugInfo.append("${index + 1}. ${profile.icon ?: "📝"} ${profile.name} $configType\n")
            debugInfo.append("   🎭 角色: ${profile.persona}\n")
            debugInfo.append("   🎵 语调: ${profile.tone}\n")
            debugInfo.append("   📄 格式: ${profile.outputFormat}\n")
            
            if (!profile.expertise.isNullOrEmpty()) {
                debugInfo.append("   🎯 专业领域: ${profile.expertise}\n")
            }
            if (profile.creativity != null) {
                debugInfo.append("   🎨 创造力: ${profile.creativity}/10\n")
            }
            if (!profile.description.isNullOrEmpty()) {
                debugInfo.append("   📖 描述: ${profile.description}\n")
            }
            if (!profile.customInstructions.isNullOrEmpty()) {
                debugInfo.append("   ⚙️ 自定义指令: ${profile.customInstructions.take(50)}${if (profile.customInstructions.length > 50) "..." else ""}\n")
            }
            
            debugInfo.append("   🆔 ID: ${profile.id}\n")
            debugInfo.append("\n")
        }
        
        // 检查当前激活的配置
        val activeId = settingsManager.getActivePromptProfileId()
        val activeProfile = profiles.find { it.id == activeId }
        if (activeProfile != null) {
            debugInfo.append("✨ 当前激活配置: ${activeProfile.name}\n\n")
            Log.d("AI_CONFIG_DEBUG", "当前激活配置: ${activeProfile.name} (ID: ${activeProfile.id})")
        }
        
        // 显示用户友好的恢复对话框
        showDataRecoveryDialog("发现历史配置数据", debugInfo.toString())
    }
    
    // 检查个别的prompt设置字段
    private fun checkIndividualPromptSettings() {
        Log.d("AI_CONFIG_DEBUG", "--- 检查个别Prompt设置字段 ---")
        
        val individualSettings = mapOf(
            "性别设定" to settingsManager.getPromptGender(),
            "出生日期" to settingsManager.getPromptBirthDate(),
            "职业信息" to settingsManager.getPromptOccupation(),
            "教育背景" to settingsManager.getPromptEducation(),
            "语调风格" to settingsManager.getPromptToneStyle(),
            "当前职业" to settingsManager.getPromptOccupationCurrent().joinToString(", "),
            "兴趣职业" to settingsManager.getPromptOccupationInterest().joinToString(", "),
            "娱乐兴趣" to settingsManager.getPromptInterestsEntertainment().joinToString(", "),
            "购物偏好" to settingsManager.getPromptInterestsShopping().joinToString(", "),
            "小众爱好" to settingsManager.getPromptInterestsNiche().joinToString(", "),
            "价值观念" to settingsManager.getPromptInterestsValues().joinToString(", "),
            "健康饮食" to settingsManager.getPromptHealthDiet().joinToString(", "),
            "慢性疾病" to settingsManager.getPromptHealthChronic().joinToString(", "),
            "体质特征" to settingsManager.getPromptHealthConstitution().joinToString(", ")
        )
        
        var foundIndividualData = false
        individualSettings.forEach { (key, value) ->
            if (value.isNotEmpty() && value != "unspecified" && value != "decline_to_state") {
                Log.d("AI_CONFIG_DEBUG", "$key: $value")
                foundIndividualData = true
            }
        }
        
        if (foundIndividualData) {
            Log.d("AI_CONFIG_DEBUG", "发现了个别字段的历史设置数据")
        } else {
            Log.d("AI_CONFIG_DEBUG", "未发现任何个别字段的历史设置数据")
        }
    }
    
    private fun showDataRecoveryDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("📊 $title")
            .setMessage(message)
            .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("导出数据") { _, _ -> 
                // 将数据写入日志以便查看
                Log.i("AI_CONFIG_EXPORT", "用户请求导出配置数据")
                Toast.makeText(this, "配置数据已导出到应用日志", Toast.LENGTH_SHORT).show()
            }
            .create()
            
        dialog.show()
    }

    // 显示配置详情对话框
    private fun showConfigDetailDialog(allProfiles: List<PromptProfile>) {
        val configText = StringBuilder()
        configText.append("您的AI指令中心历史配置数据:\n\n")
        
        allProfiles.forEachIndexed { index, profile ->
            configText.append("📋 配置 ${index + 1}: ${profile.name}\n")
            configText.append("🎭 角色人设: ${profile.persona}\n")
            configText.append("🎵 语调风格: ${profile.tone}\n")
            configText.append("📝 输出格式: ${profile.outputFormat}\n")
            if (!profile.customInstructions.isNullOrEmpty()) {
                configText.append("⚙️ 自定义指令: ${profile.customInstructions}\n")
            }
            try {
                if (profile.expertise.isNotEmpty()) {
                    configText.append("🎯 专业领域: ${profile.expertise}\n")
                }
                if (profile.description.isNotEmpty()) {
                    configText.append("📖 详细描述: ${profile.description}\n")
                }
                if (profile.tags.isNotEmpty()) {
                    configText.append("🏷️ 标签: ${profile.tags.joinToString(", ")}\n")
                }
                configText.append("🎨 创造性: ${profile.creativity}/10\n")
                configText.append("👔 正式程度: ${profile.formality}\n")
                configText.append("📏 回复长度: ${profile.responseLength}\n")
                configText.append("${profile.icon} 图标\n")
            } catch (e: Exception) {
                configText.append("(使用基础配置格式)\n")
            }
            configText.append("\n")
        }
        
        // 获取当前激活的配置
        val activeProfileId = settingsManager.getActivePromptProfileId()
        val activeProfile = allProfiles.find { it.id == activeProfileId }
        if (activeProfile != null) {
            configText.append("✅ 当前激活: ${activeProfile.name}\n")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("AI指令中心历史配置追溯")
            .setMessage(configText.toString())
            .setPositiveButton("确定", null)
            .setNeutralButton("导出到日志") { _, _ ->
                Log.i("AI_CONFIG_EXPORT", configText.toString())
                Toast.makeText(this, "配置数据已导出到应用日志", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_master_prompt_settings)
    }

    private fun setupViews() {
        profilesRecyclerView = findViewById(R.id.profiles_recycler_view)
        editProfileName = findViewById(R.id.edit_profile_name)
        editPersona = findViewById(R.id.edit_persona)
        editTone = findViewById(R.id.edit_tone)
        editOutputFormat = findViewById(R.id.edit_output_format)
        editCustomInstructions = findViewById(R.id.edit_custom_instructions)
        
        // 注意：扩展UI字段在当前布局中可能不存在，暂时注释掉
        // 主要功能是数据追溯，UI扩展可以后续添加
        // setupSpinners()
        // setupSeekBars()
    }

    private fun setupRecyclerView() {
        profileAdapter = ProfileAdapter(profiles, ::onProfileSelected)
        profilesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        profilesRecyclerView.adapter = profileAdapter
    }
    
    // 注意：这些方法暂时注释掉，因为对应的UI元素不存在
    // 当添加完整UI布局后可以取消注释
    /*
    private fun setupSpinners() {
        // 设置正式程度下拉框
        val formalityAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("非正式", "适中", "正式")
        )
        formalityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        editFormality.adapter = formalityAdapter
        
        // 设置回复长度下拉框
        val lengthAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("简短", "适中", "详细")
        )
        lengthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        editResponseLength.adapter = lengthAdapter
        
        // 设置代码风格下拉框
        val codeStyleAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("简洁", "清晰", "详细")
        )
        codeStyleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        editCodeStyle.adapter = codeStyleAdapter
    }
    
    private fun setupSeekBars() {
        // 创造性水平 (1-10)
        editCreativity.max = 10
        editCreativity.min = 1
        
        // 温度参数 (0.0-2.0, 精度0.1)
        editTemperature.max = 20
        editTemperature.min = 0
        
        // Top-p参数 (0.0-1.0, 精度0.1)
        editTopP.max = 10
        editTopP.min = 0
    }
    */

    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(settingsManager.getAllPromptProfiles())
        val activeProfileId = settingsManager.getActivePromptProfileId()
        
        activeProfile = profiles.find { it.id == activeProfileId }
        if (activeProfile == null) {
            activeProfile = profiles.firstOrNull()
            activeProfile?.let { settingsManager.setActivePromptProfileId(it.id) }
        }

        // 通知适配器数据已更新
        if (::profileAdapter.isInitialized) {
            profileAdapter.notifyDataSetChanged()
        }

        updateUIForActiveProfile()
    }

    private fun onProfileSelected(selectedProfile: PromptProfile) {
        if (selectedProfile.id == activeProfile?.id) return

        // 1. Save current UI state to the old active profile object
        saveCurrentUIToActiveProfile()

        // 2. Switch to the new profile
        activeProfile = selectedProfile
        settingsManager.setActivePromptProfileId(selectedProfile.id)

        // 3. Update UI with the new active profile's data
        updateUIForActiveProfile()
    }

    private fun saveCurrentUIToActiveProfile() {
        activeProfile?.let { profile ->
            val updatedProfile = profile.copy(
                name = editProfileName.text.toString(),
                persona = editPersona.text.toString(),
                tone = editTone.text.toString(),
                outputFormat = editOutputFormat.text.toString(),
                customInstructions = editCustomInstructions.text.toString().takeIf { it.isNotBlank() }
                // 注意：扩展字段暂时保持原值，因为UI元素不存在
                // 当添加完整UI布局后可以取消注释以下字段
                // expertise = editExpertise.text.toString(),
                // language = editLanguage.text.toString(),
                // creativity = editCreativity.progress,
                // formality = editFormality.selectedItem.toString(),
                // responseLength = editResponseLength.selectedItem.toString(),
                // reasoning = switchReasoning.isChecked,
                // examples = switchExamples.isChecked,
                // codeStyle = editCodeStyle.selectedItem.toString(),
                // temperature = editTemperature.progress / 10.0f,
                // topP = editTopP.progress / 10.0f,
                // maxTokens = editMaxTokens.text.toString().toIntOrNull() ?: 2048,
                // description = editDescription.text.toString(),
                // icon = editIcon.text.toString()
            )
            // Update the profile in our in-memory list
            val index = profiles.indexOfFirst { it.id == profile.id }
            if (index != -1) {
                profiles[index] = updatedProfile
            }
        }
    }

    private fun updateUIForActiveProfile() {
        activeProfile?.let { profile ->
            editProfileName.setText(profile.name)
            editPersona.setText(profile.persona)
            editTone.setText(profile.tone)
            editOutputFormat.setText(profile.outputFormat)
            editCustomInstructions.setText(profile.customInstructions ?: "")
            
            // 注意：扩展字段的UI更新暂时注释掉，因为UI元素不存在
            // 当添加完整UI布局后可以取消注释以下代码
            // editExpertise.setText(profile.expertise)
            // editLanguage.setText(profile.language)
            // editCreativity.progress = profile.creativity
            // 
            // val formalityOptions = arrayOf("非正式", "适中", "正式")
            // editFormality.setSelection(formalityOptions.indexOf(profile.formality).coerceAtLeast(0))
            // 
            // val lengthOptions = arrayOf("简短", "适中", "详细")
            // editResponseLength.setSelection(lengthOptions.indexOf(profile.responseLength).coerceAtLeast(0))
            // 
            // val codeStyleOptions = arrayOf("简洁", "清晰", "详细")
            // editCodeStyle.setSelection(codeStyleOptions.indexOf(profile.codeStyle).coerceAtLeast(0))
            // 
            // switchReasoning.isChecked = profile.reasoning
            // switchExamples.isChecked = profile.examples
            // 
            // editTemperature.progress = (profile.temperature * 10).toInt()
            // editTopP.progress = (profile.topP * 10).toInt()
            // 
            // editMaxTokens.setText(profile.maxTokens.toString())
            // editDescription.setText(profile.description)
            // editIcon.setText(profile.icon)
        }
        
        if (::profileAdapter.isInitialized) {
            profileAdapter.setActiveProfileId(activeProfile?.id)
        }

        // Scroll to the active profile
        val activeIndex = profiles.indexOfFirst { it.id == activeProfile?.id }
        if (activeIndex != -1) {
            profilesRecyclerView.smoothScrollToPosition(activeIndex)
        }
    }

    private fun saveAllChanges() {
        saveCurrentUIToActiveProfile() // Save any pending changes in the UI
        settingsManager.saveAllPromptProfiles(profiles)
        Toast.makeText(this, "所有修改已保存", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        saveAllChanges()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.master_prompt_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_add_profile -> {
                showAddProfileDialog()
                true
            }
            R.id.action_delete_profile -> {
                activeProfile?.let { handleDeleteProfile(it) }
                true
            }
            R.id.action_save_prompt -> {
                saveAllChanges()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddProfileDialog() {
        val templates = arrayOf(
            "空白画像" to PromptProfile.DEFAULT,
            "编程专家" to PromptProfile.PROGRAMMING_EXPERT,
            "写作助手" to PromptProfile.WRITING_ASSISTANT,
            "商业顾问" to PromptProfile.BUSINESS_CONSULTANT
        )
        
        val templateNames = templates.map { it.first }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("选择画像模板")
            .setItems(templateNames) { _, which ->
                val selectedTemplate = templates[which].second
                val newProfile = selectedTemplate.copy(
                    id = UUID.randomUUID().toString()
                    )
                    profiles.add(newProfile)
                    profileAdapter.notifyItemInserted(profiles.size - 1)
                    onProfileSelected(newProfile) // Switch to the new profile
                Toast.makeText(this, "已创建新画像：${newProfile.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun handleDeleteProfile(profileToDelete: PromptProfile) {
        if (profileToDelete.id == "default") {
            Toast.makeText(this, "不能删除默认画像", Toast.LENGTH_SHORT).show()
            return
        }
        if (profiles.size <= 1) {
            Toast.makeText(this, "至少保留一个画像", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("删除画像")
            .setMessage("确定要删除 “${profileToDelete.name}” 吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val deleteIndex = profiles.indexOf(profileToDelete)
                profiles.removeAt(deleteIndex)
                profileAdapter.notifyItemRemoved(deleteIndex)
                
                // Select the first profile as the new active one
                onProfileSelected(profiles.first())
            }
            .show()
    }
} 