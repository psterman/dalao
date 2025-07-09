package com.example.aifloatingball.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
    private lateinit var toneDropdown: AutoCompleteTextView
    private lateinit var outputFormatDropdown: AutoCompleteTextView
    private lateinit var editCustomInstructions: TextInputEditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_core_instructions, container, false)
        setupViews(view)
        setupDropdowns()
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
        toneDropdown = view.findViewById(R.id.tone_dropdown)
        outputFormatDropdown = view.findViewById(R.id.output_format_dropdown)
        editCustomInstructions = view.findViewById(R.id.edit_custom_instructions)
    }
    
    private fun setupDropdowns() {
        context?.let { ctx ->
            // 设置语调风格下拉菜单
            val toneAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.tone_entries)
            )
            toneDropdown.setAdapter(toneAdapter)
            
            // 设置回答格式下拉菜单
            val outputFormatAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.output_format_entries)
            )
            outputFormatDropdown.setAdapter(outputFormatAdapter)
        }
    }

    private fun displayProfile(profile: PromptProfile?) {
        profile ?: return
        editProfileName.setText(profile.name)
        editPersona.setText(profile.persona)
        
        // 根据保存的值设置下拉菜单的选定项
        val toneEntries = resources.getStringArray(R.array.tone_entries)
        val toneValues = resources.getStringArray(R.array.tone_values)
        val toneIndex = toneValues.indexOf(profile.tone)
        if (toneIndex >= 0) {
            toneDropdown.setText(toneEntries[toneIndex], false)
        } else {
            toneDropdown.setText("", false)
        }
        
        val outputFormatEntries = resources.getStringArray(R.array.output_format_entries)
        val outputFormatValues = resources.getStringArray(R.array.output_format_values)
        val outputFormatIndex = outputFormatValues.indexOf(profile.outputFormat)
        if (outputFormatIndex >= 0) {
            outputFormatDropdown.setText(outputFormatEntries[outputFormatIndex], false)
        } else {
            outputFormatDropdown.setText("", false)
        }
        
        editCustomInstructions.setText(profile.customInstructions)
    }

    fun collectProfileData(profile: PromptProfile): PromptProfile {
        // 获取选定值的索引并转换为对应的值
        val toneEntries = resources.getStringArray(R.array.tone_entries)
        val toneValues = resources.getStringArray(R.array.tone_values)
        val selectedTone = toneDropdown.text.toString()
        val toneIndex = toneEntries.indexOf(selectedTone)
        val toneValue = if (toneIndex >= 0) toneValues[toneIndex] else ""
        
        val outputFormatEntries = resources.getStringArray(R.array.output_format_entries)
        val outputFormatValues = resources.getStringArray(R.array.output_format_values)
        val selectedOutputFormat = outputFormatDropdown.text.toString()
        val outputFormatIndex = outputFormatEntries.indexOf(selectedOutputFormat)
        val outputFormatValue = if (outputFormatIndex >= 0) outputFormatValues[outputFormatIndex] else ""
        
        return profile.copy(
            name = editProfileName.text.toString(),
            persona = editPersona.text.toString(),
            tone = toneValue,
            outputFormat = outputFormatValue,
            customInstructions = editCustomInstructions.text.toString()
        )
    }
}