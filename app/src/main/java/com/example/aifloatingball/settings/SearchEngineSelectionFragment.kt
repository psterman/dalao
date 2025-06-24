package com.example.aifloatingball.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineCategory

class SearchEngineSelectionFragment : PreferenceFragmentCompat() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.search_engine_preferences, rootKey)
        settingsManager = SettingsManager.getInstance(requireContext())

        val allEngines = SearchEngine.DEFAULT_ENGINES
        val enabledEngines = settingsManager.getEnabledSearchEngines()
        
        // Group engines by category
        val enginesByCategory = allEngines.groupBy { it.category }

        // Create preferences for each category
        for (category in SearchEngineCategory.values()) {
            val engines = enginesByCategory[category] ?: continue

            val preferenceCategory = PreferenceCategory(requireContext()).apply {
                title = category.displayName
                isIconSpaceReserved = false
            }
            preferenceScreen.addPreference(preferenceCategory)

            engines.forEach { engine ->
                val switchPreference = SwitchPreferenceCompat(requireContext()).apply {
                    key = "engine_${engine.name}"
                    title = engine.displayName
                    summary = engine.description
                    isChecked = enabledEngines.contains(engine.name)
                    
                    setOnPreferenceChangeListener { _, newValue ->
                        val currentEnabled = settingsManager.getEnabledSearchEngines().toMutableSet()
                        if (newValue as Boolean) {
                            currentEnabled.add(engine.name)
                        } else {
                            currentEnabled.remove(engine.name)
                        }
                        settingsManager.saveEnabledSearchEngines(currentEnabled)
                        true
                    }
                }
                preferenceCategory.addPreference(switchPreference)
            }
        }
    }
} 