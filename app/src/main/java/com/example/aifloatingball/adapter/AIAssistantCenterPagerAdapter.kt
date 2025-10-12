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
        TaskFragment(),          // 任务
        BasicInfoFragment(),      // 基础信息（搜索模式设置）
        AIConfigFragment(),       // AI配置（AI指令、基础信息、API设置）
        PersonalizationFragment() // 个性化
    )
    
    override fun getItemCount(): Int = fragments.size
    
    override fun createFragment(position: Int): Fragment = fragments[position]
    
    /**
     * 获取指定位置的Fragment
     */
    fun getFragment(position: Int): Fragment = fragments[position]
}
