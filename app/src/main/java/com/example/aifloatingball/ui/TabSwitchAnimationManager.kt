package com.example.aifloatingball.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout

/**
 * 标签切换动画管理器
 * 为AI分类标签切换提供平滑的动画效果
 */
class TabSwitchAnimationManager {
    
    companion object {
        private const val ANIMATION_DURATION = 300L
        private const val FADE_DURATION = 150L
    }
    
    private val interpolator = AccelerateDecelerateInterpolator()
    private var currentAnimation: AnimatorSet? = null
    
    /**
     * 执行标签切换动画
     */
    fun animateTabSwitch(
        recyclerView: RecyclerView,
        tabLayout: TabLayout,
        fromPosition: Int,
        toPosition: Int,
        onAnimationComplete: () -> Unit
    ) {
        // 取消当前动画
        currentAnimation?.cancel()
        
        // 如果是相同标签，直接完成
        if (fromPosition == toPosition) {
            onAnimationComplete()
            return
        }
        
        val animatorSet = AnimatorSet()
        val animations = mutableListOf<Animator>()
        
        // 1. 淡出当前内容
        val fadeOut = ObjectAnimator.ofFloat(recyclerView, "alpha", 1f, 0f).apply {
            duration = FADE_DURATION
            interpolator = this@TabSwitchAnimationManager.interpolator
        }
        
        // 2. 轻微缩放效果
        val scaleDown = ObjectAnimator.ofFloat(recyclerView, "scaleY", 1f, 0.95f).apply {
            duration = FADE_DURATION
            interpolator = this@TabSwitchAnimationManager.interpolator
        }
        
        // 3. 淡入新内容
        val fadeIn = ObjectAnimator.ofFloat(recyclerView, "alpha", 0f, 1f).apply {
            duration = FADE_DURATION
            startDelay = FADE_DURATION
            interpolator = this@TabSwitchAnimationManager.interpolator
        }
        
        // 4. 恢复缩放
        val scaleUp = ObjectAnimator.ofFloat(recyclerView, "scaleY", 0.95f, 1f).apply {
            duration = FADE_DURATION
            startDelay = FADE_DURATION
            interpolator = this@TabSwitchAnimationManager.interpolator
        }
        
        // 5. 标签指示器动画（如果需要）
        val tabIndicatorAnimation = createTabIndicatorAnimation(tabLayout, fromPosition, toPosition)
        
        animations.addAll(listOf(fadeOut, scaleDown, fadeIn, scaleUp))
        if (tabIndicatorAnimation != null) {
            animations.add(tabIndicatorAnimation)
        }
        
        animatorSet.playTogether(animations)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                // 在动画开始时切换内容
                recyclerView.postDelayed({
                    onAnimationComplete()
                }, FADE_DURATION)
            }
            
            override fun onAnimationEnd(animation: Animator) {
                // 重置所有属性
                recyclerView.alpha = 1f
                recyclerView.scaleY = 1f
                currentAnimation = null
            }
            
            override fun onAnimationCancel(animation: Animator) {
                // 重置所有属性
                recyclerView.alpha = 1f
                recyclerView.scaleY = 1f
                currentAnimation = null
            }
        })
        
        currentAnimation = animatorSet
        animatorSet.start()
    }
    
    /**
     * 创建标签指示器动画
     */
    private fun createTabIndicatorAnimation(
        tabLayout: TabLayout,
        fromPosition: Int,
        toPosition: Int
    ): Animator? {
        return try {
            // 获取标签视图
            val fromTab = tabLayout.getTabAt(fromPosition)
            val toTab = tabLayout.getTabAt(toPosition)
            
            if (fromTab != null && toTab != null) {
                // 创建一个简单的透明度动画来增强视觉效果
                val tabAnimation = ObjectAnimator.ofFloat(tabLayout, "alpha", 0.8f, 1f).apply {
                    duration = ANIMATION_DURATION
                    interpolator = this@TabSwitchAnimationManager.interpolator
                }
                tabAnimation
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 执行快速切换动画（用于相邻标签）
     */
    fun animateQuickSwitch(
        recyclerView: RecyclerView,
        direction: SwitchDirection,
        onAnimationComplete: () -> Unit
    ) {
        // 取消当前动画
        currentAnimation?.cancel()
        
        val animatorSet = AnimatorSet()
        val slideDistance = 50f
        
        // 根据方向确定滑动
        val startX = when (direction) {
            SwitchDirection.LEFT_TO_RIGHT -> -slideDistance
            SwitchDirection.RIGHT_TO_LEFT -> slideDistance
        }
        
        // 1. 滑入动画
        val slideIn = ObjectAnimator.ofFloat(recyclerView, "translationX", startX, 0f).apply {
            duration = ANIMATION_DURATION
            interpolator = this@TabSwitchAnimationManager.interpolator
        }
        
        // 2. 淡入动画
        val fadeIn = ObjectAnimator.ofFloat(recyclerView, "alpha", 0.7f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = this@TabSwitchAnimationManager.interpolator
        }
        
        animatorSet.playTogether(slideIn, fadeIn)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                // 设置初始状态
                recyclerView.translationX = startX
                recyclerView.alpha = 0.7f
                onAnimationComplete()
            }
            
            override fun onAnimationEnd(animation: Animator) {
                // 重置属性
                recyclerView.translationX = 0f
                recyclerView.alpha = 1f
                currentAnimation = null
            }
            
            override fun onAnimationCancel(animation: Animator) {
                // 重置属性
                recyclerView.translationX = 0f
                recyclerView.alpha = 1f
                currentAnimation = null
            }
        })
        
        currentAnimation = animatorSet
        animatorSet.start()
    }
    
    /**
     * 取消当前动画
     */
    fun cancelCurrentAnimation() {
        currentAnimation?.cancel()
        currentAnimation = null
    }
    
    /**
     * 切换方向枚举
     */
    enum class SwitchDirection {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT
    }
}
