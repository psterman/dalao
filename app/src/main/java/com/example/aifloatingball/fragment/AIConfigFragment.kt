package com.example.aifloatingball.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.AIConfigPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * AI配置Fragment - 包含AI指令、基础信息、API配置的子标签
 */
class AIConfigFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.ai_config_tab_layout)
        viewPager = view.findViewById(R.id.ai_config_view_pager)

        setupViewPager()
    }

    private fun setupViewPager() {
        val fragmentList = listOf(
            MasterPromptFragment(),  // AI指令
            BasicInfoSubpageFragment(),  // 基础信息
            AIApiSettingsFragment()  // API设置
        )
        
        val pagerAdapter = AIConfigPagerAdapter(childFragmentManager, lifecycle, fragmentList)
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
                viewPager.post {
                    enableScrollingForCurrentFragment()
                }
            }
        })

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "AI指令"
                1 -> tab.text = "基础信息"
                2 -> tab.text = "API设置"
            }
        }.attach()
    }
    
    private fun enableScrollingForCurrentFragment() {
        // 查找当前Fragment并确保滚动正常
        val currentFragment = childFragmentManager.fragments.find { it.isVisible }
        currentFragment?.view?.let { fragmentView ->
            // 查找所有ScrollView并启用滚动
            val scrollViews = mutableListOf<android.widget.ScrollView>()
            findAllScrollViews(fragmentView, scrollViews)
            scrollViews.forEach { scrollView ->
                scrollView.isNestedScrollingEnabled = true
                scrollView.isScrollContainer = true
            }
        }
    }
    
    private fun findAllScrollViews(view: android.view.View, scrollViews: MutableList<android.widget.ScrollView>) {
        if (view is android.widget.ScrollView) {
            scrollViews.add(view)
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findAllScrollViews(view.getChildAt(i), scrollViews)
            }
        }
    }
}