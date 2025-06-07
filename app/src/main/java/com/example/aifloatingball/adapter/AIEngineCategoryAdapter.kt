package com.example.aifloatingball.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.ui.settings.AIEngineListFragment

class AIEngineCategoryAdapter(
    activity: FragmentActivity,
    private val categories: List<List<AISearchEngine>>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        return AIEngineListFragment.newInstance(categories[position])
    }
} 