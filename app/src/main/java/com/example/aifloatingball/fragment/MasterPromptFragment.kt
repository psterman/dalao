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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.UUID

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
            setupViewPager()
            setupButtons()
            
            // 注册档案变更监听器
            val settingsManager = SettingsManager.getInstance(requireContext())
            settingsManager.registerOnSettingChangeListener<List<PromptProfile>>("prompt_profiles") { key: String, value: List<PromptProfile>? ->
                android.util.Log.d("MasterPromptFragment", "档案列表已更新，重新加载档案")
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
    }
    
    private fun setupRecyclerView() {
        profileAdapter = ProfileAdapter(profiles) { profile ->
            selectProfile(profile)
        }
        profilesRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        profilesRecyclerView.adapter = profileAdapter
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
        addProfileButton.setOnClickListener {
            createNewProfile()
        }
        
        saveButton.setOnClickListener {
            saveCurrentProfile()
        }
    }
    
    private fun loadProfiles() {
        // 从SettingsManager加载已保存的档案
        val settingsManager = SettingsManager.getInstance(requireContext())
        profiles.clear()
        profiles.addAll(settingsManager.getPromptProfiles())
        if (profiles.isEmpty()) {
            profiles.add(PromptProfile.DEFAULT)
        }
        
        // 更新适配器
        profileAdapter.notifyDataSetChanged()
        
        // 设置当前活跃档案
        val activeProfileId = settingsManager.getActivePromptProfileId()
        val currentProfile = profiles.find { it.id == activeProfileId } ?: profiles.first()
        selectProfile(currentProfile)
        
        android.util.Log.d("MasterPromptFragment", "加载档案完成，共${profiles.size}个档案")
    }
    
    private fun selectProfile(profile: PromptProfile) {
        // 设置选中的档案
        activeProfile = profile
        profileAdapter.notifyDataSetChanged()
        
        // 加载档案的配置数据到ViewPager
        loadProfileData(profile)
    }
    
    private fun loadProfileData(profile: PromptProfile) {
        // 根据档案加载相应的配置数据到各个Fragment
        // 这里需要实现具体的数据加载逻辑
    }
    
    private fun createNewProfile() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建档案")
            .setMessage("请输入档案名称")
            .setView(android.widget.EditText(requireContext()).apply {
                hint = "档案名称"
            })
            .setPositiveButton("创建") { dialog, _ ->
                val input = (dialog as androidx.appcompat.app.AlertDialog).findViewById<android.widget.EditText>(android.R.id.edit)
                val name = input?.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    try {
                        val newProfile = PromptProfile(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            persona = "一个乐于助人的AI助手",
                            tone = "友好、清晰、简洁",
                            outputFormat = "使用Markdown格式进行回复",
                            description = "新建的AI助手档案"
                        )
                        
                        // 使用SettingsManager保存档案，确保触发通知机制
                        val settingsManager = SettingsManager.getInstance(requireContext())
                        settingsManager.savePromptProfile(newProfile)
                        
                        // 设置为当前活跃档案
                        settingsManager.setActivePromptProfileId(newProfile.id)
                        
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
            // 保存当前档案的配置
            saveProfileData(activeProfile!!)
            Toast.makeText(requireContext(), "档案保存成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "请先选择一个档案", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveProfileData(profile: PromptProfile) {
        // 这里实现保存档案配置数据的逻辑
        // 需要从各个Fragment中收集数据并保存
    }
}
