package com.example.aifloatingball

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adapter.ProfileAdapter
import com.example.aifloatingball.model.PromptProfile
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.*

class MasterPromptSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var profilesRecyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var viewPager: ViewPager2

    private var profiles: MutableList<PromptProfile> = mutableListOf()
    private var activeProfile: PromptProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_prompt_settings)
        settingsManager = SettingsManager.getInstance(this)

        setupToolbar()
        setupViews()
        loadProfiles()
        setupViewPager()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_master_prompt_settings)
    }

    private fun setupViews() {
        profilesRecyclerView = findViewById(R.id.profiles_recycler_view)
        viewPager = findViewById(R.id.view_pager)
        profilesRecyclerView.layoutManager = LinearLayoutManager(this)
        profileAdapter = ProfileAdapter(profiles) { profile ->
            // Save current profile before switching
            activeProfile?.let { saveCurrentProfileData(it) }
            // Switch to new profile
            switchProfile(profile)
        }
        profilesRecyclerView.adapter = profileAdapter
    }
    
    private fun setupViewPager() {
        val tabLayout: TabLayout = findViewById(R.id.tab_layout)
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getPageTitle(position)
        }.attach()
    }

    private fun loadProfiles() {
        profiles = settingsManager.getPromptProfiles()
        val activeProfileId = settingsManager.getActiveProfileId()

        if (profiles.isEmpty()) {
            // Create a default profile if none exist
            val defaultProfile = PromptProfile(name = "默认档案")
            profiles.add(defaultProfile)
            settingsManager.savePromptProfiles(profiles)
        }
        
        activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.first()
        settingsManager.setActiveProfileId(activeProfile?.id)
        
        updateProfileSelection()
        activeProfile?.let { settingsManager.loadProfileToPreferences(it) }

        profileAdapter.updateData(profiles)
    }

    private fun switchProfile(selectedProfile: PromptProfile) {
        if (selectedProfile.id == activeProfile?.id) return

        // Save current changes to the old active profile
        activeProfile?.let {
            val updatedProfile = settingsManager.savePreferencesToProfile(it)
            val index = profiles.indexOfFirst { p -> p.id == it.id }
            if (index != -1) {
                profiles[index] = updatedProfile
            }
        }

        // Switch to the new profile
        activeProfile = selectedProfile
        settingsManager.setActiveProfileId(activeProfile?.id)
        
        // Load new profile's data and refresh UI
        activeProfile?.let { settingsManager.loadProfileToPreferences(it) }
        updateProfileSelection()
        
        // Force ViewPager to recreate fragments to reflect new preferences
        viewPager.adapter = ViewPagerAdapter(this)
    }
    
    private fun updateProfileSelection() {
        profiles.forEach { it.isSelected = (it.id == activeProfile?.id) }
        profileAdapter.updateData(profiles)
    }

    private fun saveCurrentProfileData(profile: PromptProfile) {
        settingsManager.savePreferencesToProfile(profile)
        // Also update the master list in memory
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles[index] = profile
        }
    }

    private fun handleProfileDeletion(profileToDelete: PromptProfile) {
        if (profiles.size <= 1) {
            Toast.makeText(this, "无法删除最后一个档案", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("删除档案")
            .setMessage("您确定要删除 “${profileToDelete.name}” 吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val wasSelected = profileToDelete.isSelected
                val deletedPosition = profiles.indexOf(profileToDelete)

                // Remove from data source
                profiles.remove(profileToDelete)
                settingsManager.savePromptProfiles(profiles)

                // Notify adapter
                profileAdapter.updateData(profiles)

                // If the deleted profile was selected, select a new one
                if (wasSelected) {
                    val newProfileToSelect = profiles.getOrNull(0) // Select the first one
                    if (newProfileToSelect != null) {
                        switchProfile(newProfileToSelect)
                    } else {
                        // This case should not happen due to the size check, but as a fallback:
                        activeProfile = null
                        settingsManager.setActiveProfileId(null)
                        // Clear the form or show an empty state
                        recreate() // Simple way to reset the view
                    }
                }
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Save changes of the currently active profile before leaving
        activeProfile?.let {
            val updatedProfile = settingsManager.savePreferencesToProfile(it)
            val index = profiles.indexOfFirst { p -> p.id == it.id }
            if (index != -1) {
                profiles[index] = updatedProfile
            }
            settingsManager.savePromptProfiles(profiles)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.master_prompt_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_add_profile -> {
                showAddProfileDialog()
                true
            }
            R.id.action_delete_profile -> {
                activeProfile?.let { handleProfileDeletion(it) }
                    ?: Toast.makeText(this, "没有选中的档案", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_save_prompt -> {
                activeProfile?.let {
                    saveCurrentProfileData(it)
                    settingsManager.savePromptProfiles(profiles) // Save the whole list
                    Toast.makeText(this, "${it.name} 已保存", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddProfileDialog() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("新增档案")
            .setMessage("请输入新档案的名称")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    val newProfile = PromptProfile(name = name)
                    profiles.add(newProfile)
                    settingsManager.savePromptProfiles(profiles)
                    switchProfile(newProfile)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        private val fragmentsInfo = listOf(
            Pair("基本信息", MasterPromptSubPageFragment.newInstance(R.xml.prompt_basic_info_preferences)),
            Pair("职业信息", PromptOccupationFragment()),
            Pair("兴趣观念", PromptInterestsFragment()),
            Pair("健康状况", PromptHealthFragment()),
            Pair("回复偏好", MasterPromptSubPageFragment.newInstance(R.xml.prompt_reply_preferences))
        )

        override fun getItemCount(): Int = fragmentsInfo.size
        override fun createFragment(position: Int): Fragment = fragmentsInfo[position].second
        fun getPageTitle(position: Int): CharSequence = fragmentsInfo[position].first
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    abstract class BaseSettingsFragment : PreferenceFragmentCompat()
    class PromptOccupationFragment : BaseSettingsFragment() { override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) { setPreferencesFromResource(R.xml.preferences_prompt_occupation, rootKey) } }
    class PromptInterestsFragment : BaseSettingsFragment() { override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) { setPreferencesFromResource(R.xml.preferences_prompt_interests, rootKey) } }
    class PromptHealthFragment : BaseSettingsFragment() { override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) { setPreferencesFromResource(R.xml.preferences_prompt_health, rootKey) } }
} 