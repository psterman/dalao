package com.example.aifloatingball.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.viewmodel.SettingsViewModel

class PersonalizationFragment : Fragment() {
    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_personalization, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.selectedProfile.observe(viewLifecycleOwner) { profile ->
            profile?.let { displayProfile(it) }
        }
    }

    private fun displayProfile(profile: PromptProfile) {
        // TODO: 实现个性化配置显示逻辑
    }

    fun collectProfileData(profile: PromptProfile): PromptProfile {
        // TODO: 实现个性化配置收集逻辑
        return profile
    }
} 