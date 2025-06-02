package com.example.aifloatingball.ui.floating

import android.widget.HorizontalScrollView
import android.widget.SeekBar

/**
 * 滚动控制器，管理SeekBar和HorizontalScrollView之间的交互
 */
class ScrollController {
    
    /**
     * 设置滚动控制，绑定SeekBar和ScrollView
     */
    fun setupScrollControl(seekBar: SeekBar?, scrollView: HorizontalScrollView?) {
        setupSeekBarListener(seekBar, scrollView)
        setupScrollViewListener(seekBar, scrollView)
    }
    
    /**
     * 设置SeekBar的监听器，当用户拖动SeekBar时滚动视图
     */
    private fun setupSeekBarListener(seekBar: SeekBar?, scrollView: HorizontalScrollView?) {
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    scrollView?.let { view ->
                        val maxScroll = calculateMaxScroll(view)
                        val scrollTo = (maxScroll * (progress.toFloat() / 100)).toInt()
                        view.scrollTo(scrollTo, 0)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    /**
     * 设置ScrollView的监听器，当视图滚动时更新SeekBar进度
     */
    private fun setupScrollViewListener(seekBar: SeekBar?, scrollView: HorizontalScrollView?) {
        scrollView?.viewTreeObserver?.addOnScrollChangedListener {
            scrollView.let { view ->
                val maxScroll = calculateMaxScroll(view)
                if (maxScroll > 0) {
                    val progress = (view.scrollX.toFloat() / maxScroll * 100).toInt()
                    seekBar?.progress = progress
                }
            }
        }
    }
    
    /**
     * 计算最大滚动距离
     */
    private fun calculateMaxScroll(scrollView: HorizontalScrollView): Int {
        return scrollView.getChildAt(0).width - scrollView.width
    }
} 