package com.example.aifloatingball

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class MasterPromptSubPageFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val prefResId = arguments?.getInt(ARG_PREFERENCE_RES_ID, 0) ?: 0
        if (prefResId != 0) {
            setPreferencesFromResource(prefResId, rootKey)
        }
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