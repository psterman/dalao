package com.example.aifloatingball.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.aifloatingball.model.PromptProfile

class SettingsViewModel : ViewModel() {
    private val _selectedProfile = MutableLiveData<PromptProfile>()
    val selectedProfile: LiveData<PromptProfile> = _selectedProfile

    fun selectProfile(profile: PromptProfile) {
        _selectedProfile.value = profile
    }
} 