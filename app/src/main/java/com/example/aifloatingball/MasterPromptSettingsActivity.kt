package com.example.aifloatingball

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.example.aifloatingball.MasterPromptSubPageFragment
class MasterPromptSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_prompt_settings)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_master_prompt_settings)

        val viewPager: ViewPager2 = findViewById(R.id.view_pager)
        val tabLayout: TabLayout = findViewById(R.id.tab_layout)

        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getPageTitle(position)
        }.attach()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.master_prompt_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                // Preferences are saved automatically by PreferenceFragmentCompat
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                true
            }
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        private val fragments = listOf(
            Pair("基本信息", MasterPromptSubPageFragment.newInstance(R.xml.prompt_basic_info_preferences)),
            Pair("职业信息", PromptOccupationFragment()),
            Pair("兴趣观念", PromptInterestsFragment()),
            Pair("健康状况", PromptHealthFragment()),
            Pair("回复偏好", MasterPromptSubPageFragment.newInstance(R.xml.prompt_reply_preferences))
        )

        override fun getItemCount(): Int = fragments.size

        override fun createFragment(position: Int): Fragment = fragments[position].second

        fun getPageTitle(position: Int): CharSequence = fragments[position].first
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // Base fragment for settings pages
    abstract class BaseSettingsFragment : PreferenceFragmentCompat()

    // Fragment for Occupation settings
    class PromptOccupationFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_prompt_occupation, rootKey)
        }
    }

    // Fragment for Interests settings
    class PromptInterestsFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_prompt_interests, rootKey)
        }
    }

    // Fragment for Health settings
    class PromptHealthFragment : BaseSettingsFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_prompt_health, rootKey)
        }
    }
} 