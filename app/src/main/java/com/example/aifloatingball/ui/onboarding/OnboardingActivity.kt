package com.example.aifloatingball.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.databinding.ActivityOnboardingBinding
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewPager = binding.viewPager

        val adapter = OnboardingViewPagerAdapter(this)
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "第一步：唤醒灵动岛",
                "onboarding_step_1.json"
            )
        )
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "第二步：选择您的“身份”",
                "onboarding_step_2.json"
            )
        )
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "第三步：选择为您服务的“助手”",
                "onboarding_step_3.json"
            )
        )
        adapter.addFragment(
            OnboardingStepFragment.newInstance(
                "第四步：开始对话",
                "onboarding_step_4.json"
            )
        )

        viewPager.adapter = adapter
        TabLayoutMediator(binding.dotsIndicator, viewPager) { tab, position -> }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == adapter.itemCount - 1) {
                    binding.nextButton.text = "完成"
                } else {
                    binding.nextButton.text = "下一步"
                }
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