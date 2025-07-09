package com.example.aifloatingball

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adapter.ProfileAdapter
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.ui.settings.CoreInstructionsFragment
import com.example.aifloatingball.ui.settings.ExtendedConfigFragment
import com.example.aifloatingball.ui.settings.AiParamsFragment
import com.example.aifloatingball.ui.settings.PersonalizationFragment
import com.example.aifloatingball.ui.settings.SectionsPagerAdapter
import com.example.aifloatingball.viewmodel.SettingsViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.UUID

class MasterPromptSettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var settingsManager: SettingsManager
    private lateinit var profilesRecyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: SectionsPagerAdapter

    private var profiles: MutableList<PromptProfile> = mutableListOf()
    private var activeProfile: PromptProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_prompt_settings)
        settingsManager = SettingsManager.getInstance(this)

        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupViewPager()
        loadProfiles()
    }

    private fun setupViews() {
        profilesRecyclerView = findViewById(R.id.profiles_recycler_view)
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tabs)
    }

    private fun setupViewPager() {
        pagerAdapter = SectionsPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "核心指令"
                1 -> "扩展配置"
                2 -> "AI参数"
                3 -> "个性化"
                else -> null
            }
        }.attach()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        profilesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        profileAdapter = ProfileAdapter(
            profiles,
            onProfileClicked = { profile -> selectProfile(profile) }
        )
        profilesRecyclerView.adapter = profileAdapter
    }

    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(settingsManager.getPromptProfiles())
        if (profiles.isEmpty()) {
            profiles.add(PromptProfile.DEFAULT)
        }
        val activeProfileId = settingsManager.getActivePromptProfileId()
        activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.first()
        profileAdapter.updateData(profiles)
        selectProfile(activeProfile!!)
    }

    private fun selectProfile(profile: PromptProfile) {
        activeProfile = profile
        viewModel.selectProfile(profile) // Update ViewModel
        profileAdapter.setActiveProfileId(profile.id)
        settingsManager.setActivePromptProfileId(profile.id)
    }

    private fun saveCurrentProfile() {
        if (activeProfile == null) {
            Toast.makeText(this, "没有活动的配置可保存", Toast.LENGTH_SHORT).show()
            return
        }

        var updatedProfile = activeProfile!!

        // Collect data from fragments
        supportFragmentManager.fragments.forEach { fragment ->
            when (fragment) {
                is CoreInstructionsFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
                is ExtendedConfigFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
                is AiParamsFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
                is PersonalizationFragment -> {
                    updatedProfile = fragment.collectProfileData(updatedProfile)
                }
            }
        }

        val index = profiles.indexOfFirst { it.id == updatedProfile.id }
        if (index != -1) {
            profiles[index] = updatedProfile
            profileAdapter.notifyItemChanged(index)
        } else {
            profiles.add(updatedProfile)
            profileAdapter.notifyItemInserted(profiles.size - 1)
        }
        activeProfile = updatedProfile
        settingsManager.savePromptProfiles(profiles)
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_master_prompt_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_add_profile -> {
                addNewProfile()
                true
            }
            // Assuming delete is handled via some other UI interaction now
            // R.id.action_delete_profile -> {
            //     activeProfile?.let { confirmDeleteProfile(it) }
            //     true
            // }
            R.id.action_save -> {
                saveCurrentProfile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addNewProfile() {
        val newProfile = PromptProfile.DEFAULT.copy(
            id = UUID.randomUUID().toString(),
            name = "新配置"
        )
        profiles.add(newProfile)
        profileAdapter.notifyItemInserted(profiles.size - 1)
        selectProfile(newProfile)
        profilesRecyclerView.smoothScrollToPosition(profiles.size - 1)
    }

    private fun confirmDeleteProfile(profile: PromptProfile) {
        if (profile.id == PromptProfile.DEFAULT.id) {
            Toast.makeText(this, "不能删除默认配置", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("删除配置")
            .setMessage("您确定要删除 '${profile.name}' 吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                deleteProfile(profile)
            }
            .show()
    }

    private fun deleteProfile(profile: PromptProfile) {
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles.removeAt(index)
            settingsManager.savePromptProfiles(profiles)
            profileAdapter.notifyItemRemoved(index)

            val newActiveProfile = profiles.firstOrNull() ?: PromptProfile.DEFAULT
            if (profiles.isEmpty()) {
                profiles.add(newActiveProfile)
            }
            selectProfile(newActiveProfile)
        }
    }
} 