package com.example.aifloatingball

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.text.SimpleDateFormat
import java.util.*

class MasterPromptSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.master_prompt_preferences, rootKey)
        settingsManager = SettingsManager.getInstance(requireContext())

        setupBirthDatePreference()
        setupSubPageNavigation()
    }

    private fun setupSubPageNavigation() {
        findPreference<Preference>("prompt_occupation_subpage")?.setOnPreferenceClickListener {
            navigateToSubPage(R.xml.preferences_prompt_occupation, "职业信息")
            true
        }
        findPreference<Preference>("prompt_interests_subpage")?.setOnPreferenceClickListener {
            navigateToSubPage(R.xml.preferences_prompt_interests, "兴趣与观念")
            true
        }
        findPreference<Preference>("prompt_health_subpage")?.setOnPreferenceClickListener {
            navigateToSubPage(R.xml.preferences_prompt_health, "健康状况")
            true
        }
    }

    private fun navigateToSubPage(prefResId: Int, title: String) {
        val intent = Intent(requireContext(), SubSettingsActivity::class.java).apply {
            putExtra(SubSettingsActivity.EXTRA_PREFERENCE_RESOURCE, prefResId)
            putExtra(SubSettingsActivity.EXTRA_PREFERENCE_TITLE, title)
        }
        startActivity(intent)
    }

    private fun setupBirthDatePreference() {
        val birthDatePreference: Preference? = findPreference("prompt_birth_date")
        val savedDate = settingsManager.getPromptBirthDate()

        if (savedDate.isNotEmpty()) {
            birthDatePreference?.summary = savedDate
        }

        birthDatePreference?.setOnPreferenceClickListener {
            showDatePickerDialog()
            true
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)

                settingsManager.setPromptBirthDate(formattedDate)
                findPreference<Preference>("prompt_birth_date")?.summary = formattedDate
            },
            currentYear,
            currentMonth,
            currentDay
        )
        datePickerDialog.show()
    }
} 