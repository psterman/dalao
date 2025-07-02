package com.example.aifloatingball.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.databinding.ActivityOnboardingBinding
import com.example.aifloatingball.service.FloatingWindowService
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var viewPager: ViewPager2
    private var currentPermissionStep = 0
    
    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已开启！", Toast.LENGTH_SHORT).show()
            checkOtherPermissions()
        } else {
            Toast.makeText(this, "悬浮窗权限是必需的，请在设置中开启", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置进入动画
        setupEnterAnimations()

        viewPager = binding.viewPager

        val adapter = OnboardingViewPagerAdapter(this)
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "🎯 第一步：启动智能悬浮球",
                "onboarding_step_1.json"
            )
        )
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "👤 第二步：设定您的专属身份",
                "onboarding_step_2.json"
            )
        )
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "🤖 第三步：选择AI智能助手",
                "onboarding_step_3.json"
            )
        )
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "💬 第四步：开始智能对话",
                "onboarding_step_4.json"
            )
        )
        // 添加权限引导步骤
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "🔐 第五步：开启必要权限",
                "onboarding_step_5.json", // 使用专门的权限动画
                isPermissionStep = true
            )
        )

        viewPager.adapter = adapter
        TabLayoutMediator(binding.dotsIndicator, viewPager) { tab, position -> }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonState(position, adapter.itemCount)
            }
        })

        binding.nextButton.setOnClickListener {
            val currentPosition = viewPager.currentItem
            val totalPages = (viewPager.adapter as OnboardingViewPagerAdapter).itemCount
            
            if (currentPosition < totalPages - 1) {
                viewPager.currentItem += 1
            } else {
                // 最后一页，开始权限检查流程
                startPermissionFlow()
            }
        }

        binding.skipButton.setOnClickListener {
            finishOnboarding()
        }
        
        // 初始化按钮状态
        updateButtonState(0, adapter.itemCount)
    }

    private fun setupEnterAnimations() {
        // ViewPager淡入动画
        binding.viewPager.alpha = 0f
        binding.viewPager.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(200)
            .start()

        // 指示器从下方滑入
        binding.dotsIndicator.translationY = 100f
        binding.dotsIndicator.alpha = 0f
        binding.dotsIndicator.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(400)
            .start()

        // 按钮容器从下方滑入
        binding.buttonContainer.translationY = 150f
        binding.buttonContainer.alpha = 0f
        binding.buttonContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(700)
            .setStartDelay(600)
            .start()
    }

    private fun updateButtonState(position: Int, totalPages: Int) {
        when (position) {
            totalPages - 1 -> {
                // 权限页面
                binding.nextButton.text = "开启权限"
                binding.nextButton.setIconResource(R.drawable.ic_rocket)
                binding.skipButton.text = "稍后设置"
            }
            totalPages - 2 -> {
                // 倒数第二页
                binding.nextButton.text = "授权设置"
                binding.nextButton.setIconResource(R.drawable.ic_arrow_forward)
                binding.skipButton.text = "跳过"
            }
            else -> {
                // 其他页面
                binding.nextButton.text = "下一步"
                binding.nextButton.setIconResource(R.drawable.ic_arrow_forward)
                binding.skipButton.text = "跳过"
            }
        }
        
        // 添加页面切换动画效果
        binding.nextButton.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                binding.nextButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
    
    private fun startPermissionFlow() {
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            checkOtherPermissions()
        }
    }
    
    private fun requestOverlayPermission() {
        Toast.makeText(this, "请在接下来的设置页面中，允许应用显示在其他应用上层", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }
    
    private fun checkOtherPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        when {
            permissionsToRequest.isEmpty() -> {
                // 所有权限都已授予
                Toast.makeText(this, "所有权限设置完成！", Toast.LENGTH_SHORT).show()
                finishOnboardingWithPermissions()
            }
            else -> {
                // 请求缺少的权限
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toTypedArray(),
                    PERMISSIONS_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val grantedPermissions = mutableListOf<String>()
            val deniedPermissions = mutableListOf<String>()
            
            for (i in permissions.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(permissions[i])
                } else {
                    deniedPermissions.add(permissions[i])
                }
            }
            
            if (deniedPermissions.isNotEmpty()) {
                val message = when {
                    deniedPermissions.contains(Manifest.permission.RECORD_AUDIO) -> 
                        "语音功能需要录音权限，您可以稍后在设置中开启"
                    else -> "部分功能可能无法使用，您可以稍后在设置中开启相关权限"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "权限设置完成！", Toast.LENGTH_SHORT).show()
            }
            
            finishOnboardingWithPermissions()
        }
    }

    private fun finishOnboarding() {
        // Set a flag to indicate that onboarding is complete
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("onboarding_complete", true)
            apply()
        }

        // Navigate to the main activity
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun finishOnboardingWithPermissions() {
        // Set a flag to indicate that onboarding is complete
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("onboarding_complete", true)
            apply()
        }

        // 启动对应的服务
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val displayMode = settingsManager.getDisplayMode()
            
            when (displayMode) {
                "floating_ball" -> {
                    val serviceIntent = Intent(this, FloatingWindowService::class.java)
                    ContextCompat.startForegroundService(this, serviceIntent)
                }
                // 可以添加其他显示模式的服务启动
            }
        } catch (e: Exception) {
            // 如果服务启动失败，不影响引导完成
        }

        // Navigate to the main activity
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 2
    }
} 