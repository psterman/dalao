package com.example.aifloatingball.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R

/**
 * 聊天界面布局动画管理器
 * 负责处理左右手模式切换时的动画效果
 */
class ChatLayoutAnimationManager(private val context: Context) {
    
    private val animationDuration = 300L
    private val interpolator = DecelerateInterpolator()
    
    /**
     * 执行左右手模式切换动画
     */
    fun animateLayoutModeSwitch(
        recyclerView: RecyclerView,
        inputArea: View,
        fromLeftHanded: Boolean,
        toLeftHanded: Boolean,
        onAnimationComplete: () -> Unit
    ) {
        if (fromLeftHanded == toLeftHanded) {
            onAnimationComplete()
            return
        }
        
        val animatorSet = AnimatorSet()
        val animations = mutableListOf<Animator>()
        
        // 1. 淡出当前布局
        val fadeOut = ObjectAnimator.ofFloat(recyclerView, "alpha", 1f, 0f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
        }
        
        val inputFadeOut = ObjectAnimator.ofFloat(inputArea, "alpha", 1f, 0f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
        }
        
        // 2. 滑动效果
        val slideDirection = if (toLeftHanded) -1f else 1f
        val slideOut = ObjectAnimator.ofFloat(recyclerView, "translationX", 0f, slideDirection * 100f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
        }
        
        val inputSlideOut = ObjectAnimator.ofFloat(inputArea, "translationX", 0f, slideDirection * 100f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
        }
        
        // 3. 淡入新布局
        val fadeIn = ObjectAnimator.ofFloat(recyclerView, "alpha", 0f, 1f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
            startDelay = animationDuration / 2
        }
        
        val inputFadeIn = ObjectAnimator.ofFloat(inputArea, "alpha", 0f, 1f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
            startDelay = animationDuration / 2
        }
        
        // 4. 滑入效果
        val slideIn = ObjectAnimator.ofFloat(recyclerView, "translationX", -slideDirection * 100f, 0f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
            startDelay = animationDuration / 2
        }
        
        val inputSlideIn = ObjectAnimator.ofFloat(inputArea, "translationX", -slideDirection * 100f, 0f).apply {
            duration = animationDuration / 2
            interpolator = this@ChatLayoutAnimationManager.interpolator
            startDelay = animationDuration / 2
        }
        
        animations.addAll(listOf(fadeOut, inputFadeOut, slideOut, inputSlideOut, fadeIn, inputFadeIn, slideIn, inputSlideIn))
        
        animatorSet.playTogether(animations)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 重置所有属性
                recyclerView.alpha = 1f
                recyclerView.translationX = 0f
                inputArea.alpha = 1f
                inputArea.translationX = 0f
                
                onAnimationComplete()
            }
        })
        
        animatorSet.start()
    }
    
    /**
     * 执行消息气泡进入动画
     */
    fun animateMessageBubbleEntry(
        messageView: View,
        isUserMessage: Boolean,
        isLeftHanded: Boolean
    ) {
        // 根据消息类型和左右手模式确定动画方向
        val fromDirection = when {
            isUserMessage && !isLeftHanded -> 1f  // 右手模式用户消息从右侧进入
            isUserMessage && isLeftHanded -> -1f  // 左手模式用户消息从左侧进入
            !isUserMessage && !isLeftHanded -> -1f // 右手模式AI消息从左侧进入
            else -> 1f // 左手模式AI消息从右侧进入
        }
        
        messageView.alpha = 0f
        messageView.translationX = fromDirection * 200f
        messageView.scaleX = 0.8f
        messageView.scaleY = 0.8f
        
        val animatorSet = AnimatorSet()
        val animations = listOf(
            ObjectAnimator.ofFloat(messageView, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(messageView, "translationX", fromDirection * 200f, 0f),
            ObjectAnimator.ofFloat(messageView, "scaleX", 0.8f, 1f),
            ObjectAnimator.ofFloat(messageView, "scaleY", 0.8f, 1f)
        )
        
        animations.forEach { animator ->
            animator.duration = 400L
            animator.interpolator = DecelerateInterpolator()
        }
        
        animatorSet.playTogether(animations)
        animatorSet.start()
    }
    
    /**
     * 执行输入区域按钮切换动画
     */
    fun animateInputButtonSwitch(
        hideView: View,
        showView: View,
        onSwitchComplete: () -> Unit
    ) {
        val animatorSet = AnimatorSet()
        
        // 隐藏当前按钮
        val hideAnimator = ObjectAnimator.ofFloat(hideView, "alpha", 1f, 0f).apply {
            duration = 150L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hideView.visibility = View.GONE
                }
            })
        }
        
        // 显示新按钮
        val showAnimator = ObjectAnimator.ofFloat(showView, "alpha", 0f, 1f).apply {
            duration = 150L
            startDelay = 150L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    showView.visibility = View.VISIBLE
                    showView.alpha = 0f
                }
                override fun onAnimationEnd(animation: Animator) {
                    onSwitchComplete()
                }
            })
        }
        
        animatorSet.playTogether(hideAnimator, showAnimator)
        animatorSet.start()
    }
    
    /**
     * 执行功能按钮区域展开/收起动画
     */
    fun animateFunctionButtonsToggle(
        functionArea: View,
        isExpanding: Boolean,
        onAnimationComplete: () -> Unit
    ) {
        val targetHeight = if (isExpanding) {
            // 测量目标高度
            functionArea.measure(
                View.MeasureSpec.makeMeasureSpec(functionArea.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            functionArea.measuredHeight
        } else {
            0
        }
        
        val currentHeight = functionArea.height
        
        val heightAnimator = ObjectAnimator.ofInt(currentHeight, targetHeight).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val height = animation.animatedValue as Int
                val layoutParams = functionArea.layoutParams
                layoutParams.height = height
                functionArea.layoutParams = layoutParams
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (isExpanding) {
                        functionArea.visibility = View.VISIBLE
                    }
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!isExpanding) {
                        functionArea.visibility = View.GONE
                    }
                    onAnimationComplete()
                }
            })
        }
        
        val alphaAnimator = ObjectAnimator.ofFloat(
            functionArea, 
            "alpha", 
            if (isExpanding) 0f else 1f, 
            if (isExpanding) 1f else 0f
        ).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
        }
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(heightAnimator, alphaAnimator)
        animatorSet.start()
    }
    
    /**
     * 执行主题切换动画
     */
    fun animateThemeSwitch(
        rootView: View,
        onAnimationComplete: () -> Unit
    ) {
        val fadeOut = ObjectAnimator.ofFloat(rootView, "alpha", 1f, 0.7f).apply {
            duration = 200L
        }
        
        val fadeIn = ObjectAnimator.ofFloat(rootView, "alpha", 0.7f, 1f).apply {
            duration = 200L
            startDelay = 100L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onAnimationComplete()
                }
            })
        }
        
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(fadeOut, fadeIn)
        animatorSet.start()
    }
}
