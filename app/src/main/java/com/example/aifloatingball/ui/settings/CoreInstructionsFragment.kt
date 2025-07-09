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
import com.google.android.material.textfield.TextInputEditText

class CoreInstructionsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var editProfileName: TextInputEditText
    private lateinit var editPersona: TextInputEditText
    private lateinit var editTone: TextInputEditText
    private lateinit var editOutputFormat: TextInputEditText
    private lateinit var editCustomInstructions: TextInputEditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_core_instructions, container, false)
        setupViews(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.selectedProfile.observe(viewLifecycleOwner) { profile ->
            displayProfile(profile)
        }
    }

    private fun setupViews(view: View) {
        editProfileName = view.findViewById(R.id.edit_profile_name)
        editPersona = view.findViewById(R.id.edit_persona)
        editTone = view.findViewById(R.id.edit_tone)
        editOutputFormat = view.findViewById(R.id.edit_output_format)
        editCustomInstructions = view.findViewById(R.id.edit_custom_instructions)
    }

    private fun displayProfile(profile: PromptProfile?) {
        profile ?: return
        editProfileName.setText(profile.name)
        editPersona.setText(profile.persona)
        editTone.setText(profile.tone)
        editOutputFormat.setText(profile.outputFormat)
        editCustomInstructions.setText(profile.customInstructions)
    }

    fun collectProfileData(profile: PromptProfile): PromptProfile {
        return profile.copy(
            name = editProfileName.text.toString(),
            persona = editPersona.text.toString(),
            tone = editTone.text.toString(),
            outputFormat = editOutputFormat.text.toString(),
            customInstructions = editCustomInstructions.text.toString()
        )
    }
}