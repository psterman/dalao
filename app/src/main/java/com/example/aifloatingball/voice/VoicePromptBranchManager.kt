package com.example.aifloatingball.voice

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.google.android.material.card.MaterialCardView
import android.os.Handler
import android.os.Looper
import android.graphics.Rect

class VoicePromptBranchManager(private val context: Context, private val listener: BranchViewListener) {
    private val TAG = "VoicePromptBranchManager"

    interface BranchViewListener {
        fun onBranchViewHidden()
    }

    enum class InteractionMode {
        CLICK,
        DRAG
    }

    var interactionMode: InteractionMode = InteractionMode.CLICK

    private var branchRootView: View? = null
    private var targetTextView: TextView? = null
    private var currentLevel = 0
    private var currentPath = mutableListOf<BranchOption>()
    private var backButton: ImageButton? = null
    private var currentOptions: List<BranchOption> = emptyList()

    // 拖动状态变量
    private var isDragging = false
    private var dragLineView: DragLineView? = null
    private val anchorStack = mutableListOf<Pair<Float, Float>>() // 锚点栈
    private val containerLocation = IntArray(2)

    // 新增：用于映射视图和选项数据
    private val viewOptionMap = mutableMapOf<View, BranchOption>()
    
    // 新增：用于处理悬停事件
    private val hoverHandler = Handler(Looper.getMainLooper())
    private var hoverRunnable: Runnable? = null
    private var hoveredCard: MaterialCardView? = null

    data class BranchOption(
        val title: String,
        val prompt: String = "",
        val children: List<BranchOption> = emptyList(),
        val isDirectory: Boolean = false
    )

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

    fun showBranchView(rootView: ViewGroup, targetView: TextView, micCenterX: Float, micCenterY: Float) {
        this.targetTextView = targetView
        resetBranchState()
        val inflater = android.view.LayoutInflater.from(context)
        branchRootView = inflater.inflate(R.layout.voice_prompt_branch_layout, rootView, false)

        branchRootView?.apply {
            isClickable = true
            isFocusable = true
            
            alpha = 0f
            animate().alpha(1f).setDuration(200).start()

            post {
                val container = findViewById<ConstraintLayout>(R.id.branch_level_2) ?: return@post
                container.getLocationOnScreen(containerLocation)

                // 初始化锚点栈，第一个锚点是麦克风按钮
                anchorStack.clear()
                val initialAnchorX = micCenterX - containerLocation[0]
                val initialAnchorY = micCenterY - containerLocation[1]
                anchorStack.add(initialAnchorX to initialAnchorY)
            }
        }

        setupBackButton()
        currentOptions = promptTree
        showCurrentLevelOptions()
        rootView.addView(branchRootView)
    }

    private fun setupBackButton() {
        backButton = branchRootView?.findViewById(R.id.branch_back_button)
        backButton?.apply {
            visibility = if (currentLevel > 0) View.VISIBLE else View.GONE
            setOnClickListener {
                if (canNavigateBack()) {
                    navigateBack()
                } else {
                    hideBranchView()
                }
            }
        }
    }

    private fun showCurrentLevelOptions() {
        val container = branchRootView?.findViewById<ConstraintLayout>(R.id.branch_level_2) ?: return
        // 清理旧视图和映射，但保留DragLineView
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is MaterialCardView) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { container.removeView(it) }
        viewOptionMap.clear()
        
        // 延迟布局，确保容器已经测量完毕
        container.post {
            currentOptions.forEachIndexed { index, option ->
                createOptionView(option, index, container)
            }
        }
    }

    private fun createOptionView(option: BranchOption, index: Int, container: ConstraintLayout?) {
        val cardView = MaterialCardView(context).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (interactionMode == InteractionMode.DRAG) {
                    // 拖动模式采用环形布局
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

                    val angle = 360f / currentOptions.size * index
                    // 使用已测量好的容器宽度
                    val radius = (container?.width ?: 0) / 3
                    
                    circleConstraint = ConstraintLayout.LayoutParams.PARENT_ID
                    circleAngle = angle
                    circleRadius = radius
                } else {
                    // 点击模式维持原布局
                    when (index % 3) {
                        0 -> { topToTop = ConstraintLayout.LayoutParams.PARENT_ID; startToStart = ConstraintLayout.LayoutParams.PARENT_ID; topMargin = 100 + (index / 3) * 150; marginStart = 50 }
                        1 -> { topToTop = ConstraintLayout.LayoutParams.PARENT_ID; startToStart = ConstraintLayout.LayoutParams.PARENT_ID; endToEnd = ConstraintLayout.LayoutParams.PARENT_ID; topMargin = 100 + (index / 3) * 150 }
                        2 -> { topToTop = ConstraintLayout.LayoutParams.PARENT_ID; endToEnd = ConstraintLayout.LayoutParams.PARENT_ID; topMargin = 100 + (index / 3) * 150; marginEnd = 50 }
                    }
                }
            }
            radius = context.resources.getDimension(R.dimen.mtrl_card_corner_radius_large)
            cardElevation = context.resources.getDimension(R.dimen.mtrl_card_elevation_large)
            
            // 关键：在拖动模式下，卡片本身不处理点击，让事件穿透到父视图
            isClickable = interactionMode != InteractionMode.DRAG
            isFocusable = interactionMode != InteractionMode.DRAG

            if (option.isDirectory) {
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.mtrl_blue_50))
            } else {
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.mtrl_green_50))
            }

            // 点击模式下才设置单独的点击监听
            if (interactionMode == InteractionMode.CLICK) {
                setOnClickListener { handleOptionClick(option) }
            }
        }

        val textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            text = option.title
            textSize = 14f
            setPadding(24, 16, 24, 16)
            setTextColor(ContextCompat.getColor(context, if (option.isDirectory) R.color.directory_text else R.color.leaf_text))
        }

        cardView.addView(textView)
        container?.addView(cardView)
        // 映射视图和数据
        viewOptionMap[cardView] = option

        // 入场动画
        cardView.alpha = 0f
        cardView.scaleX = 0.5f
        cardView.scaleY = 0.5f
        cardView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setStartDelay((index * 50).toLong())
            .start()
    }

    fun handleDragEvent(event: MotionEvent) {
        if (interactionMode != InteractionMode.DRAG || anchorStack.isEmpty()) return
        val container = branchRootView?.findViewById<ConstraintLayout>(R.id.branch_level_2) ?: return

        val (anchorX, anchorY) = anchorStack.last() // 始终从当前层级的锚点画线
        val containerX = event.rawX - containerLocation[0]
        val containerY = event.rawY - containerLocation[1]

        val viewUnderPoint = findViewAt(container, containerX, containerY)

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    isDragging = true
                    Log.d(TAG, "DRAG: Dragging started on first move.")
                    if (dragLineView == null) {
                        dragLineView = DragLineView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                        container.addView(dragLineView, 0)
                    }
                    dragLineView?.visibility = View.VISIBLE
                }

                dragLineView?.updateLine(anchorX, anchorY, containerX, containerY)

                val cardUnderPoint = viewUnderPoint as? MaterialCardView
                if (cardUnderPoint != hoveredCard) {
                    hoveredCard?.let { animateCardForDrag(it, false) }
                    hoverHandler.removeCallbacks(hoverRunnable ?: Runnable {})
                    
                    hoveredCard = cardUnderPoint
                    
                    val optionUnderPoint = hoveredCard?.let { viewOptionMap[it] }
                    if (hoveredCard != null && optionUnderPoint?.isDirectory == true) {
                        animateCardForDrag(hoveredCard!!, true)
                        hoverRunnable = Runnable {
                            val currentViewUnderPoint = findViewAt(container, containerX, containerY)
                            if (currentViewUnderPoint == hoveredCard) {
                                openSubDirectory(optionUnderPoint, hoveredCard!!)
                            } else {
                                hoveredCard?.let { animateCardForDrag(it, false) }
                            }
                        }
                        hoverHandler.postDelayed(hoverRunnable!!, 400)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val finalOption = viewUnderPoint?.let { viewOptionMap[it] }
                    if (finalOption != null && !finalOption.isDirectory) {
                        val fullPrompt = (currentPath.map { it.title } + finalOption.title).joinToString(" > ") + ": " + finalOption.prompt
                        applyPromptWithAnimation(fullPrompt)
                    } else {
                        hideBranchView()
                    }
                }
                resetDragState()
            }
        }
    }

    private fun findViewAt(parent: ViewGroup, x: Float, y: Float): View? {
        for (i in (parent.childCount - 1) downTo 0) {
            val child = parent.getChildAt(i)
            if (child is MaterialCardView) {
                val rect = Rect()
                child.getHitRect(rect)
                if (rect.contains(x.toInt(), y.toInt())) {
                    return child
                }
            }
        }
        return null
    }

    fun hideBranchView() {
        if (!isBranchViewVisible) return
        
        branchRootView?.let { view ->
            view.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    (view.parent as? ViewGroup)?.removeView(view)
                    branchRootView = null
                    resetBranchState()
                    listener.onBranchViewHidden()
                }
                .start()
        }
    }

    private fun resetDragState() {
        isDragging = false
        dragLineView?.visibility = View.GONE
        hoverHandler.removeCallbacks(hoverRunnable ?: Runnable {})
        hoveredCard?.let { animateCardForDrag(it, false) }
        hoveredCard = null
        hoverRunnable = null
    }

    private fun handleOptionClick(option: BranchOption) {
        try {
            if (option.isDirectory) {
                val view = viewOptionMap.entries.find { it.value == option }?.key
                if (view != null) {
                    openSubDirectory(option, view)
                }
            } else {
                applyPromptWithAnimation(option.prompt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling option click: ${e.message}")
            showErrorToast("无法处理选项，请重试")
        }
    }
    
    private fun openSubDirectory(option: BranchOption, parentCard: View) {
        val container = branchRootView?.findViewById<ConstraintLayout>(R.id.branch_level_2) ?: return
        
        // 计算并推入新的锚点
        val newAnchorX = parentCard.left + parentCard.width / 2f
        val newAnchorY = parentCard.top + parentCard.height / 2f
        anchorStack.add(newAnchorX to newAnchorY)

        // 播放过渡动画
        animateDirectoryTransition {
            currentLevel++
            currentPath.add(option)
            currentOptions = option.children
            showCurrentLevelOptions()
            setupBackButton()
        }
    }

    fun navigateBack() {
        if (!canNavigateBack()) return
        
        // 弹出锚点
        if (anchorStack.size > 1) {
            anchorStack.removeAt(anchorStack.lastIndex)
        }

        try {
            animateDirectoryTransition {
                currentLevel--
                currentPath.removeAt(currentPath.lastIndex)
                currentOptions = if (currentPath.isEmpty()) promptTree else currentPath.last().children
                showCurrentLevelOptions()
                setupBackButton()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating back: ${e.message}")
            showErrorToast("返回失败，请重试")
        }
    }

    fun canNavigateBack(): Boolean = currentLevel > 0

    val isBranchViewVisible: Boolean
        get() = branchRootView != null
    
    private fun resetBranchState() {
        currentLevel = 0
        currentPath.clear()
        currentOptions = promptTree
        anchorStack.clear() // 彻底清空锚点
        isDragging = false
        dragLineView?.visibility = View.GONE
        dragLineView = null // 释放旧的View引用
        hoverHandler.removeCallbacks(hoverRunnable ?: Runnable {})
        hoveredCard?.let { animateCardForDrag(it, false) }
        hoveredCard = null
        hoverRunnable = null
    }

    private fun applyPromptWithAnimation(prompt: String) {
        targetTextView?.let { textView ->
            val animatorSet = AnimatorSet()
            val scaleX = ObjectAnimator.ofFloat(textView, "scaleX", 1f, 1.2f, 1f)
            val scaleY = ObjectAnimator.ofFloat(textView, "scaleY", 1f, 1.2f, 1f)
            
            animatorSet.playTogether(scaleX, scaleY)
            animatorSet.duration = 300
            animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val currentText = textView.text.toString().substringBefore("\n\n") // 只保留用户自己输入的部分
                    val newText = if (currentText.isEmpty()) prompt else "$currentText\n\n$prompt"
                    textView.text = newText
                    hideBranchView() // 应用后自动隐藏
                }
            })
            animatorSet.start()
        }
    }

    private fun animateDirectoryTransition(onTransitionComplete: () -> Unit) {
        val container = branchRootView?.findViewById<ConstraintLayout>(R.id.branch_level_2)
        container?.animate()
                ?.alpha(0f)
                ?.setDuration(150)
                ?.withEndAction {
                    onTransitionComplete()
                    // 在内容更新后，再播放淡入动画
                    container.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                ?.start()
    }
    
    private fun animateToSubDirectory(card: MaterialCardView, onAnimationEnd: () -> Unit) {
        val container = card.parent as? ViewGroup
        val animSet = AnimatorSet()
        
        // 其他卡片飞出
        container?.let { c ->
            for (i in 0 until c.childCount) {
                val childView = c.getChildAt(i)
                if (childView is MaterialCardView && childView != card) {
                    val flyOut = ObjectAnimator.ofFloat(childView, "alpha", 1f, 0f).setDuration(200)
                    animSet.play(flyOut)
                }
            }
        }

        // 被选中的卡片放大并消失
        val scaleX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.5f)
        val scaleY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.5f)
        val alpha = ObjectAnimator.ofFloat(card, "alpha", 1f, 0f)
        
        val selectionAnim = AnimatorSet()
        selectionAnim.playTogether(scaleX, scaleY, alpha)
        selectionAnim.duration = 300
        
        animSet.play(selectionAnim)
        animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onAnimationEnd()
            }
        })
        animSet.start()
    }

    private fun animateCardForDrag(card: MaterialCardView, isDragging: Boolean) {
        val scale = if (isDragging) 1.2f else 1f
        val elevation = if (isDragging) context.resources.getDimension(R.dimen.mtrl_card_elevation_large) * 2
                        else context.resources.getDimension(R.dimen.mtrl_card_elevation_large)
        
        card.animate().scaleX(scale).scaleY(scale).setDuration(200).start()
        ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, elevation).setDuration(200).start()
    }

    private fun resetCardAnimation(card: MaterialCardView) {
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, context.resources.getDimension(R.dimen.mtrl_card_elevation_large)).setDuration(200).start()
    }
    
    private fun showErrorToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private inner class DragLineView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.colorAccent) // 使用主题强调色
            strokeWidth = 8f // 加粗线条
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND // 圆头画笔
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f) // 虚线效果
            isAntiAlias = true
        }
        private val path = Path()
        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f

        fun updateLine(startX: Float, startY: Float, endX: Float, endY: Float) {
            this.startX = startX; this.startY = startY; this.endX = endX; this.endY = endY
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (startX == 0f && startY == 0f) return // 避免画线到(0,0)
            
            path.reset()
            path.moveTo(startX, startY)
            // 使用贝塞尔曲线，使线条更平滑
            path.quadTo(startX, endY, endX, endY)
            canvas.drawPath(path, paint)
        }
    }
} 
