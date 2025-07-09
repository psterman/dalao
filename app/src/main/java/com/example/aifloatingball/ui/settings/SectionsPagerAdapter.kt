package com.example.aifloatingball.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SectionsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CoreInstructionsFragment()
            1 -> ExtendedConfigFragment()
            2 -> AiParamsFragment()
            3 -> PersonalizationFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}