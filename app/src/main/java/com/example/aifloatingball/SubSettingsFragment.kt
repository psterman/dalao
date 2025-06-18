package com.example.aifloatingball

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class SubSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val prefResId = arguments?.getInt(ARG_PREFERENCE_ROOT, 0) ?: 0
        if (prefResId != 0) {
            setPreferencesFromResource(prefResId, rootKey)
        }
    }

    companion object {
        private const val ARG_PREFERENCE_ROOT = "preference_root"

        fun newInstance(prefResId: Int): SubSettingsFragment {
            val fragment = SubSettingsFragment()
            val args = Bundle()
            args.putInt(ARG_PREFERENCE_ROOT, prefResId)
            fragment.arguments = args
            return fragment
        }
    }
} 