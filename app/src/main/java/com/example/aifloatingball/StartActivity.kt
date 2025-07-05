package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.ui.onboarding.OnboardingActivity

class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsManager = SettingsManager.getInstance(this)
        if (!settingsManager.isOnboardingComplete()) {
            // 第一次运行，启动引导页
            startActivity(Intent(this, OnboardingActivity::class.java))
        } else {
            // 非第一次运行，启动主界面
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        finish() // 结束当前Activity，防止用户返回
    }
} 