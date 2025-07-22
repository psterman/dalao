package com.example.aifloatingball.search

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.view.View
import com.example.aifloatingball.R
import com.example.aifloatingball.model.MenuItem

class SearchEngineSelector(private val context: Context) {
    private var popupWindow: PopupWindow? = null
    
    fun show(
        anchor: View,
        engines: List<MenuItem>,
        currentEngine: MenuItem?,
        onEngineSelected: (MenuItem) -> Unit
    ) {
        // 创建一个自定义布局
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
            setBackgroundResource(R.drawable.dialog_background)
        }

        // 添加标题
        TextView(context).apply {
            text = "选择搜索引擎"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 24)
            layout.addView(this)
        }

        // 创建搜索引擎列表
        engines.forEach { engine ->
            TextView(context).apply {
                text = engine.name
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(24, 16, 24, 16)
                background = ColorDrawable(Color.TRANSPARENT)

                // 高亮当前选中的搜索引擎
                if (engine == currentEngine) {
                    setBackgroundResource(R.drawable.selected_item_background)
                }

                setOnClickListener {
                    onEngineSelected(engine)
                    popupWindow?.dismiss()
                }

                layout.addView(this)
            }
        }

        // 创建并显示 PopupWindow
        popupWindow = PopupWindow(
            layout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 10f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            
            // 显示在锚点下方
            showAsDropDown(anchor, 0, 0, Gravity.START)
        }
    }
    
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
} 