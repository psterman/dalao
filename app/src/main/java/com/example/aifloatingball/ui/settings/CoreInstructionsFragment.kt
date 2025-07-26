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
        return try {
            android.util.Log.d("CoreInstructionsFragment", "开始创建视图")

            val view = inflater.inflate(R.layout.fragment_core_instructions, container, false)
            android.util.Log.d("CoreInstructionsFragment", "成功加载布局")

            setupViews(view)
            android.util.Log.d("CoreInstructionsFragment", "成功设置视图")

            setupDropdowns()
            android.util.Log.d("CoreInstructionsFragment", "成功设置下拉菜单")

            android.util.Log.d("CoreInstructionsFragment", "视图创建完成")
            view
        } catch (e: Exception) {
            android.util.Log.e("CoreInstructionsFragment", "创建视图失败", e)
            // 返回一个简单的错误视图
            createErrorView(inflater, container, e.message ?: "未知错误")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            viewModel.selectedProfile.observe(viewLifecycleOwner) { profile ->
                displayProfile(profile)
            }
        } catch (e: Exception) {
            android.util.Log.e("CoreInstructionsFragment", "设置观察者失败", e)
        }
    }

    /**
     * 创建错误视图
     */
    private fun createErrorView(inflater: LayoutInflater, container: ViewGroup?, errorMessage: String): View {
        return android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)

            addView(android.widget.TextView(requireContext()).apply {
                text = "加载失败"
                textSize = 18f
                setTextColor(android.graphics.Color.RED)
                gravity = android.view.Gravity.CENTER
            })

            addView(android.widget.TextView(requireContext()).apply {
                text = "错误信息: $errorMessage"
                textSize = 14f
                setTextColor(android.graphics.Color.GRAY)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 0)
            })

            addView(android.widget.Button(requireContext()).apply {
                text = "重试"
                setOnClickListener {
                    // 尝试重新加载Fragment
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, CoreInstructionsFragment())
                        .commit()
                }
            })
        }
    }

    private fun setupViews(view: View) {
        try {
            android.util.Log.d("CoreInstructionsFragment", "开始设置视图")

            editProfileName = view.findViewById(R.id.edit_profile_name)
                ?: throw IllegalStateException("找不到edit_profile_name控件")
            android.util.Log.d("CoreInstructionsFragment", "成功找到edit_profile_name")

            editPersona = view.findViewById(R.id.edit_persona)
                ?: throw IllegalStateException("找不到edit_persona控件")
            android.util.Log.d("CoreInstructionsFragment", "成功找到edit_persona")

            toneDropdown = view.findViewById(R.id.tone_dropdown)
                ?: throw IllegalStateException("找不到tone_dropdown控件")
            android.util.Log.d("CoreInstructionsFragment", "成功找到tone_dropdown")

            outputFormatDropdown = view.findViewById(R.id.output_format_dropdown)
                ?: throw IllegalStateException("找不到output_format_dropdown控件")
            android.util.Log.d("CoreInstructionsFragment", "成功找到output_format_dropdown")

            editCustomInstructions = view.findViewById(R.id.edit_custom_instructions)
                ?: throw IllegalStateException("找不到edit_custom_instructions控件")
            android.util.Log.d("CoreInstructionsFragment", "成功找到edit_custom_instructions")

            android.util.Log.d("CoreInstructionsFragment", "所有视图设置完成")
        } catch (e: Exception) {
            android.util.Log.e("CoreInstructionsFragment", "设置视图失败", e)
            throw e
        }
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