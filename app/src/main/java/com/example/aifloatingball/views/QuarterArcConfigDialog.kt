package com.example.aifloatingball.views

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager

/**
 * 四分之一圆弧操作栏配置对话框
 */
class QuarterArcConfigDialog : DialogFragment() {

    private var operationBar: QuarterArcOperationBar? = null
    private var onConfigChangedListener: OnConfigChangedListener? = null
    private var settingsManager: SettingsManager? = null
    
    interface OnConfigChangedListener {
        fun onConfigChanged()
    }
    
    companion object {
        fun newInstance(
            operationBar: QuarterArcOperationBar,
            settingsManager: SettingsManager
        ): QuarterArcConfigDialog {
            return QuarterArcConfigDialog().apply {
                this.operationBar = operationBar
                this.settingsManager = settingsManager
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_quarter_arc_config, null)
        
        setupViews(view)
        
        return AlertDialog.Builder(context)
            .setTitle("圆弧操作栏配置")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                onConfigChangedListener?.onConfigChanged()
            }
            .setNegativeButton("取消", null)
            .create()
    }
    
    private fun setupViews(view: View) {
        val operationBar = this.operationBar ?: return

        // 预设模式选择
        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_layout_mode)
        val customSettingsLayout = view.findViewById<LinearLayout>(R.id.layout_custom_settings)

        // 自定义设置控件
        val sizeSeekBar = view.findViewById<SeekBar>(R.id.seekbar_arc_size)
        val sizeValueText = view.findViewById<TextView>(R.id.text_arc_size_value)
        val buttonRadiusSeekBar = view.findViewById<SeekBar>(R.id.seekbar_button_radius)
        val buttonRadiusValueText = view.findViewById<TextView>(R.id.text_button_radius_value)

        // 初始化当前值
        val currentRadius = operationBar.getArcRadius()
        val currentOffset = operationBar.getButtonRadiusOffset()
        val buttonCount = operationBar.getButtonConfigs().size

        // 根据当前设置选择预设模式
        val currentMode = determineCurrentMode(currentRadius, currentOffset, buttonCount)
        when (currentMode) {
            "compact" -> view.findViewById<RadioButton>(R.id.radio_compact).isChecked = true
            "normal" -> view.findViewById<RadioButton>(R.id.radio_normal).isChecked = true
            "spacious" -> view.findViewById<RadioButton>(R.id.radio_spacious).isChecked = true
            else -> view.findViewById<RadioButton>(R.id.radio_custom).isChecked = true
        }

        // 预设模式切换监听
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_compact -> {
                    applyPresetMode("compact", operationBar)
                    customSettingsLayout.visibility = View.GONE
                }
                R.id.radio_normal -> {
                    applyPresetMode("normal", operationBar)
                    customSettingsLayout.visibility = View.GONE
                }
                R.id.radio_spacious -> {
                    applyPresetMode("spacious", operationBar)
                    customSettingsLayout.visibility = View.GONE
                }
                R.id.radio_custom -> {
                    customSettingsLayout.visibility = View.VISIBLE
                }
            }
        }

        // 初始显示状态
        customSettingsLayout.visibility = if (currentMode == "custom") View.VISIBLE else View.GONE

        // 圆弧大小调整
        sizeSeekBar.progress = ((currentRadius - 80f) / (200f - 80f) * 100).toInt()
        sizeValueText.text = "${currentRadius.toInt()}dp"

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    view.findViewById<RadioButton>(R.id.radio_custom).isChecked = true
                    val radius = 80f + (progress / 100f) * (200f - 80f)
                    operationBar.setArcRadius(radius)
                    sizeValueText.text = "${radius.toInt()}dp"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 按钮半径调整
        buttonRadiusSeekBar.progress = ((currentOffset + 30f) / 60f * 100).toInt()
        buttonRadiusValueText.text = "${currentOffset.toInt()}dp"

        buttonRadiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    view.findViewById<RadioButton>(R.id.radio_custom).isChecked = true
                    val offset = -30f + (progress / 100f) * 60f
                    operationBar.setButtonRadiusOffset(offset)
                    buttonRadiusValueText.text = "${offset.toInt()}dp"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 左手模式开关
        val leftHandedSwitch = view.findViewById<Switch>(R.id.switch_left_handed)

        // 从设置管理器获取当前状态
        leftHandedSwitch.isChecked = settingsManager?.isLeftHandedModeEnabled() ?: false

        leftHandedSwitch.setOnCheckedChangeListener { _, isChecked ->
            operationBar.setLeftHandedMode(isChecked)
            // 保存到设置管理器
            settingsManager?.setLeftHandedMode(isChecked)
        }
        
        // 按钮配置列表
        val buttonRecyclerView = view.findViewById<RecyclerView>(R.id.recyclerview_buttons)
        buttonRecyclerView.layoutManager = LinearLayoutManager(context)
        
        val buttonConfigs = operationBar.getButtonConfigs().toMutableList()
        val adapter = ButtonConfigAdapter(buttonConfigs) { position, config ->
            // 更新按钮配置
            buttonConfigs[position] = config
            operationBar.setButtonConfigs(buttonConfigs)
        }
        buttonRecyclerView.adapter = adapter
        
        // 添加按钮
        val addButtonBtn = view.findViewById<Button>(R.id.btn_add_button)
        addButtonBtn.setOnClickListener {
            showAddButtonDialog { newConfig ->
                buttonConfigs.add(newConfig)
                adapter.notifyItemInserted(buttonConfigs.size - 1)
                operationBar.setButtonConfigs(buttonConfigs)
            }
        }
    }
    
    private fun showAddButtonDialog(onButtonAdded: (QuarterArcOperationBar.ButtonConfig) -> Unit) {
        val context = requireContext()
        val items = arrayOf("刷新", "切换标签", "后退", "撤回关闭", "自定义")
        val icons = arrayOf(
            R.drawable.ic_refresh,
            R.drawable.ic_tab_switch,
            R.drawable.ic_arrow_back,
            R.drawable.ic_undo,
            R.drawable.ic_refresh // 默认图标
        )
        
        AlertDialog.Builder(context)
            .setTitle("选择按钮功能")
            .setItems(items) { _, which ->
                val config = QuarterArcOperationBar.ButtonConfig(
                    icon = icons[which],
                    action = {
                        Toast.makeText(context, "执行${items[which]}操作", Toast.LENGTH_SHORT).show()
                    },
                    description = items[which],
                    isEnabled = true
                )
                onButtonAdded(config)
            }
            .show()
    }
    
    fun setOnConfigChangedListener(listener: OnConfigChangedListener) {
        this.onConfigChangedListener = listener
    }

    private fun determineCurrentMode(radius: Float, offset: Float, buttonCount: Int): String {
        return when {
            radius <= 100f && offset <= -10f -> "compact"
            radius in 100f..140f && offset in -10f..10f -> "normal"
            radius >= 140f && offset >= -5f -> "spacious"
            else -> "custom"
        }
    }

    private fun applyPresetMode(mode: String, operationBar: QuarterArcOperationBar) {
        when (mode) {
            "compact" -> {
                // 紧凑模式：小圆弧，按钮靠近
                operationBar.setArcRadius(80f)
                operationBar.setButtonRadiusOffset(-20f)
            }
            "normal" -> {
                // 标准模式：中等圆弧，标准距离
                operationBar.setArcRadius(100f)
                operationBar.setButtonRadiusOffset(-10f)
            }
            "spacious" -> {
                // 宽松模式：大圆弧，按钮分散
                operationBar.setArcRadius(120f)
                operationBar.setButtonRadiusOffset(0f)
            }
        }
    }
}

/**
 * 按钮配置适配器
 */
class ButtonConfigAdapter(
    private val configs: MutableList<QuarterArcOperationBar.ButtonConfig>,
    private val onConfigChanged: (Int, QuarterArcOperationBar.ButtonConfig) -> Unit
) : RecyclerView.Adapter<ButtonConfigAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: ImageView = view.findViewById(R.id.icon_button)
        val nameText: TextView = view.findViewById(R.id.text_button_name)
        val enabledSwitch: Switch = view.findViewById(R.id.switch_button_enabled)
        val deleteBtn: ImageButton = view.findViewById(R.id.btn_delete_button)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_button_config, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configs[position]
        
        holder.iconView.setImageResource(config.icon)
        holder.nameText.text = config.description
        holder.enabledSwitch.isChecked = config.isEnabled
        
        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            val newConfig = config.copy(isEnabled = isChecked)
            configs[position] = newConfig
            onConfigChanged(position, newConfig)
        }
        
        holder.deleteBtn.setOnClickListener {
            configs.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, configs.size)
        }
    }
    
    override fun getItemCount() = configs.size
}
