package com.example.aifloatingball

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adapter.ProfileAdapter
import com.example.aifloatingball.adapter.ProfileListAdapter
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.ui.settings.CoreInstructionsFragment
import com.example.aifloatingball.ui.settings.ExtendedConfigFragment
import com.example.aifloatingball.ui.settings.AiParamsFragment
import com.example.aifloatingball.ui.settings.PersonalizationFragment
import com.example.aifloatingball.ui.settings.SectionsPagerAdapter
import com.example.aifloatingball.viewmodel.SettingsViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.UUID

class MasterPromptSettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var settingsManager: SettingsManager
    private lateinit var profilesRecyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var profileListAdapter: ProfileListAdapter
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: SectionsPagerAdapter

    // 档案管理相关组件
    private lateinit var currentProfileName: TextView
    private lateinit var selectProfileButton: MaterialButton
    private lateinit var newProfileButton: MaterialButton
    private lateinit var manageProfilesButton: MaterialButton
    private lateinit var emptyProfilesText: TextView

    private var profiles: MutableList<PromptProfile> = mutableListOf()
    private var activeProfile: PromptProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            android.util.Log.d("MasterPromptSettings", "开始创建主提示词设置Activity")

            setContentView(R.layout.activity_master_prompt_settings)
            android.util.Log.d("MasterPromptSettings", "成功设置内容视图")

            settingsManager = SettingsManager.getInstance(this)
            android.util.Log.d("MasterPromptSettings", "成功初始化SettingsManager")
            
            // 注册档案变更监听器
            settingsManager.registerOnSettingChangeListener<List<PromptProfile>>("prompt_profiles") { key, value ->
                android.util.Log.d("MasterPromptSettings", "档案列表已更新，重新加载档案")
                // 在主线程中刷新UI
                runOnUiThread {
                    loadProfiles()
                }
            }

            setupToolbar()
            android.util.Log.d("MasterPromptSettings", "成功设置工具栏")

            setupViews()
            android.util.Log.d("MasterPromptSettings", "成功设置视图")

            setupRecyclerView()
            android.util.Log.d("MasterPromptSettings", "成功设置RecyclerView")

            setupViewPager()
            android.util.Log.d("MasterPromptSettings", "成功设置ViewPager")

            android.util.Log.d("MasterPromptSettings", "主提示词设置Activity创建完成")
            
            // 在try块内加载档案，确保所有组件都已初始化
            loadProfiles()
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptSettings", "创建主提示词设置Activity失败", e)
            Toast.makeText(this, "加载主提示词设置失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        // 当从其他页面返回时，重新加载档案列表以确保数据同步
        android.util.Log.d("MasterPromptSettings", "onResume: 重新加载档案列表")
        loadProfiles()
    }

    private fun setupViews() {
        profilesRecyclerView = findViewById(R.id.profiles_recycler_view)
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tabs)

        // 档案管理相关组件
        currentProfileName = findViewById(R.id.current_profile_name)
        selectProfileButton = findViewById(R.id.select_profile_button)
        newProfileButton = findViewById(R.id.new_profile_button)
        manageProfilesButton = findViewById(R.id.manage_profiles_button)
        emptyProfilesText = findViewById(R.id.empty_profiles_text)
    }

    private fun setupButtons() {
        // 档案管理按钮
        selectProfileButton.setOnClickListener {
            showProfileSelector()
        }

        newProfileButton.setOnClickListener {
            showNewProfileDialog()
        }

        manageProfilesButton.setOnClickListener {
            // 当前页面就是档案管理页面，显示提示
            Toast.makeText(this, "您已在档案管理页面", Toast.LENGTH_SHORT).show()
        }

        // 保存档案按钮
        val saveButton = findViewById<ExtendedFloatingActionButton>(R.id.fab_save)
        saveButton?.setOnClickListener {
            saveCurrentProfile()
        }
    }

    private fun setupViewPager() {
        pagerAdapter = SectionsPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_basic_info)
                1 -> getString(R.string.tab_extended_config)
                2 -> getString(R.string.tab_ai_behavior)
                3 -> getString(R.string.tab_personalization)
                else -> null
            }
            // 添加图标
            tab.icon = when (position) {
                0 -> getDrawable(R.drawable.ic_edit)
                1 -> getDrawable(R.drawable.ic_settings)
                2 -> getDrawable(R.drawable.ic_brain)
                3 -> getDrawable(R.drawable.ic_person)
                else -> null
            }
        }.attach()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        profilesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // 设置档案列表
        setupProfileList()
    }

    private fun setupProfileList() {
        try {
            android.util.Log.d("MasterPromptSettings", "setupProfileList called")
            // 设置RecyclerView布局管理器
            val layoutManager = LinearLayoutManager(
                this,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            profilesRecyclerView.layoutManager = layoutManager

            // 加载档案列表
            loadProfileList()

            android.util.Log.d("MasterPromptSettings", "档案列表设置完成")
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptSettings", "设置档案列表失败", e)
        }
    }

    private fun loadProfileList() {
        try {
            android.util.Log.d("MasterPromptSettings", "loadProfileList called")
            val profiles = settingsManager.getPromptProfiles()
            val currentProfileId = settingsManager.getActivePromptProfileId()

            android.util.Log.d("MasterPromptSettings", "Loaded profiles count: ${profiles.size}")
            android.util.Log.d("MasterPromptSettings", "Current active profile ID: $currentProfileId")

            // 打印所有档案信息
            profiles.forEachIndexed { index, profile ->
                android.util.Log.d("MasterPromptSettings", "Profile $index: ${profile.name} (ID: ${profile.id})")
            }

            // 如果没有档案，确保至少有一个默认档案
            val finalProfiles = if (profiles.isEmpty()) {
                android.util.Log.d("MasterPromptSettings", "No profiles found, creating default profile")
                val defaultProfile = PromptProfile.DEFAULT
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
                android.util.Log.d("MasterPromptSettings", "Still no profiles after creating default, showing empty text")
            } else {
                // 显示档案列表
                profilesRecyclerView.visibility = android.view.View.VISIBLE
                emptyProfilesText.visibility = android.view.View.GONE

                // 使用ProfileListAdapter，与布局文件item_prompt_profile匹配
                profileListAdapter = ProfileListAdapter(
                    this,
                    finalProfiles,
                    currentProfileId
                ) { selectedProfile ->
                    // 档案选择回调
                    android.util.Log.d("MasterPromptSettings", "Profile selected: ${selectedProfile.name}")
                    settingsManager.setActivePromptProfileId(selectedProfile.id)
                    loadCurrentProfile()
                    // 更新适配器数据
                    profileListAdapter.updateProfiles(finalProfiles, selectedProfile.id)
                    Toast.makeText(
                        this,
                        "已切换到档案: ${selectedProfile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                profilesRecyclerView.adapter = profileListAdapter
                android.util.Log.d("MasterPromptSettings", "ProfileListAdapter set with ${finalProfiles.size} items")
            }
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptSettings", "加载档案列表失败", e)
            // 显示错误状态
            profilesRecyclerView.visibility = android.view.View.GONE
            emptyProfilesText.visibility = android.view.View.VISIBLE
            emptyProfilesText.text = "加载档案失败: ${e.localizedMessage}"
        }
    }

    private fun loadCurrentProfile() {
        try {
            val profiles = settingsManager.getPromptProfiles()
            val activeProfileId = settingsManager.getActivePromptProfileId()
            val currentProfile = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()

            if (currentProfile != null) {
                currentProfileName.text = currentProfile.name
                android.util.Log.d("MasterPromptSettings", "加载当前档案: ${currentProfile.name}")
            } else {
                currentProfileName.text = "默认画像"
                android.util.Log.w("MasterPromptSettings", "没有找到当前档案")
            }
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptSettings", "加载当前档案失败", e)
        }
    }

    private fun loadProfiles() {
        try {
            android.util.Log.d("MasterPromptSettings", "开始加载档案列表")
            
            profiles.clear()
            val allProfiles = settingsManager.getPromptProfiles()
            android.util.Log.d("MasterPromptSettings", "从SettingsManager获取到${allProfiles.size}个档案")
            
            profiles.addAll(allProfiles)
            if (profiles.isEmpty()) {
                android.util.Log.d("MasterPromptSettings", "没有档案，添加默认档案")
                profiles.add(PromptProfile.DEFAULT)
            }
            
            val activeProfileId = settingsManager.getActivePromptProfileId()
            android.util.Log.d("MasterPromptSettings", "当前活跃档案ID: $activeProfileId")
            
            activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.first()
            android.util.Log.d("MasterPromptSettings", "设置活跃档案: ${activeProfile?.name}")
            
            // 确保RecyclerView和Adapter已初始化
            if (::profileAdapter.isInitialized) {
                profileAdapter.updateData(profiles)
                android.util.Log.d("MasterPromptSettings", "档案适配器已更新，共${profiles.size}个档案")
            } else {
                android.util.Log.w("MasterPromptSettings", "档案适配器未初始化")
            }

            if (activeProfile != null) {
                selectProfile(activeProfile!!)
            }

            // 加载当前档案信息和档案列表
            loadCurrentProfile()
            loadProfileList()

            android.util.Log.d("MasterPromptSettings", "档案列表加载完成")
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptSettings", "加载档案列表失败", e)
            Toast.makeText(this, "加载档案列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectProfile(profile: PromptProfile) {
        activeProfile = profile
        viewModel.selectProfile(profile) // Update ViewModel
        profileAdapter.setActiveProfileId(profile.id)
        settingsManager.setActivePromptProfileId(profile.id)
    }

    private fun createNewProfile() {
        val newProfile = PromptProfile(
            id = "profile_${System.currentTimeMillis()}",
            name = "新档案 ${profiles.size + 1}",
            persona = "",
            tone = "友好",
            outputFormat = "详细",
            customInstructions = ""
        )

        profiles.add(newProfile)
        profileAdapter.updateData(profiles)
        selectProfile(newProfile)
        
        // 使用savePromptProfile方法确保触发通知机制
        settingsManager.savePromptProfile(newProfile)

        Toast.makeText(this, "已创建新档案", Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentProfile() {
        if (activeProfile == null) {
            Toast.makeText(this, "没有活动的配置可保存", Toast.LENGTH_SHORT).show()
            return
        }

        var updatedProfile = activeProfile!!

        // Collect data from fragments
        supportFragmentManager.fragments.forEach { fragment ->
            when (fragment) {
                is CoreInstructionsFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
                is ExtendedConfigFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
                is AiParamsFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
                is PersonalizationFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
            }
        }

        val index = profiles.indexOfFirst { it.id == updatedProfile.id }
        if (index != -1) {
            profiles[index] = updatedProfile
            profileAdapter.notifyItemChanged(index)
        } else {
            profiles.add(updatedProfile)
            profileAdapter.notifyItemInserted(profiles.size - 1)
        }
        activeProfile = updatedProfile
        
        // 使用savePromptProfile方法确保触发通知机制
        settingsManager.savePromptProfile(updatedProfile)
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_master_prompt_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_add_profile -> {
                addNewProfile()
                true
            }
            // Assuming delete is handled via some other UI interaction now
            // R.id.action_delete_profile -> {
            //     activeProfile?.let { confirmDeleteProfile(it) }
            //     true
            // }
            R.id.action_save -> {
                saveCurrentProfile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addNewProfile() {
        val newProfile = PromptProfile.DEFAULT.copy(
            id = UUID.randomUUID().toString(),
            name = "新配置"
        )
        profiles.add(newProfile)
        profileAdapter.notifyItemInserted(profiles.size - 1)
        selectProfile(newProfile)
        profilesRecyclerView.smoothScrollToPosition(profiles.size - 1)
        
        // 使用savePromptProfile方法确保触发通知机制
        settingsManager.savePromptProfile(newProfile)
    }

    private fun confirmDeleteProfile(profile: PromptProfile) {
        if (profile.id == PromptProfile.DEFAULT.id) {
            Toast.makeText(this, "不能删除默认配置", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("删除配置")
            .setMessage("您确定要删除 '${profile.name}' 吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                deleteProfile(profile)
            }
            .show()
    }

    private fun deleteProfile(profile: PromptProfile) {
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles.removeAt(index)
            
            // 使用savePromptProfiles方法确保触发通知机制
            settingsManager.savePromptProfiles(profiles)
            profileAdapter.notifyItemRemoved(index)

            val newActiveProfile = profiles.firstOrNull() ?: PromptProfile.DEFAULT
            if (profiles.isEmpty()) {
                profiles.add(newActiveProfile)
                settingsManager.savePromptProfile(newActiveProfile)
            }
            selectProfile(newActiveProfile)
        }
    }

    private fun showProfileSelector() {
        try {
            val profiles = settingsManager.getPromptProfiles()
            if (profiles.isEmpty()) {
                Toast.makeText(this, "暂无档案，请先创建档案", Toast.LENGTH_SHORT).show()
                return
            }

            val profileNames = profiles.map { it.name }.toTypedArray()
            val currentProfileId = settingsManager.getActivePromptProfileId()
            val currentIndex = profiles.indexOfFirst { it.id == currentProfileId }.coerceAtLeast(0)

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择档案")
                .setSingleChoiceItems(profileNames, currentIndex) { dialog, which ->
                    val selectedProfile = profiles[which]
                    settingsManager.setActivePromptProfileId(selectedProfile.id)
                    loadCurrentProfile()
                    loadProfileList()
                    Toast.makeText(this, "已切换到档案: ${selectedProfile.name}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()

        } catch (e: Exception) {
            android.util.Log.e("MasterPromptSettings", "显示档案选择器失败", e)
            Toast.makeText(this, "显示档案选择器失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNewProfileDialog() {
        try {
            val input = android.widget.EditText(this).apply {
                hint = "请输入档案名称"
                setPadding(32, 16, 32, 16)
            }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("新建档案")
                .setMessage("请为新档案输入一个名称")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        try {
                            val newProfile = PromptProfile(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                persona = "一个乐于助人的通用AI助手",
                                tone = "友好、清晰、简洁",
                                outputFormat = "使用Markdown格式进行回复"
                            )

                            // 保存新档案
                            settingsManager.savePromptProfile(newProfile)
                            settingsManager.setActivePromptProfileId(newProfile.id)

                            // 刷新界面
                            loadProfiles()

                            Toast.makeText(this, "档案 \"$name\" 创建成功", Toast.LENGTH_SHORT).show()
                            android.util.Log.d("MasterPromptSettings", "新建档案成功: $name")
                        } catch (e: Exception) {
                            android.util.Log.e("MasterPromptSettings", "创建档案失败", e)
                            Toast.makeText(this, "创建档案失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "档案名称不能为空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()

        } catch (e: Exception) {
            android.util.Log.e("MasterPromptSettings", "显示新建档案对话框失败", e)
            Toast.makeText(this, "无法显示新建档案对话框", Toast.LENGTH_SHORT).show()
        }
    }
}