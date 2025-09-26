package com.example.aifloatingball.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.viewmodel.SettingsViewModel
import com.example.aifloatingball.utils.ThemeUtils

class ExtendedConfigFragment : Fragment() {
    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var expertiseDropdown: AutoCompleteTextView
    private lateinit var languageDropdown: AutoCompleteTextView
    private lateinit var formalityDropdown: AutoCompleteTextView
    private lateinit var responseLengthDropdown: AutoCompleteTextView
    private lateinit var seekCreativity: SeekBar
    private lateinit var labelCreativity: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            val view = inflater.inflate(R.layout.fragment_extended_config, container, false)
            // 应用当前主题
            applyTheme(view)
            setupViews(view)
            setupDropdowns()
            setupSeekBarListeners()
            view
        } catch (e: Exception) {
            android.util.Log.e("ExtendedConfigFragment", "Error in onCreateView", e)
            // 返回一个简单的错误视图
            val errorView = TextView(requireContext())
            errorView.text = "扩展配置加载失败，请重试"
            errorView.gravity = android.view.Gravity.CENTER
            errorView
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.selectedProfile.observe(viewLifecycleOwner) { profile ->
            displayProfile(profile)
        }
    }
    
    private fun applyTheme(view: View) {
        // 使用主题工具类应用AI助手中心主题
        ThemeUtils.applyAIAssistantTheme(view, requireContext())
    }

    private fun setupViews(view: View) {
        try {
            expertiseDropdown = view.findViewById(R.id.expertise_dropdown) ?: throw IllegalStateException("expertise_dropdown not found")
            languageDropdown = view.findViewById(R.id.language_dropdown) ?: throw IllegalStateException("language_dropdown not found")
            formalityDropdown = view.findViewById(R.id.formality_dropdown) ?: throw IllegalStateException("formality_dropdown not found")
            responseLengthDropdown = view.findViewById(R.id.response_length_dropdown) ?: throw IllegalStateException("response_length_dropdown not found")
            seekCreativity = view.findViewById(R.id.seek_creativity) ?: throw IllegalStateException("seek_creativity not found")
            labelCreativity = view.findViewById(R.id.label_creativity) ?: throw IllegalStateException("label_creativity not found")
        } catch (e: Exception) {
            android.util.Log.e("ExtendedConfigFragment", "Error setting up views", e)
            throw e
        }
    }
    
    private fun setupDropdowns() {
        context?.let { ctx ->
            // 设置专业领域下拉菜单
            val expertiseAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.expertise_entries)
            )
            expertiseDropdown.setAdapter(expertiseAdapter)
            
            // 设置语言偏好下拉菜单
            val languageAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.language_entries)
            )
            languageDropdown.setAdapter(languageAdapter)
            
            // 设置正式程度下拉菜单
            val formalityAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.formality_entries)
            )
            formalityDropdown.setAdapter(formalityAdapter)
            
            // 设置回复长度下拉菜单
            val responseLengthAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.response_length_entries)
            )
            responseLengthDropdown.setAdapter(responseLengthAdapter)
        }
    }

    private fun setupSeekBarListeners() {
        seekCreativity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                labelCreativity.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun displayProfile(profile: PromptProfile?) {
        profile ?: return
        
        // 根据保存的值设置下拉菜单的选定项
        val expertiseEntries = resources.getStringArray(R.array.expertise_entries)
        val expertiseValues = resources.getStringArray(R.array.expertise_values)
        val expertiseIndex = expertiseValues.indexOf(profile.expertise)
        if (expertiseIndex >= 0) {
            expertiseDropdown.setText(expertiseEntries[expertiseIndex], false)
        } else {
            expertiseDropdown.setText("", false)
        }
        
        val languageEntries = resources.getStringArray(R.array.language_entries)
        val languageValues = resources.getStringArray(R.array.language_values)
        val languageIndex = languageValues.indexOf(profile.language)
        if (languageIndex >= 0) {
            languageDropdown.setText(languageEntries[languageIndex], false)
        } else {
            languageDropdown.setText("", false)
        }
        
        val formalityEntries = resources.getStringArray(R.array.formality_entries)
        val formalityValues = resources.getStringArray(R.array.formality_values)
        val formalityIndex = formalityValues.indexOf(profile.formality)
        if (formalityIndex >= 0) {
            formalityDropdown.setText(formalityEntries[formalityIndex], false)
        } else {
            formalityDropdown.setText("", false)
        }
        
        val responseLengthEntries = resources.getStringArray(R.array.response_length_entries)
        val responseLengthValues = resources.getStringArray(R.array.response_length_values)
        val responseLengthIndex = responseLengthValues.indexOf(profile.responseLength)
        if (responseLengthIndex >= 0) {
            responseLengthDropdown.setText(responseLengthEntries[responseLengthIndex], false)
        } else {
            responseLengthDropdown.setText("", false)
        }
        
        seekCreativity.progress = profile.creativity
        labelCreativity.text = profile.creativity.toString()
    }

    fun collectProfileData(profile: PromptProfile): PromptProfile {
        // 获取选定值的索引并转换为对应的值
        val expertiseEntries = resources.getStringArray(R.array.expertise_entries)
        val expertiseValues = resources.getStringArray(R.array.expertise_values)
        val selectedExpertise = expertiseDropdown.text.toString()
        val expertiseIndex = expertiseEntries.indexOf(selectedExpertise)
        val expertiseValue = if (expertiseIndex >= 0) expertiseValues[expertiseIndex] else ""
        
        val languageEntries = resources.getStringArray(R.array.language_entries)
        val languageValues = resources.getStringArray(R.array.language_values)
        val selectedLanguage = languageDropdown.text.toString()
        val languageIndex = languageEntries.indexOf(selectedLanguage)
        val languageValue = if (languageIndex >= 0) languageValues[languageIndex] else ""
        
        val formalityEntries = resources.getStringArray(R.array.formality_entries)
        val formalityValues = resources.getStringArray(R.array.formality_values)
        val selectedFormality = formalityDropdown.text.toString()
        val formalityIndex = formalityEntries.indexOf(selectedFormality)
        val formalityValue = if (formalityIndex >= 0) formalityValues[formalityIndex] else ""
        
        val responseLengthEntries = resources.getStringArray(R.array.response_length_entries)
        val responseLengthValues = resources.getStringArray(R.array.response_length_values)
        val selectedResponseLength = responseLengthDropdown.text.toString()
        val responseLengthIndex = responseLengthEntries.indexOf(selectedResponseLength)
        val responseLengthValue = if (responseLengthIndex >= 0) responseLengthValues[responseLengthIndex] else ""
        
        return profile.copy(
            expertise = expertiseValue,
            language = languageValue,
            formality = formalityValue,
            responseLength = responseLengthValue,
            creativity = seekCreativity.progress
        )
    }
} 