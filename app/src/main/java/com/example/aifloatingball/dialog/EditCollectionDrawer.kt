package com.example.aifloatingball.dialog

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.example.aifloatingball.R
import com.example.aifloatingball.model.*
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.*

/**
 * 侧滑编辑面板
 * 用于编辑收藏项的所有元数据字段
 */
class EditCollectionDrawer : DialogFragment() {
    
    private var collectionItem: UnifiedCollectionItem? = null
    private var onSaveListener: ((UnifiedCollectionItem) -> Unit)? = null
    
    // UI组件
    private lateinit var titleInput: EditText
    private lateinit var contentInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var locationSpinner: Spinner
    private lateinit var sourceInput: EditText
    private lateinit var tagsChipGroup: ChipGroup
    private lateinit var tagInput: EditText
    private lateinit var priorityGroup: RadioGroup
    private lateinit var completionStatusGroup: RadioGroup
    private lateinit var likeLevelGroup: LinearLayout
    private lateinit var emotionTagGroup: ChipGroup
    private lateinit var encryptSwitch: Switch
    private lateinit var reminderSwitch: Switch
    private lateinit var reminderTimeText: TextView
    
    private var selectedLikeLevel = 0
    private var selectedReminderTime: Long? = null
    
    companion object {
        fun newInstance(item: UnifiedCollectionItem? = null): EditCollectionDrawer {
            return EditCollectionDrawer().apply {
                collectionItem = item
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_edit_collection_drawer, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupSpinners()
        setupListeners()
        loadData()
    }
    
    private fun initViews(view: View) {
        // 标题栏
        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.btn_save).setOnClickListener { saveCollection() }
        
        // 基础信息
        titleInput = view.findViewById(R.id.input_title)
        contentInput = view.findViewById(R.id.input_content)
        typeSpinner = view.findViewById(R.id.spinner_type)
        locationSpinner = view.findViewById(R.id.spinner_location)
        sourceInput = view.findViewById(R.id.input_source)
        
        // 标签
        tagsChipGroup = view.findViewById(R.id.chip_group_tags)
        tagInput = view.findViewById(R.id.input_tag)
        view.findViewById<Button>(R.id.btn_add_tag).setOnClickListener { addTag() }
        
        // 优先级和状态
        priorityGroup = view.findViewById(R.id.radio_group_priority)
        completionStatusGroup = view.findViewById(R.id.radio_group_completion_status)
        
        // 喜欢程度
        likeLevelGroup = view.findViewById(R.id.layout_like_level)
        setupLikeLevelStars()
        
        // 情感标签
        emotionTagGroup = view.findViewById(R.id.chip_group_emotion)
        setupEmotionTags()
        
        // 安全和提醒
        encryptSwitch = view.findViewById(R.id.switch_encrypt)
        reminderSwitch = view.findViewById(R.id.switch_reminder)
        reminderTimeText = view.findViewById(R.id.text_reminder_time)
        
        reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            reminderTimeText.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked && selectedReminderTime == null) {
                showReminderTimePicker()
            }
        }
        
        reminderTimeText.setOnClickListener {
            showReminderTimePicker()
        }
    }
    
    private fun setupSpinners() {
        // 收藏类型
        val types = CollectionType.values().map { it.displayName }
        typeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        
        // 收藏地点
        val locations = listOf(
            "AI对话Tab", "搜索Tab", "软件Tab", "语音Tab",
            "悬浮球", "灵动岛", "读书阅读器", "其他"
        )
        locationSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, locations)
    }
    
    private fun setupListeners() {
        // 标签输入框回车添加标签
        tagInput.setOnEditorActionListener { _, _, _ ->
            addTag()
            true
        }
    }
    
    private fun setupLikeLevelStars() {
        likeLevelGroup.removeAllViews()
        
        for (i in 1..5) {
            val starButton = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.star_off)
                background = null
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }
                setOnClickListener {
                    selectedLikeLevel = i
                    updateLikeLevelStars()
                }
            }
            likeLevelGroup.addView(starButton)
        }
    }
    
    private fun updateLikeLevelStars() {
        for (i in 0 until likeLevelGroup.childCount) {
            val starButton = likeLevelGroup.getChildAt(i) as ImageButton
            if (i < selectedLikeLevel) {
                starButton.setImageResource(android.R.drawable.star_on)
            } else {
                starButton.setImageResource(android.R.drawable.star_off)
            }
        }
    }
    
    private fun setupEmotionTags() {
        emotionTagGroup.removeAllViews()
        
        EmotionTag.values().forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag.displayName
                isCheckable = true
                isChecked = false
            }
            emotionTagGroup.addView(chip)
        }
    }
    
    private fun addTag() {
        val tagText = tagInput.text.toString().trim()
        if (tagText.isNotEmpty() && !hasTag(tagText)) {
            val chip = Chip(requireContext()).apply {
                text = tagText
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    tagsChipGroup.removeView(this)
                }
            }
            tagsChipGroup.addView(chip)
            tagInput.text.clear()
        }
    }
    
    private fun hasTag(tag: String): Boolean {
        for (i in 0 until tagsChipGroup.childCount) {
            val chip = tagsChipGroup.getChildAt(i) as Chip
            if (chip.text.toString() == tag) {
                return true
            }
        }
        return false
    }
    
    private fun showReminderTimePicker() {
        val calendar = Calendar.getInstance()
        if (selectedReminderTime != null) {
            calendar.timeInMillis = selectedReminderTime!!
        }
        
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        selectedReminderTime = calendar.timeInMillis
                        updateReminderTimeText()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    private fun updateReminderTimeText() {
        if (selectedReminderTime != null) {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            reminderTimeText.text = formatter.format(Date(selectedReminderTime!!))
        }
    }
    
    private fun loadData() {
        collectionItem?.let { item ->
            titleInput.setText(item.title)
            contentInput.setText(item.content)
            
            // 设置类型
            val typeIndex = CollectionType.values().indexOfFirst { it == item.collectionType }
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
            
            // 设置地点
            val locationAdapter = locationSpinner.adapter as? ArrayAdapter<String>
            val locationIndex = locationAdapter?.let { adapter ->
                adapter.getPosition(item.sourceLocation)
            } ?: -1
            if (locationIndex >= 0) locationSpinner.setSelection(locationIndex)
            
            sourceInput.setText(item.sourceDetail)
            
            // 设置标签
            item.customTags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        tagsChipGroup.removeView(this)
                    }
                }
                tagsChipGroup.addView(chip)
            }
            
            // 设置优先级
            when (item.priority) {
                Priority.HIGH -> priorityGroup.check(R.id.radio_priority_high)
                Priority.NORMAL -> priorityGroup.check(R.id.radio_priority_normal)
                Priority.LOW -> priorityGroup.check(R.id.radio_priority_low)
            }
            
            // 设置完成状态
            when (item.completionStatus) {
                CompletionStatus.NOT_STARTED -> completionStatusGroup.check(R.id.radio_status_not_started)
                CompletionStatus.IN_PROGRESS -> completionStatusGroup.check(R.id.radio_status_in_progress)
                CompletionStatus.COMPLETED -> completionStatusGroup.check(R.id.radio_status_completed)
            }
            
            // 设置喜欢程度
            selectedLikeLevel = item.likeLevel
            updateLikeLevelStars()
            
            // 设置情感标签
            for (i in 0 until emotionTagGroup.childCount) {
                val view = emotionTagGroup.getChildAt(i)
                if (view is Chip && view.text == item.emotionTag.displayName) {
                    view.isChecked = true
                }
            }
            
            // 设置加密状态
            encryptSwitch.isChecked = item.isEncrypted
            
            // 设置提醒
            selectedReminderTime = item.reminderTime
            reminderSwitch.isChecked = item.reminderTime != null
            if (item.reminderTime != null) {
                updateReminderTimeText()
            }
        }
    }
    
    private fun saveCollection() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "请输入标题", Toast.LENGTH_SHORT).show()
            return
        }
        
        val content = contentInput.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "请输入内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 获取选中的类型
        val selectedType = CollectionType.values()[typeSpinner.selectedItemPosition]
        
        // 获取选中的地点
        val selectedLocation = locationSpinner.selectedItem.toString()
        
        // 获取标签
        val tags = mutableListOf<String>()
        for (i in 0 until tagsChipGroup.childCount) {
            val chip = tagsChipGroup.getChildAt(i) as Chip
            tags.add(chip.text.toString())
        }
        
        // 获取优先级
        val priority = when (priorityGroup.checkedRadioButtonId) {
            R.id.radio_priority_high -> Priority.HIGH
            R.id.radio_priority_low -> Priority.LOW
            else -> Priority.NORMAL
        }
        
        // 获取完成状态
        val completionStatus = when (completionStatusGroup.checkedRadioButtonId) {
            R.id.radio_status_in_progress -> CompletionStatus.IN_PROGRESS
            R.id.radio_status_completed -> CompletionStatus.COMPLETED
            else -> CompletionStatus.NOT_STARTED
        }
        
        // 获取情感标签
        val emotionTag = (0 until emotionTagGroup.childCount)
            .mapNotNull { emotionTagGroup.getChildAt(it) as? Chip }
            .firstOrNull { it.isChecked }
            ?.let { chip ->
                EmotionTag.values().find { it.displayName == chip.text.toString() }
            } ?: EmotionTag.NEUTRAL
        
        // 构建收藏项
        val updatedItem = (collectionItem ?: UnifiedCollectionItem(
            title = title,
            content = content,
            collectionType = selectedType,
            sourceLocation = selectedLocation
        )).copy(
            title = title,
            content = content,
            preview = content.take(200),
            collectionType = selectedType,
            sourceLocation = selectedLocation,
            sourceDetail = sourceInput.text.toString().takeIf { it.isNotBlank() },
            customTags = tags,
            priority = priority,
            completionStatus = completionStatus,
            likeLevel = selectedLikeLevel,
            emotionTag = emotionTag,
            isEncrypted = encryptSwitch.isChecked,
            reminderTime = if (reminderSwitch.isChecked) selectedReminderTime else null,
            modifiedTime = System.currentTimeMillis()
        )
        
        onSaveListener?.invoke(updatedItem)
        dismiss()
    }
    
    fun setOnSaveListener(listener: (UnifiedCollectionItem) -> Unit) {
        onSaveListener = listener
    }
}

