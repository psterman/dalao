package com.example.aifloatingball.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.viewmodel.SettingsViewModel
import com.example.aifloatingball.utils.ThemeUtils

class PersonalizationFragment : Fragment() {
    private val viewModel: SettingsViewModel by activityViewModels()
    
    // 基本信息
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var ageGroupDropdown: AutoCompleteTextView
    
    // 职业信息
    private lateinit var occupationDropdown: AutoCompleteTextView
    private lateinit var occupationInterestDropdown: AutoCompleteTextView
    private lateinit var educationDropdown: AutoCompleteTextView
    
    // 兴趣与偏好
    private lateinit var entertainmentDropdown: AutoCompleteTextView
    private lateinit var shoppingDropdown: AutoCompleteTextView
    private lateinit var nicheDropdown: AutoCompleteTextView
    
    // 观念与取向
    private lateinit var orientationDropdown: AutoCompleteTextView
    private lateinit var valuesDropdown: AutoCompleteTextView
    
    // 健康信息
    private lateinit var diagnosedDropdown: AutoCompleteTextView
    private lateinit var dietaryDropdown: AutoCompleteTextView
    private lateinit var sleepDropdown: AutoCompleteTextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            val view = inflater.inflate(R.layout.fragment_personalization, container, false)
            // 应用当前主题
            applyTheme(view)
            view
        } catch (e: Exception) {
            android.util.Log.e("PersonalizationFragment", "Error in onCreateView", e)
            // 返回null让系统处理错误
            null
        }
    }
    
    private fun applyTheme(view: View) {
        // 使用主题工具类应用AI助手中心主题
        ThemeUtils.applyAIAssistantTheme(view, requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            initializeViews(view)
            setupDropdowns()
            
            viewModel.selectedProfile.observe(viewLifecycleOwner) { profile ->
                updateUI(profile)
            }
        } catch (e: Exception) {
            android.util.Log.e("PersonalizationFragment", "Error in onViewCreated", e)
        }
    }

    private fun initializeViews(view: View) {
        try {
            // 基本信息
            genderDropdown = view.findViewById(R.id.gender_dropdown) ?: throw NullPointerException("gender_dropdown not found")
            ageGroupDropdown = view.findViewById(R.id.age_group_dropdown) ?: throw NullPointerException("age_group_dropdown not found")
            
            // 职业信息
            occupationDropdown = view.findViewById(R.id.occupation_dropdown) ?: throw NullPointerException("occupation_dropdown not found")
            occupationInterestDropdown = view.findViewById(R.id.occupation_interest_dropdown) ?: throw NullPointerException("occupation_interest_dropdown not found")
            educationDropdown = view.findViewById(R.id.education_dropdown) ?: throw NullPointerException("education_dropdown not found")
            
            // 兴趣与偏好
            entertainmentDropdown = view.findViewById(R.id.entertainment_dropdown) ?: throw NullPointerException("entertainment_dropdown not found")
            shoppingDropdown = view.findViewById(R.id.shopping_dropdown) ?: throw NullPointerException("shopping_dropdown not found")
            nicheDropdown = view.findViewById(R.id.niche_dropdown) ?: throw NullPointerException("niche_dropdown not found")
            
            // 观念与取向
            orientationDropdown = view.findViewById(R.id.orientation_dropdown) ?: throw NullPointerException("orientation_dropdown not found")
            valuesDropdown = view.findViewById(R.id.values_dropdown) ?: throw NullPointerException("values_dropdown not found")
            
            // 健康信息
            diagnosedDropdown = view.findViewById(R.id.diagnosed_dropdown) ?: throw NullPointerException("diagnosed_dropdown not found")
            dietaryDropdown = view.findViewById(R.id.dietary_dropdown) ?: throw NullPointerException("dietary_dropdown not found")
            sleepDropdown = view.findViewById(R.id.sleep_dropdown) ?: throw NullPointerException("sleep_dropdown not found")
        } catch (e: Exception) {
            android.util.Log.e("PersonalizationFragment", "Error initializing views", e)
            throw e
        }
    }

    private fun setupDropdowns() {
        // 设置性别选项
        val genders = arrayOf("未设置", "男", "女", "其他")
        genderDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, genders))
        
        // 设置年龄段选项
        val ageGroups = arrayOf("未设置", "90后 (1990-1999)", "80后 (1980-1989)", "70后 (1970-1979)", "60后 (1960-1969)", "其他")
        ageGroupDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, ageGroups))
        
        // 设置职业选项
        val occupations = resources.getStringArray(R.array.prompt_occupation_current_entries)
        occupationDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, occupations))
        
        // 设置职业兴趣选项
        val occupationInterests = resources.getStringArray(R.array.prompt_occupation_interest_entries)
        occupationInterestDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, occupationInterests))
        
        // 设置教育程度选项
        val educationLevels = arrayOf("未设置", "高中及以下", "大专", "本科", "硕士", "博士")
        educationDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, educationLevels))
        
        // 设置日常娱乐选项
        val entertainments = resources.getStringArray(R.array.prompt_interests_entertainment_entries)
        entertainmentDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, entertainments))
        
        // 设置购物喜好选项
        val shoppingPrefs = resources.getStringArray(R.array.prompt_interests_shopping_entries)
        shoppingDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, shoppingPrefs))
        
        // 设置小众爱好选项
        val nicheInterests = resources.getStringArray(R.array.prompt_interests_niche_entries)
        nicheDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, nicheInterests))
        
        // 设置性取向选项
        val orientations = resources.getStringArray(R.array.prompt_interests_orientation_entries)
        orientationDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, orientations))
        
        // 设置三观倾向选项
        val values = resources.getStringArray(R.array.prompt_interests_values_entries)
        valuesDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, values))
        
        // 设置确诊疾病选项
        val diagnosed = resources.getStringArray(R.array.health_diagnosed_entries)
        diagnosedDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, diagnosed))
        
        // 设置饮食偏好选项
        val dietary = resources.getStringArray(R.array.health_dietary_restrictions_entries)
        dietaryDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, dietary))
        
        // 设置睡眠模式选项
        val sleep = resources.getStringArray(R.array.health_sleep_pattern_entries)
        sleepDropdown.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item, sleep))
    }

    private fun updateUI(profile: PromptProfile) {
        // 基本信息
        genderDropdown.setText(profile.gender, false)
        
        // 将日期转换为年代段
        val ageGroup = when {
            profile.dateOfBirth.contains("199") -> "90后 (1990-1999)"
            profile.dateOfBirth.contains("198") -> "80后 (1980-1989)"
            profile.dateOfBirth.contains("197") -> "70后 (1970-1979)"
            profile.dateOfBirth.contains("196") -> "60后 (1960-1969)"
            profile.dateOfBirth.isBlank() || profile.dateOfBirth == "未设置" -> "未设置"
            else -> "其他"
        }
        ageGroupDropdown.setText(ageGroup, false)
        
        // 职业信息
        occupationDropdown.setText(profile.occupation, false)
        
        // 其他字段可能需要从保存的列表中选择第一个或默认值
        educationDropdown.setText(profile.education, false)
        
        // 健康信息
        if (profile.healthInfo.isNotBlank() && profile.healthInfo != "未设置") {
            // 尝试匹配健康信息到下拉菜单选项
            val healthOptions = resources.getStringArray(R.array.health_diagnosed_entries)
            val matchedOption = healthOptions.find { profile.healthInfo.contains(it) } ?: healthOptions.first()
            diagnosedDropdown.setText(matchedOption, false)
        } else {
            diagnosedDropdown.setText("无上述疾病", false)
        }
        
        // 默认值设置
        occupationInterestDropdown.setText(resources.getStringArray(R.array.prompt_occupation_interest_entries).first(), false)
        entertainmentDropdown.setText(resources.getStringArray(R.array.prompt_interests_entertainment_entries).first(), false)
        shoppingDropdown.setText(resources.getStringArray(R.array.prompt_interests_shopping_entries).first(), false)
        nicheDropdown.setText(resources.getStringArray(R.array.prompt_interests_niche_entries).first(), false)
        orientationDropdown.setText(resources.getStringArray(R.array.prompt_interests_orientation_entries).last(), false) // "不愿透露"
        valuesDropdown.setText(resources.getStringArray(R.array.prompt_interests_values_entries).first(), false)
        dietaryDropdown.setText(resources.getStringArray(R.array.health_dietary_restrictions_entries).first(), false)
        sleepDropdown.setText(resources.getStringArray(R.array.health_sleep_pattern_entries).last(), false)
    }

    fun collectProfileData(currentProfile: PromptProfile): PromptProfile {
        // 检查所有必需的视图是否已初始化
        if (!::genderDropdown.isInitialized ||
            !::ageGroupDropdown.isInitialized ||
            !::occupationDropdown.isInitialized ||
            !::educationDropdown.isInitialized ||
            !::entertainmentDropdown.isInitialized ||
            !::shoppingDropdown.isInitialized ||
            !::nicheDropdown.isInitialized ||
            !::diagnosedDropdown.isInitialized) {
            android.util.Log.w("PersonalizationFragment", "Views not initialized yet, returning original profile")
            return currentProfile
        }

        try {
            // 从年代段转换为出生日期
            val dateOfBirth = when (ageGroupDropdown.text.toString()) {
                "90后 (1990-1999)" -> "1995-01-01" // 使用年代中点作为代表
                "80后 (1980-1989)" -> "1985-01-01"
                "70后 (1970-1979)" -> "1975-01-01"
                "60后 (1960-1969)" -> "1965-01-01"
                "未设置" -> "未设置"
                else -> "未设置"
            }
            
            // 收集健康信息
            val healthInfo = diagnosedDropdown.text.toString().let {
                if (it == "无上述疾病") "未设置" else it
            }
            
            // 收集兴趣爱好
            val interests = listOf(
                entertainmentDropdown.text.toString(),
                shoppingDropdown.text.toString(),
                nicheDropdown.text.toString()
            ).filter { it.isNotBlank() && it != "未设置" }
            
            return currentProfile.copy(
                gender = genderDropdown.text.toString(),
                dateOfBirth = dateOfBirth,
                occupation = occupationDropdown.text.toString(),
                education = educationDropdown.text.toString(),
                interests = interests,
                healthInfo = healthInfo
            )
        } catch (e: Exception) {
            android.util.Log.e("PersonalizationFragment", "Error collecting profile data", e)
            return currentProfile
        }
    }
} 