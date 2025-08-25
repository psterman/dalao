package com.example.aifloatingball

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.ViewCompat
import com.example.aifloatingball.model.AppCategory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 分类拖拽排序助手
 */
class CategoryDragHelper(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "category_order"
        private const val KEY_CATEGORY_ORDER = "category_order"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 获取分类排序
     */
    fun getCategoryOrder(): List<AppCategory> {
        val json = prefs.getString(KEY_CATEGORY_ORDER, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val categoryNames: List<String> = gson.fromJson(json, type)
                categoryNames.mapNotNull { name ->
                    try {
                        AppCategory.valueOf(name)
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                getDefaultCategoryOrder()
            }
        } else {
            getDefaultCategoryOrder()
        }
    }
    
    /**
     * 保存分类排序
     */
    fun saveCategoryOrder(categories: List<AppCategory>) {
        val categoryNames = categories.map { it.name }
        val json = gson.toJson(categoryNames)
        prefs.edit().putString(KEY_CATEGORY_ORDER, json).apply()
    }
    
    /**
     * 获取默认分类排序
     */
    private fun getDefaultCategoryOrder(): List<AppCategory> {
        return listOf(
            AppCategory.CUSTOM,
            AppCategory.ALL,
            AppCategory.AI,
            AppCategory.SHOPPING,
            AppCategory.SOCIAL,
            AppCategory.VIDEO,
            AppCategory.MUSIC,
            AppCategory.LIFESTYLE,
            AppCategory.MAPS,
            AppCategory.BROWSER,
            AppCategory.FINANCE,
            AppCategory.TRAVEL,
            AppCategory.JOBS,
            AppCategory.EDUCATION,
            AppCategory.NEWS
        )
    }
    
    /**
     * 重新排列分类按钮
     */
    fun reorderCategoryButtons(
        container: LinearLayout,
        categoryButtons: Map<AppCategory, LinearLayout?>,
        categoryOrder: List<AppCategory>
    ) {
        // 移除所有子视图
        container.removeAllViews()
        
        // 按照新顺序添加分类按钮
        categoryOrder.forEach { category ->
            categoryButtons[category]?.let { button ->
                // 确保按钮没有父视图
                (button.parent as? ViewGroup)?.removeView(button)
                container.addView(button)
            }
        }
    }
    
    /**
     * 设置拖拽监听器
     */
    fun setupDragListeners(
        container: LinearLayout,
        categoryButtons: Map<AppCategory, LinearLayout?>,
        onOrderChanged: (List<AppCategory>) -> Unit
    ) {
        categoryButtons.forEach { (category, button) ->
            button?.setOnLongClickListener { view ->
                startDrag(view, container, categoryButtons, onOrderChanged)
                true
            }
        }
    }
    
    /**
     * 开始拖拽
     */
    private fun startDrag(
        dragView: View,
        container: LinearLayout,
        categoryButtons: Map<AppCategory, LinearLayout?>,
        onOrderChanged: (List<AppCategory>) -> Unit
    ) {
        // 找到被拖拽的分类
        val draggedCategory = categoryButtons.entries.find { it.value == dragView }?.key

        if (draggedCategory != null) {
            // 开始拖拽动画
            startDragAnimation(dragView) {
                // 创建拖拽阴影
                val shadowBuilder = View.DragShadowBuilder(dragView)

                // 开始拖拽
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    dragView.startDragAndDrop(null, shadowBuilder, draggedCategory, 0)
                } else {
                    @Suppress("DEPRECATION")
                    dragView.startDrag(null, shadowBuilder, draggedCategory, 0)
                }

                // 设置拖拽监听器
                setupDropListener(container, categoryButtons, onOrderChanged, dragView)
            }
        }
    }

    /**
     * 开始拖拽动画
     */
    private fun startDragAnimation(dragView: View, onAnimationEnd: () -> Unit) {
        // Material Design 拖拽开始动画
        val scaleUpX = ObjectAnimator.ofFloat(dragView, "scaleX", 1.0f, 1.1f)
        val scaleUpY = ObjectAnimator.ofFloat(dragView, "scaleY", 1.0f, 1.1f)
        val elevationUp = ObjectAnimator.ofFloat(dragView, "elevation", 0f, 8f)
        val alphaDown = ObjectAnimator.ofFloat(dragView, "alpha", 1.0f, 0.8f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleUpX, scaleUpY, elevationUp, alphaDown)
            duration = 150
            interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
        }

        animatorSet.doOnEnd {
            onAnimationEnd()
        }

        animatorSet.start()
    }
    
    /**
     * 设置放置监听器
     */
    private fun setupDropListener(
        container: LinearLayout,
        categoryButtons: Map<AppCategory, LinearLayout?>,
        onOrderChanged: (List<AppCategory>) -> Unit,
        dragView: View
    ) {
        container.setOnDragListener { _, event ->
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> {
                    // 拖拽开始时，为其他视图添加淡出效果
                    animateOtherViews(container, dragView, 0.6f)
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                    // 拖拽进入时的动画
                    animateContainerHighlight(container, true)
                    true
                }
                android.view.DragEvent.ACTION_DRAG_EXITED -> {
                    // 拖拽离开时的动画
                    animateContainerHighlight(container, false)
                    true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    val draggedCategory = event.localState as? AppCategory
                    if (draggedCategory != null) {
                        handleDrop(draggedCategory, event.x, event.y, container, categoryButtons, onOrderChanged, dragView)
                    }
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    // 拖拽结束时恢复所有视图
                    endDragAnimation(container, dragView)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 为其他视图添加动画效果
     */
    private fun animateOtherViews(container: LinearLayout, dragView: View, alpha: Float) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child != dragView) {
                ObjectAnimator.ofFloat(child, "alpha", child.alpha, alpha).apply {
                    duration = 200
                    start()
                }
            }
        }
    }

    /**
     * 容器高亮动画
     */
    private fun animateContainerHighlight(container: LinearLayout, highlight: Boolean) {
        val targetAlpha = if (highlight) 0.8f else 1.0f
        ObjectAnimator.ofFloat(container, "alpha", container.alpha, targetAlpha).apply {
            duration = 150
            start()
        }
    }

    /**
     * 结束拖拽动画
     */
    private fun endDragAnimation(container: LinearLayout, dragView: View) {
        // 恢复拖拽视图的原始状态
        val scaleDownX = ObjectAnimator.ofFloat(dragView, "scaleX", dragView.scaleX, 1.0f)
        val scaleDownY = ObjectAnimator.ofFloat(dragView, "scaleY", dragView.scaleY, 1.0f)
        val elevationDown = ObjectAnimator.ofFloat(dragView, "elevation", dragView.elevation, 0f)
        val alphaUp = ObjectAnimator.ofFloat(dragView, "alpha", dragView.alpha, 1.0f)

        val dragViewAnimator = AnimatorSet().apply {
            playTogether(scaleDownX, scaleDownY, elevationDown, alphaUp)
            duration = 200
            interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
        }

        // 恢复其他视图的透明度
        animateOtherViews(container, dragView, 1.0f)

        // 恢复容器状态
        animateContainerHighlight(container, false)

        dragViewAnimator.start()
    }
    
    /**
     * 处理放置
     */
    private fun handleDrop(
        draggedCategory: AppCategory,
        x: Float,
        y: Float,
        container: LinearLayout,
        categoryButtons: Map<AppCategory, LinearLayout?>,
        onOrderChanged: (List<AppCategory>) -> Unit,
        dragView: View
    ) {
        // 找到放置位置
        val dropIndex = findDropIndex(y, container)

        // 获取当前排序
        val currentOrder = getCategoryOrder().toMutableList()

        // 移除被拖拽的分类
        currentOrder.remove(draggedCategory)

        // 插入到新位置
        val insertIndex = dropIndex.coerceIn(0, currentOrder.size)
        currentOrder.add(insertIndex, draggedCategory)

        // 保存新排序
        saveCategoryOrder(currentOrder)

        // 使用动画重新排列按钮
        animateReorderButtons(container, categoryButtons, currentOrder) {
            // 通知排序改变
            onOrderChanged(currentOrder)
        }
    }

    /**
     * 动画重新排列按钮
     */
    private fun animateReorderButtons(
        container: LinearLayout,
        categoryButtons: Map<AppCategory, LinearLayout?>,
        newOrder: List<AppCategory>,
        onComplete: () -> Unit
    ) {
        // 先淡出所有按钮
        val fadeOutAnimators = mutableListOf<ObjectAnimator>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val fadeOut = ObjectAnimator.ofFloat(child, "alpha", child.alpha, 0f)
            fadeOutAnimators.add(fadeOut)
        }

        val fadeOutSet = AnimatorSet().apply {
            playTogether(fadeOutAnimators as Collection<android.animation.Animator>)
            duration = 150
        }

        fadeOutSet.doOnEnd {
            // 重新排列按钮
            reorderCategoryButtons(container, categoryButtons, newOrder)

            // 淡入所有按钮
            val fadeInAnimators = mutableListOf<ObjectAnimator>()
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                child.alpha = 0f
                val fadeIn = ObjectAnimator.ofFloat(child, "alpha", 0f, 1f)
                fadeInAnimators.add(fadeIn)
            }

            val fadeInSet = AnimatorSet().apply {
                playTogether(fadeInAnimators as Collection<android.animation.Animator>)
                duration = 200
                interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            }

            fadeInSet.doOnEnd {
                onComplete()
            }

            fadeInSet.start()
        }

        fadeOutSet.start()
    }
    
    /**
     * 找到放置位置索引
     */
    private fun findDropIndex(y: Float, container: LinearLayout): Int {
        var dropIndex = 0
        var accumulatedHeight = 0f
        
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            accumulatedHeight += child.height
            if (y < accumulatedHeight) {
                dropIndex = i
                break
            }
            dropIndex = i + 1
        }
        
        return dropIndex
    }
}
