package com.example.aifloatingball.ui.settings

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SectionsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val coreInstructionsFragment = CoreInstructionsFragment.newInstance()
    val advancedInstructionsFragment = AdvancedInstructionsFragment.newInstance()

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> coreInstructionsFragment
            1 -> advancedInstructionsFragment
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}