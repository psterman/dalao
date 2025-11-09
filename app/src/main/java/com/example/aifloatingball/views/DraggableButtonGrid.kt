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
        
        // 按钮ID映射（与HTML中的按钮ID一致）
        private const val BUTTON_ID_NEW_TAB = "new_tab"
        private const val BUTTON_ID_NEW_GROUP = "new_group"
        private const val BUTTON_ID_HISTORY = "history"
        private const val BUTTON_ID_BOOKMARKS = "bookmarks"
        private const val BUTTON_ID_DOWNLOAD = "download"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ButtonAdapter
    private var _itemTouchHelper: ItemTouchHelper? = null
    private var savedButtonOrder: List<String>? = null  // 保存加载的按钮顺序
    
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
        NEW_GROUP,       // 新建标签组
        HISTORY,         // 历史记录
        BOOKMARKS,       // 收藏夹
        DOWNLOAD,        // 下载管理
        GESTURE,         // 手势指南（候补）
        SETTINGS,        // 设置（候补）
        SHARE,           // 分享（候补）
        SCAN             // 扫码（候补）
    }
    
    /**
     * 获取按钮ID（与HTML中的按钮ID一致）
     */
    private fun ButtonType.toButtonId(): String {
        return when (this) {
            ButtonType.NEW_TAB -> BUTTON_ID_NEW_TAB
            ButtonType.NEW_GROUP -> BUTTON_ID_NEW_GROUP
            ButtonType.HISTORY -> BUTTON_ID_HISTORY
            ButtonType.BOOKMARKS -> BUTTON_ID_BOOKMARKS
            ButtonType.DOWNLOAD -> BUTTON_ID_DOWNLOAD
            else -> this.name.lowercase()
        }
    }
    
    /**
     * 从按钮ID获取按钮类型
     */
    private fun String.toButtonType(): ButtonType? {
        return when (this) {
            BUTTON_ID_NEW_TAB -> ButtonType.NEW_TAB
            BUTTON_ID_NEW_GROUP -> ButtonType.NEW_GROUP
            BUTTON_ID_HISTORY -> ButtonType.HISTORY
            BUTTON_ID_BOOKMARKS -> ButtonType.BOOKMARKS
            BUTTON_ID_DOWNLOAD -> ButtonType.DOWNLOAD
            else -> null
        }
    }

    init {
        // 延迟初始化，等待视图完全加载
    }
    
    override fun onFinishInflate() {
        super.onFinishInflate()
        // 先初始化按钮数据（使用默认值）
        initializeButtons()
        // 然后加载保存的配置（会覆盖默认值）
        loadButtonConfig()
        // 最后设置视图（使用加载后的配置）
        setupView()
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
            
            // 创建适配器（显示所有主要按钮，用于设置页面）
            // 注意：适配器显示所有按钮，isVisible属性控制选中状态
            val mainButtons = getAllMainButtons().toMutableList()
            
            // 如果有保存的顺序，按照顺序重新排列
            savedButtonOrder?.let { order ->
                val orderedButtons = mutableListOf<ButtonItem>()
                order.forEach { idOrName ->
                    val buttonType = idOrName.toButtonType()
                    val button = if (buttonType != null) {
                        mainButtons.find { it.type == buttonType }
                    } else {
                        mainButtons.find { it.type.name == idOrName }
                    }
                    button?.let {
                        orderedButtons.add(it)
                        mainButtons.remove(it)
                    }
                }
                // 添加未在顺序中的按钮
                orderedButtons.addAll(mainButtons)
                mainButtons.clear()
                mainButtons.addAll(orderedButtons)
            }
            
            adapter = ButtonAdapter(mainButtons) { buttonType ->
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
            ButtonItem(ButtonType.NEW_TAB, R.drawable.ic_add, "新建标签页", true),
            ButtonItem(ButtonType.NEW_GROUP, R.drawable.ic_add, "新建标签组", true),
            ButtonItem(ButtonType.HISTORY, R.drawable.ic_history, "历史记录", true),
            ButtonItem(ButtonType.BOOKMARKS, android.R.drawable.star_big_on, "收藏夹", true),
            ButtonItem(ButtonType.DOWNLOAD, R.drawable.ic_download, "下载", true),
            ButtonItem(ButtonType.GESTURE, R.drawable.ic_hand, "手势", false),
            ButtonItem(ButtonType.SETTINGS, R.drawable.ic_settings, "设置", false),
            ButtonItem(ButtonType.SHARE, android.R.drawable.ic_menu_share, "分享", false),
            ButtonItem(ButtonType.SCAN, android.R.drawable.ic_menu_camera, "扫码", false)
        ))
        
        updateVisibleButtons()
    }
    
    /**
     * 更新可见按钮列表（用于首页显示）
     */
    private fun updateVisibleButtons() {
        visibleButtons.clear()
        visibleButtons.addAll(allButtons.filter { it.isVisible })
        // 注意：适配器显示所有按钮，这里只更新可见按钮列表用于首页
    }
    
    /**
     * 获取所有主要按钮（用于设置页面显示）
     */
    private fun getAllMainButtons(): List<ButtonItem> {
        return allButtons.filter { 
            it.type == ButtonType.NEW_TAB ||
            it.type == ButtonType.NEW_GROUP ||
            it.type == ButtonType.HISTORY ||
            it.type == ButtonType.BOOKMARKS ||
            it.type == ButtonType.DOWNLOAD
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
                
                // 交换位置（在适配器的按钮列表中）
                val adapterButtons = (recyclerView.adapter as? ButtonAdapter)?.buttons
                if (adapterButtons != null && fromPosition < adapterButtons.size && toPosition < adapterButtons.size) {
                    val item = adapterButtons.removeAt(fromPosition)
                    adapterButtons.add(toPosition, item)
                    adapter.notifyItemMoved(fromPosition, toPosition)
                    // 延迟保存，避免频繁保存
                    recyclerView.post {
                        val grid = recyclerView.parent as? DraggableButtonGrid
                        grid?.saveButtonConfig()
                    }
                }
                
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
        // 通知适配器更新UI
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }
    
    /**
     * 切换按钮可见性（选中/取消选中）
     */
    fun toggleButtonVisibility(buttonType: ButtonType) {
        val button = allButtons.find { it.type == buttonType }
        button?.let {
            it.isVisible = !it.isVisible
            updateVisibleButtons()
            saveButtonConfig()
            // 通知适配器更新UI（只更新当前按钮项）
            if (::adapter.isInitialized) {
                val adapterButtons = (adapter as ButtonAdapter).buttons
                val position = adapterButtons.indexOfFirst { it.type == buttonType }
                if (position >= 0) {
                    adapter.notifyItemChanged(position)
                } else {
                    adapter.notifyDataSetChanged()
                }
            }
            Log.d(TAG, "按钮 ${it.label} 可见性已切换为: ${it.isVisible}")
            
            // 发送广播通知首页刷新按钮显示状态
            try {
                val refreshIntent = android.content.Intent("com.example.aifloatingball.ACTION_REFRESH_HOME_BUTTONS")
                refreshIntent.setPackage(context.packageName)
                context.sendBroadcast(refreshIntent)
                Log.d(TAG, "已发送刷新首页按钮的广播")
            } catch (e: Exception) {
                Log.e(TAG, "发送刷新广播失败", e)
            }
        }
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
     * 刷新按钮配置（从SharedPreferences重新加载）
     */
    fun refreshButtonConfig() {
        loadButtonConfig()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }
    
    /**
     * 保存按钮配置
     */
    private fun saveButtonConfig() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // 保存按钮顺序（使用按钮ID，基于适配器中的按钮顺序）
        val order = if (::adapter.isInitialized) {
            (adapter as ButtonAdapter).buttons.map { it.type.toButtonId() }
        } else {
            getAllMainButtons().map { it.type.toButtonId() }
        }
        editor.putString(KEY_BUTTON_ORDER, Gson().toJson(order))
        
        // 保存可见按钮（使用按钮ID）
        val visible = allButtons.filter { it.isVisible }.map { it.type.toButtonId() }
        editor.putString(KEY_VISIBLE_BUTTONS, Gson().toJson(visible))
        
        // 同时保存到与SimpleModeActivity相同的SharedPreferences，确保首页和设置页面同步
        val settingsManager = com.example.aifloatingball.SettingsManager.getInstance(context)
        // 标记为已初始化
        settingsManager.putBoolean("home_buttons_initialized", true)
        allButtons.forEach { button ->
            val buttonId = button.type.toButtonId()
            val key = "home_button_${buttonId}_visible"
            // 使用putBoolean方法保存（内部使用commit，确保立即保存）
            // 明确保存每个按钮的状态，即使是不显示的按钮也要保存false
            settingsManager.putBoolean(key, button.isVisible)
        }
        
        // 保存按钮顺序到SimpleModeActivity使用的SharedPreferences
        settingsManager.putString("home_button_order", Gson().toJson(order))
        
        // 立即提交本地SharedPreferences（使用commit确保立即保存）
        editor.commit()
        
        Log.d(TAG, "按钮配置已保存，顺序: $order, 可见: $visible")
        
        // 通知首页刷新（如果首页正在显示）
        try {
            // 通过广播通知首页刷新按钮显示
            val refreshIntent = android.content.Intent("com.example.aifloatingball.ACTION_REFRESH_HOME_BUTTONS")
            refreshIntent.setPackage(context.packageName) // 设置包名确保广播正确发送
            context.sendBroadcast(refreshIntent)
            Log.d(TAG, "已发送刷新首页按钮的广播")
            
            // 延迟一小段时间后再次发送，确保广播被接收
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    context.sendBroadcast(refreshIntent)
                    Log.d(TAG, "已再次发送刷新首页按钮的广播（延迟）")
                } catch (e: Exception) {
                    Log.e(TAG, "延迟发送广播失败", e)
                }
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "通知首页刷新失败", e)
        }
    }
    
    /**
     * 加载按钮配置
     */
    private fun loadButtonConfig() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsManager = com.example.aifloatingball.SettingsManager.getInstance(context)
        
        // 检查是否已经初始化过按钮配置
        val isInitialized = settingsManager.getBoolean("home_buttons_initialized", false)
        
        // 优先从SimpleModeActivity使用的SharedPreferences加载可见性（确保与首页同步）
        allButtons.forEach { button ->
            val buttonId = button.type.toButtonId()
            val key = "home_button_${buttonId}_visible"
            
            // 如果已经初始化过，必须明确检查key是否存在
            // 如果key不存在，说明用户从未设置过，应该保持初始化时的默认值（不改变）
            if (isInitialized) {
                // 已经初始化过，检查key是否存在
                if (settingsManager.getSharedPreferences().contains(key)) {
                    // key存在，使用保存的值
                    button.isVisible = settingsManager.getBoolean(key, button.isVisible)
                } else {
                    // key不存在，保持当前值（初始化时的默认值）
                    // 不改变 button.isVisible
                }
            } else {
                // 首次初始化，检查key是否存在
                // 如果key不存在，使用初始化时的默认值（主要按钮为true，其他为false）
                // 如果key存在（可能是从其他地方设置的），使用保存的值
                if (settingsManager.getSharedPreferences().contains(key)) {
                    // key存在，使用保存的值
                    button.isVisible = settingsManager.getBoolean(key, button.isVisible)
                } else {
                    // key不存在，保持初始化时的默认值（主要按钮为true，其他为false）
                    // button.isVisible 已经在 initializeButtons() 中设置好了，不需要改变
                }
            }
            Log.d(TAG, "加载按钮 ${button.label} (${buttonId}) 可见性: ${button.isVisible} (已初始化: $isInitialized)")
        }
        
        // 标记为已初始化（如果还没有），并保存初始状态
        if (!isInitialized) {
            settingsManager.putBoolean("home_buttons_initialized", true)
            // 首次初始化时，保存所有按钮的初始状态，确保后续加载时能正确读取
            allButtons.forEach { button ->
                val buttonId = button.type.toButtonId()
                val key = "home_button_${buttonId}_visible"
                settingsManager.putBoolean(key, button.isVisible)
            }
            Log.d(TAG, "首次初始化，已保存所有按钮的初始状态")
        }
        
        // 加载按钮顺序（用于设置页面显示）
        val orderJson = prefs.getString(KEY_BUTTON_ORDER, null)
        if (orderJson != null) {
            try {
                savedButtonOrder = Gson().fromJson<List<String>>(
                    orderJson,
                    object : TypeToken<List<String>>() {}.type
                )
                Log.d(TAG, "加载按钮顺序: $savedButtonOrder")
            } catch (e: Exception) {
                Log.e(TAG, "加载按钮顺序失败", e)
                savedButtonOrder = null
            }
        } else {
            savedButtonOrder = null
        }
        
        // 更新可见按钮列表（用于首页显示）
        updateVisibleButtons()
    }
    
    /**
     * 按钮适配器
     */
    private class ButtonAdapter(
        val buttons: MutableList<ButtonItem>,  // 改为public以便外部访问
        private val onButtonClick: (ButtonType) -> Unit
    ) : RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {
        
        class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val button: MaterialButton = itemView.findViewById(R.id.button_item)
            val dragHandle: View = itemView.findViewById(R.id.drag_handle)
            val selectedIndicator: View = itemView.findViewById(R.id.selected_indicator)
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
                
                // 不显示图标
                icon = null
                setIconResource(0)
                
                // 设置文字颜色，确保可见
                setTextColor(context.getColor(com.example.aifloatingball.R.color.simple_mode_text_primary_light))
                
                // 单击切换显示状态（选中/取消选中）
                setOnClickListener {
                    // 切换按钮可见性（选中/取消选中）
                    val grid = (holder.itemView.parent as? RecyclerView)?.parent as? DraggableButtonGrid
                    grid?.toggleButtonVisibility(buttonItem.type)
                }
                
                // 按钮本身不处理长按，让itemView处理拖动
                setOnLongClickListener(null)
            }
            
            // 隐藏选中指示器（绿色横线）
            holder.selectedIndicator.visibility = View.GONE
            
            // 更新按钮样式以反映选中状态
            if (buttonItem.isVisible) {
                // 选中状态：显示边框和背景色
                holder.button.apply {
                    strokeWidth = 2
                    strokeColor = android.content.res.ColorStateList.valueOf(
                        context.getColor(com.example.aifloatingball.R.color.simple_mode_accent_light)
                    )
                    setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                            context.getColor(com.example.aifloatingball.R.color.simple_mode_green_100_light)
                        )
                    )
                }
            } else {
                // 未选中状态：只有边框，透明背景
                holder.button.apply {
                    strokeWidth = 1
                    strokeColor = android.content.res.ColorStateList.valueOf(
                        context.getColor(com.example.aifloatingball.R.color.simple_mode_text_secondary_light)
                    )
                    setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }
            
            // 长按开始拖动
            holder.itemView.setOnLongClickListener {
                val recyclerView = holder.itemView.parent as? RecyclerView
                val grid = recyclerView?.parent as? DraggableButtonGrid
                grid?.itemTouchHelper?.startDrag(holder)
                true // 消费长按事件，开始拖动
            }
        }
        
        override fun getItemCount() = buttons.size
    }
    
    // 暴露itemTouchHelper供适配器使用
    internal val itemTouchHelper: ItemTouchHelper?
        get() = _itemTouchHelper
}

