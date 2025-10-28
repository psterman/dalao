package com.example.aifloatingball.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.ProfileAdapter
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.ui.settings.CoreInstructionsFragment
import com.example.aifloatingball.ui.settings.ExtendedConfigFragment
import com.example.aifloatingball.ui.settings.AiParamsFragment
import com.example.aifloatingball.ui.settings.PersonalizationFragment
import com.example.aifloatingball.ui.settings.SectionsPagerAdapter
import com.example.aifloatingball.viewmodel.SettingsViewModel
import com.example.aifloatingball.SettingsManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.UUID
import android.os.Handler
import android.os.Looper

/**
 * AI指令中心Fragment - 集成到简易模式AI助手中心
 */
class MasterPromptFragment : Fragment() {
    
    private lateinit var profilesRecyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: SectionsPagerAdapter
    private lateinit var addProfileButton: MaterialButton
    private lateinit var saveButton: ExtendedFloatingActionButton
    private lateinit var settingsManager: SettingsManager
    private val viewModel: com.example.aifloatingball.viewmodel.SettingsViewModel by activityViewModels()
    
    private var profiles: MutableList<PromptProfile> = mutableListOf()
    private var activeProfile: PromptProfile? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_master_prompt, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            setupViews(view)
            setupRecyclerView()
            setupProfileAdapter()  // 先创建ProfileAdapter
            setupViewPager()
            setupButtons()
            
            // 注册档案变更监听器
            settingsManager.registerOnSettingChangeListener<List<PromptProfile>>("prompt_profiles") { key: String, value: List<PromptProfile>? ->
                android.util.Log.d("MasterPromptFragment", "档案列表已更新，重新加载档案")
                // 在主线程中刷新UI
                requireActivity().runOnUiThread {
                    loadProfiles()
                }
            }
            
            settingsManager.registerOnSettingChangeListener<String>("active_prompt_profile_id") { key: String, value: String? ->
                android.util.Log.d("MasterPromptFragment", "活跃档案ID已更新")
                // 在主线程中刷新UI
                requireActivity().runOnUiThread {
                    loadProfiles()
                }
            }
            
            loadProfiles()
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "初始化失败", e)
            Toast.makeText(requireContext(), "加载AI指令中心失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupViews(view: View) {
        profilesRecyclerView = view.findViewById(R.id.profiles_recycler_view)
        viewPager = view.findViewById(R.id.view_pager)
        tabLayout = view.findViewById(R.id.tabs)
        addProfileButton = view.findViewById(R.id.btn_add_profile)
        saveButton = view.findViewById(R.id.fab_save)
        
        settingsManager = SettingsManager.getInstance(requireContext())
    }
    
    private fun setupRecyclerView() {
        // 设置横向布局
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        profilesRecyclerView.layoutManager = layoutManager
        
        // 禁用嵌套滚动，避免与外部ScrollView冲突
        profilesRecyclerView.isNestedScrollingEnabled = false
        
        android.util.Log.d("MasterPromptFragment", "RecyclerView已初始化，布局管理器：HORIZONTAL")
    }
    
    private fun setupProfileAdapter() {
        android.util.Log.d("MasterPromptFragment", "setupProfileAdapter: 当前profiles大小=${profiles.size}")
        profileAdapter = ProfileAdapter(
            mutableListOf(), 
            onProfileClicked = { profile ->
                selectProfile(profile)
            },
            onProfileLongClicked = { profile, position ->
                showProfileActions(profile, position)
            }
        )
        profilesRecyclerView.adapter = profileAdapter
        android.util.Log.d("MasterPromptFragment", "ProfileAdapter已创建并绑定到RecyclerView")
    }
    
    /**
     * 显示档案操作菜单（删除、编辑等）
     */
    private fun showProfileActions(profile: PromptProfile, position: Int) {
        try {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("档案操作")
                .setItems(arrayOf("删除档案", "编辑档案")) { _, which ->
                    when (which) {
                        0 -> deleteProfile(profile, position)
                        1 -> editProfile(profile)
                    }
                }
                .show()
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "显示档案操作菜单失败", e)
        }
    }
    
    /**
     * 删除档案
     */
    private fun deleteProfile(profile: PromptProfile, position: Int) {
        try {
            // 如果是默认档案，不允许删除
            if (profile == PromptProfile.DEFAULT) {
                Toast.makeText(requireContext(), "默认档案不能删除", Toast.LENGTH_SHORT).show()
                return
            }
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除档案「${profile.name}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    try {
                        // 从列表中移除
                        profiles.removeAt(position)
                        
                        // 如果删除的是当前活跃档案，切换到第一个
                        if (activeProfile?.id == profile.id) {
                            val newActiveProfile = profiles.firstOrNull()
                            if (newActiveProfile != null) {
                                settingsManager.setActivePromptProfileId(newActiveProfile.id)
                                selectProfile(newActiveProfile)
                            }
                        }
                        
                        // 更新适配器
                        profileAdapter.updateData(profiles)
                        
                        Toast.makeText(requireContext(), "档案已删除", Toast.LENGTH_SHORT).show()
                        android.util.Log.d("MasterPromptFragment", "档案已删除: ${profile.name}")
                    } catch (e: Exception) {
                        android.util.Log.e("MasterPromptFragment", "删除档案失败", e)
                        Toast.makeText(requireContext(), "删除档案失败", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "删除档案对话框失败", e)
        }
    }
    
    /**
     * 编辑档案
     */
    private fun editProfile(profile: PromptProfile) {
        try {
            val input = android.widget.EditText(requireContext()).apply {
                setText(profile.name)
                setPadding(32, 16, 32, 16)
            }
            
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("重命名档案")
                .setMessage("请输入新名称")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != profile.name) {
                        val updatedProfile = profile.copy(name = newName)
                        
                        // 更新列表中的档案
                        val index = profiles.indexOfFirst { it.id == profile.id }
                        if (index != -1) {
                            profiles[index] = updatedProfile
                            settingsManager.savePromptProfile(updatedProfile)
                            profileAdapter.updateData(profiles)
                            Toast.makeText(requireContext(), "档案已重命名", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "编辑档案失败", e)
        }
    }
    
    private fun setupViewPager() {
        pagerAdapter = SectionsPagerAdapter(requireActivity())
        viewPager.adapter = pagerAdapter
        
        // 启用嵌套滚动并设置用户输入处理
        viewPager.isNestedScrollingEnabled = true
        viewPager.isUserInputEnabled = true
        
        // 设置ViewPager2的嵌套滚动行为
        viewPager.offscreenPageLimit = 1
        
        // 添加滚动监听器来处理滚动事件
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 确保当前页面的滚动正常工作
                val currentFragment = pagerAdapter.createFragment(position)
                if (currentFragment is androidx.fragment.app.Fragment) {
                    // 延迟执行以确保Fragment完全加载
                    viewPager.post {
                        enableScrollingForCurrentFragment()
                    }
                }
            }
        })
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "核心指令"
                1 -> "扩展配置"
                2 -> "AI参数"
                3 -> "个性化"
                else -> "未知"
            }
        }.attach()
    }
    
    private fun enableScrollingForCurrentFragment() {
        // 查找当前Fragment中的ScrollView并确保滚动正常
        val currentFragment = childFragmentManager.fragments.find { it.isVisible }
        currentFragment?.view?.let { fragmentView ->
            val scrollView = fragmentView.findViewById<android.widget.ScrollView>(android.R.id.content)
            scrollView?.let {
                it.isNestedScrollingEnabled = true
                it.isScrollContainer = true
            }
        }
    }
    
    private fun setupButtons() {
        // 新建档案按钮
        addProfileButton.setOnClickListener {
            createNewProfile()
        }
        
        // 保存档案按钮
        saveButton.setOnClickListener {
            saveCurrentProfile()
        }
    }
    
    private fun loadProfiles() {
        try {
            android.util.Log.d("MasterPromptFragment", "开始加载档案")
            
            // 从SettingsManager加载已保存的档案
            profiles.clear()
            profiles.addAll(settingsManager.getPromptProfiles())
            
            android.util.Log.d("MasterPromptFragment", "已从设置管理器加载了${profiles.size}个档案")
            
            if (profiles.isEmpty()) {
                android.util.Log.d("MasterPromptFragment", "没有档案，创建默认档案")
                val defaultProfile = PromptProfile.DEFAULT
                settingsManager.savePromptProfile(defaultProfile)
                settingsManager.setActivePromptProfileId(defaultProfile.id)
                profiles.add(defaultProfile)
                android.util.Log.d("MasterPromptFragment", "创建了默认档案: ${defaultProfile.name}")
            }
            
            android.util.Log.d("MasterPromptFragment", "准备更新适配器，档案数量: ${profiles.size}")
            
            // 更新适配器数据
            if (::profileAdapter.isInitialized) {
                android.util.Log.d("MasterPromptFragment", "调用profileAdapter.updateData")
                profileAdapter.updateData(profiles)
                android.util.Log.d("MasterPromptFragment", "适配器数据已更新，ItemCount=${profileAdapter.itemCount}")
                
                // 设置当前活跃档案
                val activeProfileId = settingsManager.getActivePromptProfileId()
                val currentProfile = profiles.find { it.id == activeProfileId } ?: profiles.first()
                
                android.util.Log.d("MasterPromptFragment", "设置活跃档案: ${currentProfile.name} (ID: ${currentProfile.id})")
                
                // 更新适配器中的活跃档案ID并刷新
                profileAdapter.setActiveProfileId(currentProfile.id)
                
                // 选择当前档案
                activeProfile = currentProfile
                
                // 加载档案数据到Fragment
                loadProfileData(currentProfile)
                
                android.util.Log.d("MasterPromptFragment", "加载档案完成，共${profiles.size}个档案，当前档案：${currentProfile.name}")
            } else {
                android.util.Log.e("MasterPromptFragment", "ProfileAdapter未初始化！")
            }
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "加载档案失败", e)
            Toast.makeText(requireContext(), "加载档案失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun selectProfile(profile: PromptProfile) {
        try {
            // 设置选中的档案
            activeProfile = profile
            
            // 设置为活跃档案
            settingsManager.setActivePromptProfileId(profile.id)
            
            // 更新适配器中的活跃档案ID
            profileAdapter.setActiveProfileId(profile.id)
            
            android.util.Log.d("MasterPromptFragment", "已选择档案: ${profile.name}")
            
            // 加载档案的配置数据到ViewPager
            loadProfileData(profile)
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "选择档案失败", e)
            Toast.makeText(requireContext(), "选择档案失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadProfileData(profile: PromptProfile) {
        try {
            android.util.Log.d("MasterPromptFragment", "开始加载档案数据到Fragment: ${profile.name}")
            
            // 通过ViewModel设置档案数据，这会触发所有Fragment更新
            viewModel.selectProfile(profile)
            
            android.util.Log.d("MasterPromptFragment", "档案数据已加载到各Fragment")
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "加载档案数据失败", e)
        }
    }
    
    private fun createNewProfile() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "档案名称"
            setPadding(32, 16, 32, 16)
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建档案")
            .setMessage("请输入档案名称")
            .setView(input)
            .setPositiveButton("创建") { dialog, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    try {
                        val newProfile = PromptProfile(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            persona = "一个乐于助人的AI助手",
                            tone = "友好、清晰、简洁",
                            outputFormat = "使用Markdown格式进行回复",
                            description = "新建的AI助手档案"
                        )
                        
                        android.util.Log.d("MasterPromptFragment", "准备保存新档案: $name")
                        
                        // 使用SettingsManager保存档案，确保触发通知机制
                        settingsManager.savePromptProfile(newProfile)
                        
                        // 设置为当前活跃档案
                        settingsManager.setActivePromptProfileId(newProfile.id)
                        
                        android.util.Log.d("MasterPromptFragment", "新档案已保存，准备刷新列表")
                        
                        // 立即添加到列表并刷新适配器
                        profiles.add(newProfile)
                        activeProfile = newProfile
                        
                        if (::profileAdapter.isInitialized) {
                            profileAdapter.updateData(profiles)
                            profileAdapter.setActiveProfileId(newProfile.id)
                            android.util.Log.d("MasterPromptFragment", "档案列表已更新，当前数量: ${profiles.size}")
                        }
                        
                        // 滚动到新档案的位置
                        Handler(Looper.getMainLooper()).postDelayed({
                            val index = profiles.indexOfFirst { it.id == newProfile.id }
                            if (index != -1) {
                                profilesRecyclerView.smoothScrollToPosition(index)
                            }
                        }, 300)
                        
                        Toast.makeText(requireContext(), "档案创建成功", Toast.LENGTH_SHORT).show()
                        
                        android.util.Log.d("MasterPromptFragment", "新档案创建成功: $name")
                        
                    } catch (e: Exception) {
                        android.util.Log.e("MasterPromptFragment", "创建档案失败", e)
                        Toast.makeText(requireContext(), "创建档案失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "请输入档案名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
    }
    
    private fun saveCurrentProfile() {
        if (activeProfile != null) {
            try {
                android.util.Log.d("MasterPromptFragment", "开始保存当前档案: ${activeProfile!!.name}")
                
                // 保存当前档案的配置
                saveProfileData(activeProfile!!)
                
                Toast.makeText(requireContext(), "档案「${activeProfile!!.name}」保存成功", Toast.LENGTH_SHORT).show()
                android.util.Log.d("MasterPromptFragment", "档案保存完成")
            } catch (e: Exception) {
                android.util.Log.e("MasterPromptFragment", "保存档案失败", e)
                Toast.makeText(requireContext(), "保存档案失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "请先选择一个档案", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveProfileData(profile: PromptProfile) {
        try {
            var updatedProfile = profile
            
            android.util.Log.d("MasterPromptFragment", "开始收集档案数据")
            
            // 从childFragmentManager获取所有已附加的Fragment并收集数据
            childFragmentManager.fragments.forEach { fragment ->
                when (fragment) {
                    is com.example.aifloatingball.ui.settings.CoreInstructionsFragment -> {
                        updatedProfile = fragment.collectProfileData(updatedProfile)
                        android.util.Log.d("MasterPromptFragment", "已收集核心指令数据")
                    }
                    is com.example.aifloatingball.ui.settings.ExtendedConfigFragment -> {
                        updatedProfile = fragment.collectProfileData(updatedProfile)
                        android.util.Log.d("MasterPromptFragment", "已收集扩展配置数据")
                    }
                    is com.example.aifloatingball.ui.settings.AiParamsFragment -> {
                        updatedProfile = fragment.collectProfileData(updatedProfile)
                        android.util.Log.d("MasterPromptFragment", "已收集AI参数数据")
                    }
                    is com.example.aifloatingball.ui.settings.PersonalizationFragment -> {
                        updatedProfile = fragment.collectProfileData(updatedProfile)
                        android.util.Log.d("MasterPromptFragment", "已收集个性化数据")
                    }
                }
            }
            
            android.util.Log.d("MasterPromptFragment", "收集数据完成，准备保存档案: ${updatedProfile.name}")
            
            // 先保存到SettingsManager
            settingsManager.savePromptProfile(updatedProfile)
            android.util.Log.d("MasterPromptFragment", "已保存到SettingsManager")
            
            // 更新列表中的档案
            val index = profiles.indexOfFirst { it.id == updatedProfile.id }
            if (index != -1) {
                profiles[index] = updatedProfile
                android.util.Log.d("MasterPromptFragment", "更新现有档案，索引: $index")
            } else {
                profiles.add(updatedProfile)
                android.util.Log.d("MasterPromptFragment", "添加新档案到列表")
            }
            
            activeProfile = updatedProfile
            
            // 刷新适配器
            profileAdapter.updateData(profiles)
            profileAdapter.setActiveProfileId(updatedProfile.id)
            
            android.util.Log.d("MasterPromptFragment", "档案数据已保存并刷新列表: ${updatedProfile.name}")
        } catch (e: Exception) {
            android.util.Log.e("MasterPromptFragment", "保存档案数据失败", e)
            Toast.makeText(requireContext(), "保存档案数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
