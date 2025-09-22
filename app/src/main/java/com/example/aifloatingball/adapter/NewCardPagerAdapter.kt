package com.example.aifloatingball.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.aifloatingball.fragment.HistoryPageFragment
import com.example.aifloatingball.fragment.BookmarksPageFragment

/**
 * 新建卡片弹窗的ViewPager适配器
 */
class NewCardPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 2
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HistoryPageFragment()
            1 -> BookmarksPageFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
