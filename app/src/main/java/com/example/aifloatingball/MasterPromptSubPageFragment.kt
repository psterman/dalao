package com.example.aifloatingball

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.util.*

class MasterPromptSubPageFragment : PreferenceFragmentCompat() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        settingsManager = SettingsManager.getInstance(requireContext())
        val prefResId = arguments?.getInt(ARG_PREFERENCE_RES_ID, 0) ?: 0
        if (prefResId != 0) {
            setPreferencesFromResource(prefResId, rootKey)
        }

        findPreference<Preference>("prompt_birth_date")?.let { preference ->
            val savedDate = settingsManager.getPromptBirthDate()
            if (savedDate.isNotBlank()) {
                preference.summary = savedDate
            }

            preference.setOnPreferenceClickListener {
                showDatePickerDialog(preference)
                true
            }
        }
    }

    private fun showDatePickerDialog(preference: Preference) {
        val calendar = Calendar.getInstance()
        val savedDate = settingsManager.getPromptBirthDate()
        if (savedDate.isNotBlank()) {
            val parts = savedDate.split("-")
            if (parts.size == 3) {
                calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val date = "$selectedYear-${selectedMonth + 1}-$selectedDay"
            settingsManager.setPromptBirthDate(date)
            preference.summary = date
        }, year, month, day).show()
    }

    companion object {
        private const val ARG_PREFERENCE_RES_ID = "preference_res_id"

        fun newInstance(preferenceResId: Int): MasterPromptSubPageFragment {
            val fragment = MasterPromptSubPageFragment()
            val args = Bundle()
            args.putInt(ARG_PREFERENCE_RES_ID, preferenceResId)
            fragment.arguments = args
            return fragment
        }
    }
} 