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
        TaskFragmentTwoColumn(),        // 任务（两列布局）
        MasterPromptFragment(),         // AI指令（包含档案管理、核心指令、扩展配置等）
        AIApiSettingsFragment()         // API设置
    )
    
    override fun getItemCount(): Int = fragments.size
    
    override fun createFragment(position: Int): Fragment = fragments[position]
    
    /**
     * 获取指定位置的Fragment
     */
    fun getFragment(position: Int): Fragment = fragments[position]
}
