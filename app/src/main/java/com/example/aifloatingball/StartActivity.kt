package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.ui.onboarding.OnboardingActivity
import com.example.aifloatingball.manager.ModeManager

class StartActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "StartActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsManager = SettingsManager.getInstance(this)

        // Check if onboarding is completed.
        if (!settingsManager.isOnboardingComplete()) {
            Log.d(TAG, "启动引导页面")
            startActivity(Intent(this, OnboardingActivity::class.java))
        } else {
            // 使用ModeManager启动适当的显示模式
            Log.d(TAG, "启动应用主功能")
            startAppWithModeManager()
        }
        finish()
    }

    /**
     * 使用ModeManager启动应用
     */
    private fun startAppWithModeManager() {
        try {
            val currentMode = ModeManager.getCurrentMode(this)
            Log.d(TAG, "当前设置的显示模式: ${currentMode.displayName}")

            // 启动对应的模式
            ModeManager.switchToMode(this, currentMode)

        } catch (e: Exception) {
            Log.e(TAG, "启动模式失败，回退到简易模式", e)
            // 如果启动失败，回退到简易模式
            ModeManager.switchToMode(this, ModeManager.DisplayMode.SIMPLE_MODE)
        }
    }
}