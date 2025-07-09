package com.example.aifloatingball.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.aifloatingball.model.PromptProfile

/**
 * ViewModel用于在AI指令中心的多个Fragment之间共享PromptProfile数据
 */
class MasterPromptViewModel : ViewModel() {
    
    private val _selectedProfile = MutableLiveData<PromptProfile>()
    val selectedProfile: LiveData<PromptProfile> = _selectedProfile
    
    /**
     * 选择一个配置文件进行编辑
     */
    fun selectProfile(profile: PromptProfile) {
        _selectedProfile.value = profile
    }
    
    /**
     * 更新当前选中的配置文件
     */
    fun updateProfile(profile: PromptProfile) {
        _selectedProfile.value = profile
    }
} 