package com.example.aifloatingball.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.databinding.ActivityOnboardingBinding
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var viewPager: ViewPager2

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

        viewPager.adapter = adapter
        TabLayoutMediator(binding.dotsIndicator, viewPager) { tab, position -> }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateButtonState(position, adapter.itemCount)
            }
        })

        binding.nextButton.setOnClickListener {
            if (viewPager.currentItem < adapter.itemCount - 1) {
                viewPager.currentItem += 1
            } else {
                finishOnboarding()
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
                // 最后一页
                binding.nextButton.text = "开始体验"
                binding.nextButton.setIconResource(R.drawable.ic_rocket)
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

    private fun finishOnboarding() {
        // Set a flag to indicate that onboarding is complete
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("onboarding_complete", true)
            apply()
        }

        // Navigate to the main activity
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
} 