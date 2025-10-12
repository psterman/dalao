package com.example.aifloatingball.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.aifloatingball.fragment.*

/**
 * AI助手中心ViewPager2适配器
 */
class AIAssistantCenterPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    private val fragments = listOf(
        TaskFragment(),                    // 任务
        BasicInfoFragment(),               // 基础信息（融合搜索模式设置和档案管理）
        MasterPromptFragment(),           // AI指令
        AIApiSettingsFragment()           // API设置
    )
    
    override fun getItemCount(): Int = fragments.size
    
    override fun createFragment(position: Int): Fragment = fragments[position]
    
    /**
     * 获取指定位置的Fragment
     */
    fun getFragment(position: Int): Fragment = fragments[position]
}
