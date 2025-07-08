package com.example.aifloatingball

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.ProfileAdapter
import com.example.aifloatingball.model.PromptProfile
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class MasterPromptSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var profilesRecyclerView: RecyclerView
    private lateinit var profileAdapter: ProfileAdapter

    private lateinit var editProfileName: TextInputEditText
    private lateinit var editPersona: TextInputEditText
    private lateinit var editTone: TextInputEditText
    private lateinit var editOutputFormat: TextInputEditText
    private lateinit var editCustomInstructions: TextInputEditText

    private var profiles: MutableList<PromptProfile> = mutableListOf()
    private var activeProfile: PromptProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_prompt_settings)
        settingsManager = SettingsManager.getInstance(this)

        setupToolbar()
        setupViews()
        loadProfiles()
        setupRecyclerView()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_master_prompt_settings)
    }

    private fun setupViews() {
        profilesRecyclerView = findViewById(R.id.profiles_recycler_view)
        editProfileName = findViewById(R.id.edit_profile_name)
        editPersona = findViewById(R.id.edit_persona)
        editTone = findViewById(R.id.edit_tone)
        editOutputFormat = findViewById(R.id.edit_output_format)
        editCustomInstructions = findViewById(R.id.edit_custom_instructions)
    }

    private fun setupRecyclerView() {
        profileAdapter = ProfileAdapter(profiles, ::onProfileSelected)
        profilesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        profilesRecyclerView.adapter = profileAdapter
    }

    private fun loadProfiles() {
        profiles = settingsManager.getAllPromptProfiles()
        val activeProfileId = settingsManager.getActivePromptProfileId()
        
        activeProfile = profiles.find { it.id == activeProfileId }
        if (activeProfile == null) {
            activeProfile = profiles.firstOrNull()
            activeProfile?.let { settingsManager.setActivePromptProfileId(it.id) }
        }

        updateUIForActiveProfile()
    }

    private fun onProfileSelected(selectedProfile: PromptProfile) {
        if (selectedProfile.id == activeProfile?.id) return

        // 1. Save current UI state to the old active profile object
        saveCurrentUIToActiveProfile()

        // 2. Switch to the new profile
        activeProfile = selectedProfile
        settingsManager.setActivePromptProfileId(selectedProfile.id)

        // 3. Update UI with the new active profile's data
        updateUIForActiveProfile()
    }

    private fun saveCurrentUIToActiveProfile() {
        activeProfile?.let { profile ->
            val updatedProfile = profile.copy(
                name = editProfileName.text.toString(),
                persona = editPersona.text.toString(),
                tone = editTone.text.toString(),
                outputFormat = editOutputFormat.text.toString(),
                customInstructions = editCustomInstructions.text.toString().takeIf { it.isNotBlank() }
            )
            // Update the profile in our in-memory list
            val index = profiles.indexOfFirst { it.id == profile.id }
            if (index != -1) {
                profiles[index] = updatedProfile
            }
        }
    }

    private fun updateUIForActiveProfile() {
        activeProfile?.let {
            editProfileName.setText(it.name)
            editPersona.setText(it.persona)
            editTone.setText(it.tone)
            editOutputFormat.setText(it.outputFormat)
            editCustomInstructions.setText(it.customInstructions ?: "")
        }
        profileAdapter.setActiveProfileId(activeProfile?.id)

        // Scroll to the active profile
        val activeIndex = profiles.indexOfFirst { it.id == activeProfile?.id }
        if (activeIndex != -1) {
            profilesRecyclerView.smoothScrollToPosition(activeIndex)
        }
    }

    private fun saveAllChanges() {
        saveCurrentUIToActiveProfile() // Save any pending changes in the UI
        settingsManager.saveAllPromptProfiles(profiles)
        Toast.makeText(this, "所有修改已保存", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        saveAllChanges()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.master_prompt_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_add_profile -> {
                showAddProfileDialog()
                true
            }
            R.id.action_delete_profile -> {
                activeProfile?.let { handleDeleteProfile(it) }
                true
            }
            R.id.action_save_prompt -> {
                saveAllChanges()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddProfileDialog() {
        val editText = EditText(this).apply { hint = "例如：编程专家" }
        MaterialAlertDialogBuilder(this)
            .setTitle("新建用户画像")
            .setView(editText)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotBlank()) {
                    // Create a new profile based on the default, but with a new ID and the entered name
                    val newProfile = PromptProfile.DEFAULT.copy(
                        id = UUID.randomUUID().toString(),
                        name = name
                    )
                    profiles.add(newProfile)
                    profileAdapter.notifyItemInserted(profiles.size - 1)
                    onProfileSelected(newProfile) // Switch to the new profile
                }
            }
            .show()
    }
    
    private fun handleDeleteProfile(profileToDelete: PromptProfile) {
        if (profileToDelete.id == "default") {
            Toast.makeText(this, "不能删除默认画像", Toast.LENGTH_SHORT).show()
            return
        }
        if (profiles.size <= 1) {
            Toast.makeText(this, "至少保留一个画像", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("删除画像")
            .setMessage("确定要删除 “${profileToDelete.name}” 吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val deleteIndex = profiles.indexOf(profileToDelete)
                profiles.removeAt(deleteIndex)
                profileAdapter.notifyItemRemoved(deleteIndex)
                
                // Select the first profile as the new active one
                onProfileSelected(profiles.first())
            }
            .show()
    }
} 