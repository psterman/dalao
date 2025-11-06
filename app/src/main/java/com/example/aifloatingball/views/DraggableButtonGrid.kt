package com.example.aifloatingball.views

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 可拖动的按钮网格布局
 * 支持拖动换位置、添加候补按钮、隐藏按钮
 */
class DraggableButtonGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DraggableButtonGrid"
        private const val PREFS_NAME = "draggable_button_grid"
        private const val KEY_BUTTON_ORDER = "button_order"
        private const val KEY_VISIBLE_BUTTONS = "visible_buttons"
        private const val COLUMN_COUNT = 3 // 每行3个按钮
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ButtonAdapter
    private var _itemTouchHelper: ItemTouchHelper? = null
    
    // 所有可用按钮（包括候补按钮）
    private val allButtons = mutableListOf<ButtonItem>()
    
    // 当前显示的按钮
    private val visibleButtons = mutableListOf<ButtonItem>()
    
    // 按钮点击监听器
    private var onButtonClickListener: ((ButtonType) -> Unit)? = null
    
    // 按钮数据类
    data class ButtonItem(
        val type: ButtonType,
        val iconResId: Int,
        val label: String,
        var isVisible: Boolean = true
    )
    
    // 按钮类型枚举
    enum class ButtonType {
        NEW_TAB,        // 新建标签页
        GESTURE,        // 手势指南
        DOWNLOAD,       // 下载管理
        HISTORY,        // 历史记录（候补）
        BOOKMARKS,      // 收藏夹（候补）
        SETTINGS,       // 设置（候补）
        SHARE,          // 分享（候补）
        SCAN            // 扫码（候补）
    }

    init {
        // 延迟初始化，等待视图完全加载
    }
    
    override fun onFinishInflate() {
        super.onFinishInflate()
        setupView()
        loadButtonConfig()
    }

    private fun setupView() {
        try {
            // 创建RecyclerView
            recyclerView = RecyclerView(context).apply {
                layoutManager = GridLayoutManager(context, COLUMN_COUNT)
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
            }
            
            addView(recyclerView)
            
            // 初始化按钮数据
            initializeButtons()
            
            // 创建适配器
            adapter = ButtonAdapter(visibleButtons) { buttonType ->
                onButtonClickListener?.invoke(buttonType)
            }
            recyclerView.adapter = adapter
            
            // 设置拖动手势
            setupDragAndDrop()
        } catch (e: Exception) {
            Log.e(TAG, "设置视图失败", e)
            throw e
        }
    }
    
    /**
     * 初始化按钮数据
     */
    private fun initializeButtons() {
        allButtons.clear()
        allButtons.addAll(listOf(
            ButtonItem(ButtonType.NEW_TAB, R.drawable.ic_add, "新建", true),
            ButtonItem(ButtonType.GESTURE, R.drawable.ic_hand, "手势", true),
            ButtonItem(ButtonType.DOWNLOAD, R.drawable.ic_download, "下载", true),
            ButtonItem(ButtonType.HISTORY, R.drawable.ic_history, "历史", false),
            ButtonItem(ButtonType.BOOKMARKS, android.R.drawable.star_big_on, "收藏", false),
            ButtonItem(ButtonType.SETTINGS, R.drawable.ic_settings, "设置", false),
            ButtonItem(ButtonType.SHARE, android.R.drawable.ic_menu_share, "分享", false),
            ButtonItem(ButtonType.SCAN, android.R.drawable.ic_menu_camera, "扫码", false)
        ))
        
        updateVisibleButtons()
    }
    
    /**
     * 更新可见按钮列表
     */
    private fun updateVisibleButtons() {
        visibleButtons.clear()
        visibleButtons.addAll(allButtons.filter { it.isVisible })
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }
    
    /**
     * 设置拖动手势
     */
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                
                // 交换位置
                val item = visibleButtons.removeAt(fromPosition)
                visibleButtons.add(toPosition, item)
                
                adapter.notifyItemMoved(fromPosition, toPosition)
                saveButtonConfig()
                
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }
            
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }
            
            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        }
        
        _itemTouchHelper = ItemTouchHelper(callback)
        _itemTouchHelper?.attachToRecyclerView(recyclerView)
    }
    
    /**
     * 设置按钮点击监听器
     */
    fun setOnButtonClickListener(listener: (ButtonType) -> Unit) {
        onButtonClickListener = listener
    }
    
    /**
     * 显示/隐藏按钮
     */
    fun setButtonVisible(buttonType: ButtonType, visible: Boolean) {
        val button = allButtons.find { it.type == buttonType }
        button?.isVisible = visible
        updateVisibleButtons()
        saveButtonConfig()
    }
    
    /**
     * 添加候补按钮
     */
    fun addButton(buttonType: ButtonType) {
        val button = allButtons.find { it.type == buttonType }
        button?.isVisible = true
        updateVisibleButtons()
        saveButtonConfig()
    }
    
    /**
     * 移除按钮
     */
    fun removeButton(buttonType: ButtonType) {
        val button = allButtons.find { it.type == buttonType }
        button?.isVisible = false
        updateVisibleButtons()
        saveButtonConfig()
    }
    
    /**
     * 保存按钮配置
     */
    private fun saveButtonConfig() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // 保存按钮顺序
        val order = visibleButtons.map { it.type.name }
        editor.putString(KEY_BUTTON_ORDER, Gson().toJson(order))
        
        // 保存可见按钮
        val visible = allButtons.filter { it.isVisible }.map { it.type.name }
        editor.putString(KEY_VISIBLE_BUTTONS, Gson().toJson(visible))
        
        editor.apply()
        Log.d(TAG, "按钮配置已保存")
    }
    
    /**
     * 加载按钮配置
     */
    private fun loadButtonConfig() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 加载可见按钮
        val visibleJson = prefs.getString(KEY_VISIBLE_BUTTONS, null)
        if (visibleJson != null) {
            val visibleTypes = Gson().fromJson<List<String>>(
                visibleJson,
                object : TypeToken<List<String>>() {}.type
            )
            allButtons.forEach { button ->
                button.isVisible = visibleTypes.contains(button.type.name)
            }
        }
        
        // 加载按钮顺序
        val orderJson = prefs.getString(KEY_BUTTON_ORDER, null)
        if (orderJson != null) {
            val order = Gson().fromJson<List<String>>(
                orderJson,
                object : TypeToken<List<String>>() {}.type
            )
            
            // 按照保存的顺序重新排列可见按钮
            val orderedButtons = mutableListOf<ButtonItem>()
            order.forEach { typeName ->
                val button = allButtons.find { it.type.name == typeName && it.isVisible }
                button?.let { orderedButtons.add(it) }
            }
            
            // 添加未在顺序中的新按钮
            allButtons.filter { it.isVisible && !orderedButtons.contains(it) }
                .forEach { orderedButtons.add(it) }
            
            visibleButtons.clear()
            visibleButtons.addAll(orderedButtons)
        } else {
            updateVisibleButtons()
        }
    }
    
    /**
     * 按钮适配器
     */
    private class ButtonAdapter(
        private val buttons: MutableList<ButtonItem>,
        private val onButtonClick: (ButtonType) -> Unit
    ) : RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {
        
        class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val button: MaterialButton = itemView.findViewById(R.id.button_item)
            val dragHandle: View = itemView.findViewById(R.id.drag_handle)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_draggable_button, parent, false)
            return ButtonViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
            val buttonItem = buttons[position]
            
            holder.button.apply {
                text = buttonItem.label
                setIconResource(buttonItem.iconResId)
                setOnClickListener {
                    onButtonClick(buttonItem.type)
                }
            }
            
            // 长按开始拖动
            holder.itemView.setOnLongClickListener {
                val recyclerView = holder.itemView.parent as? RecyclerView
                val grid = recyclerView?.parent as? DraggableButtonGrid
                grid?.itemTouchHelper?.startDrag(holder)
                true
            }
        }
        
        override fun getItemCount() = buttons.size
    }
    
    // 暴露itemTouchHelper供适配器使用
    internal val itemTouchHelper: ItemTouchHelper?
        get() = _itemTouchHelper
}

