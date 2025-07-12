package com.example.aifloatingball.voice

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SimpleModeActivity
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs
import kotlin.math.atan2

class VoicePromptBranchManager(private val context: Context) {
    private var branchRootView: View? = null
    private var targetTextView: TextView? = null
    private var selectedBranchId = -1
    private var hasBranchSelected = false
    private var currentLevel = 0
    private var currentPath = mutableListOf<BranchOption>()
    private var backButton: ImageButton? = null

    /**
     * 分支选项数据类
     */
    data class BranchOption(
        val title: String,
        val prompt: String = "",
        val children: List<BranchOption> = emptyList(),
        val isDirectory: Boolean = false
    )

    // 定义提示词目录树
    private val promptTree = listOf(
        BranchOption("详细解释", isDirectory = true, children = listOf(
            BranchOption("技术概念", isDirectory = true, children = listOf(
                BranchOption("基础定义", "请详细解释这个技术概念的基本定义和核心要点"),
                BranchOption("应用场景", "请列举这个技术在实际中的具体应用场景和使用案例"),
                BranchOption("优缺点分析", "请分析这个技术的优点和局限性")
            )),
            BranchOption("代码分析", isDirectory = true, children = listOf(
                BranchOption("功能解释", "请解释这段代码的主要功能和实现逻辑"),
                BranchOption("性能分析", "请分析这段代码的性能特点和可能的优化空间"),
                BranchOption("最佳实践", "请说明这种实现是否符合最佳实践，有什么改进空间")
            )),
            BranchOption("问题诊断", isDirectory = true, children = listOf(
                BranchOption("错误分析", "请分析这个错误的可能原因和解决方案"),
                BranchOption("性能问题", "请诊断这个性能问题的原因和优化建议"),
                BranchOption("兼容性", "请说明可能的兼容性问题和解决方案")
            ))
        )),
        BranchOption("代码示例", isDirectory = true, children = listOf(
            BranchOption("基础示例", isDirectory = true, children = listOf(
                BranchOption("简单实现", "请提供一个简单的代码示例来实现这个功能"),
                BranchOption("完整示例", "请提供一个包含错误处理的完整代码示例"),
                BranchOption("测试用例", "请提供相关的单元测试示例")
            )),
            BranchOption("进阶示例", isDirectory = true, children = listOf(
                BranchOption("优化版本", "请提供一个优化后的代码示例，重点关注性能和可维护性"),
                BranchOption("设计模式", "请用设计模式改进这个实现并提供示例代码"),
                BranchOption("最佳实践", "请提供符合最佳实践的代码示例")
            ))
        )),
        BranchOption("分步骤解析", isDirectory = true, children = listOf(
            BranchOption("实现步骤", isDirectory = true, children = listOf(
                BranchOption("环境准备", "请列出实现这个功能需要的准备工作和环境配置"),
                BranchOption("核心步骤", "请详细说明实现这个功能的具体步骤"),
                BranchOption("测试验证", "请说明如何测试和验证实现的正确性")
            )),
            BranchOption("问题解决", isDirectory = true, children = listOf(
                BranchOption("诊断步骤", "请列出诊断这个问题的具体步骤"),
                BranchOption("解决方案", "请提供逐步的解决方案"),
                BranchOption("预防措施", "请说明如何预防类似问题的发生")
            ))
        )),
        BranchOption("思维导图", isDirectory = true, children = listOf(
            BranchOption("概念梳理", isDirectory = true, children = listOf(
                BranchOption("核心概念", "请用思维导图展示这个概念的核心组成部分"),
                BranchOption("关系分析", "请用思维导图分析相关概念之间的关系"),
                BranchOption("应用场景", "请用思维导图展示这个概念的应用场景")
            )),
            BranchOption("方案设计", isDirectory = true, children = listOf(
                BranchOption("架构设计", "请用思维导图展示这个方案的架构设计"),
                BranchOption("实现路径", "请用思维导图展示实现这个功能的可能路径"),
                BranchOption("优化方向", "请用思维导图分析可能的优化方向")
            ))
        ))
    )

    private var currentOptions = promptTree

    /**
     * 显示分支视图
     */
    fun showBranchView(rootView: ViewGroup, targetView: TextView) {
        this.targetTextView = targetView
        
        // 重置状态
        resetBranchState()
        currentLevel = 0
        currentPath.clear()
        currentOptions = promptTree
        
        // 创建分支视图
        val inflater = android.view.LayoutInflater.from(context)
        branchRootView = inflater.inflate(R.layout.voice_prompt_branch_layout, rootView, false)
        
        // 设置返回按钮
        setupBackButton()
        
        // 显示当前层级的选项
        showCurrentLevelOptions()
        
        // 添加到根视图
        rootView.addView(branchRootView)
        
        // 添加显示动画
        branchRootView?.alpha = 0f
        branchRootView?.animate()
            ?.alpha(1f)
            ?.setDuration(200)
            ?.start()
    }

    /**
     * 设置返回按钮
     */
    private fun setupBackButton() {
        backButton = branchRootView?.findViewById(R.id.branch_back_button)
        backButton?.apply {
            visibility = if (currentLevel > 0) View.VISIBLE else View.GONE
            setOnClickListener {
                navigateBack()
            }
        }
    }

    /**
     * 返回上一级
     */
    private fun navigateBack() {
        if (currentLevel > 0) {
            currentLevel--
            currentPath.removeAt(currentPath.lastIndex)
            currentOptions = if (currentPath.isEmpty()) {
                promptTree
            } else {
                var options = promptTree
                for (option in currentPath) {
                    options = options.find { it.title == option.title }?.children ?: promptTree
                }
                options
            }
            showCurrentLevelOptions()
            setupBackButton()
        }
    }

    /**
     * 显示当前层级的选项
     */
    private fun showCurrentLevelOptions() {
        val level2Container = branchRootView?.findViewById<ConstraintLayout>(R.id.branch_level_2)
        level2Container?.removeAllViews()
        
        currentOptions.forEachIndexed { index, option ->
            createOptionView(option, index, level2Container)
        }
    }

    /**
     * 创建选项视图
     */
    private fun createOptionView(option: BranchOption, index: Int, container: ConstraintLayout?) {
        val cardView = MaterialCardView(context).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // 根据索引设置位置
                when (index % 3) {
                    0 -> {
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        topMargin = 100 + (index / 3) * 150
                        marginStart = 50
                    }
                    1 -> {
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        topMargin = 100 + (index / 3) * 150
                    }
                    2 -> {
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        topMargin = 100 + (index / 3) * 150
                        marginEnd = 50
                    }
                }
            }
            radius = context.resources.getDimension(com.google.android.material.R.dimen.mtrl_card_corner_radius)
            cardElevation = context.resources.getDimension(com.google.android.material.R.dimen.mtrl_card_elevation)
            alpha = 0.9f
            
            // 设置目录和叶子节点的不同样式
            if (option.isDirectory) {
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.directory_background))
            } else {
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.leaf_background))
            }
        }

        // 创建文本视图
        val textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            text = option.title
            textSize = 14f
            setPadding(24, 16, 24, 16)
            setTextColor(ContextCompat.getColor(context, if (option.isDirectory) R.color.directory_text else R.color.leaf_text))
        }

        // 添加文本到卡片
        cardView.addView(textView)

        // 设置点击事件
        cardView.setOnClickListener {
            if (option.isDirectory) {
                // 如果是目录，进入下一级
                currentLevel++
                currentPath.add(option)
                currentOptions = option.children
                showCurrentLevelOptions()
                setupBackButton()
            } else {
                // 如果是叶子节点，应用提示并关闭
                applyPromptToInput(option.prompt)
                hideBranchView()
            }
        }

        // 添加到容器
        container?.addView(cardView)

        // 添加显示动画
        cardView.alpha = 0f
        cardView.animate()
            .alpha(1f)
            .setDuration(200)
            .setStartDelay((index * 50).toLong())
            .start()
    }

    /**
     * 应用提示到输入框
     */
    private fun applyPromptToInput(prompt: String) {
        targetTextView?.let {
            val currentText = it.text.toString()
            val newText = if (currentText.isEmpty()) {
                prompt
            } else {
                "$currentText\n\n$prompt"
            }
            it.setText(newText)
        }
    }

    /**
     * 隐藏分支视图
     */
    fun hideBranchView() {
        branchRootView?.let { view ->
            view.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    val parent = view.parent as? ViewGroup
                    parent?.removeView(view)
                    branchRootView = null
                    
                    val activity = context as? SimpleModeActivity
                    activity?.applyBackgroundBlur(false)
                }
                .start()
        }
    }

    /**
     * 重置分支状态
     */
    private fun resetBranchState() {
        selectedBranchId = -1
        hasBranchSelected = false
    }
} 