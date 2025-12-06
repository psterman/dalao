package com.example.aifloatingball.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.example.aifloatingball.R
import com.example.aifloatingball.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 搜索面板
 * 支持多条件搜索：文本、标签、分类、时间、收藏类型
 */
class SearchCollectionPanel : DialogFragment() {
    
    private var onSearchListener: ((SearchParams) -> Unit)? = null
    
    // UI组件
    private lateinit var textSearchInput: EditText
    private lateinit var tagInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var timeRangeSpinner: Spinner
    private lateinit var startDateText: TextView
    private lateinit var endDateText: TextView
    private lateinit var prioritySpinner: Spinner
    private lateinit var completionStatusSpinner: Spinner
    private lateinit var encryptSpinner: Spinner
    
    private var selectedStartDate: Long? = null
    private var selectedEndDate: Long? = null
    
    /**
     * 搜索参数数据类
     */
    data class SearchParams(
        val query: String? = null,
        val tags: List<String> = emptyList(),
        val type: CollectionType? = null,
        val timeRange: Pair<Long, Long>? = null,
        val priority: Priority? = null,
        val completionStatus: CompletionStatus? = null,
        val isEncrypted: Boolean? = null
    )
    
    companion object {
        fun newInstance(): SearchCollectionPanel {
            return SearchCollectionPanel()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_search_collection_panel, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupSpinners()
        setupListeners()
    }
    
    private fun initViews(view: View) {
        // 标题栏
        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.btn_search).setOnClickListener { performSearch() }
        view.findViewById<Button>(R.id.btn_reset).setOnClickListener { resetSearch() }
        
        // 搜索输入
        textSearchInput = view.findViewById(R.id.input_text_search)
        tagInput = view.findViewById(R.id.input_tag_search)
        
        // 下拉选择
        typeSpinner = view.findViewById(R.id.spinner_type)
        timeRangeSpinner = view.findViewById(R.id.spinner_time_range)
        prioritySpinner = view.findViewById(R.id.spinner_priority)
        completionStatusSpinner = view.findViewById(R.id.spinner_completion_status)
        encryptSpinner = view.findViewById(R.id.spinner_encrypt)
        
        // 日期选择
        startDateText = view.findViewById(R.id.text_start_date)
        endDateText = view.findViewById(R.id.text_end_date)
        
        startDateText.setOnClickListener { showDatePicker(true) }
        endDateText.setOnClickListener { showDatePicker(false) }
    }
    
    private fun setupSpinners() {
        // 收藏类型（全部 + 各类型）
        val types = listOf("全部") + CollectionType.values().map { it.displayName }
        typeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        
        // 时间范围
        val timeRanges = listOf(
            "全部时间",
            "今天",
            "最近7天",
            "最近30天",
            "最近3个月",
            "自定义"
        )
        timeRangeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, timeRanges)
        timeRangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val showCustom = position == 5 // 自定义
                startDateText.visibility = if (showCustom) View.VISIBLE else View.GONE
                endDateText.visibility = if (showCustom) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // 优先级
        val priorities = listOf("全部", "高", "中", "低")
        prioritySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, priorities)
        
        // 完成状态
        val statuses = listOf("全部") + CompletionStatus.values().map { it.displayName }
        completionStatusSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statuses)
        
        // 加密状态
        val encryptOptions = listOf("全部", "已加密", "未加密")
        encryptSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, encryptOptions)
    }
    
    private fun setupListeners() {
        // 回车搜索
        textSearchInput.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
        
        tagInput.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
    }
    
    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val currentDate = if (isStartDate) selectedStartDate else selectedEndDate
        if (currentDate != null) {
            calendar.timeInMillis = currentDate
        }
        
        android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                if (isStartDate) {
                    selectedStartDate = calendar.timeInMillis
                    updateDateText(startDateText, selectedStartDate)
                } else {
                    selectedEndDate = calendar.timeInMillis
                    updateDateText(endDateText, selectedEndDate)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    private fun updateDateText(textView: TextView, timestamp: Long?) {
        if (timestamp != null) {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            textView.text = formatter.format(Date(timestamp))
        } else {
            textView.text = "选择日期"
        }
    }
    
    private fun performSearch() {
        // 文本搜索
        val query = textSearchInput.text.toString().takeIf { it.isNotBlank() }
        
        // 标签搜索
        val tags = tagInput.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        // 类型筛选
        val type = if (typeSpinner.selectedItemPosition == 0) {
            null
        } else {
            CollectionType.values()[typeSpinner.selectedItemPosition - 1]
        }
        
        // 时间范围
        val timeRange = when (timeRangeSpinner.selectedItemPosition) {
            0 -> null // 全部时间
            1 -> { // 今天
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val end = calendar.timeInMillis - 1
                Pair(start, end)
            }
            2 -> { // 最近7天
                val end = System.currentTimeMillis()
                val start = end - 7 * 24 * 60 * 60 * 1000L
                Pair(start, end)
            }
            3 -> { // 最近30天
                val end = System.currentTimeMillis()
                val start = end - 30 * 24 * 60 * 60 * 1000L
                Pair(start, end)
            }
            4 -> { // 最近3个月
                val end = System.currentTimeMillis()
                val start = end - 90 * 24 * 60 * 60 * 1000L
                Pair(start, end)
            }
            5 -> { // 自定义
                if (selectedStartDate != null && selectedEndDate != null) {
                    Pair(selectedStartDate!!, selectedEndDate!!)
                } else {
                    null
                }
            }
            else -> null
        }
        
        // 优先级
        val priority = when (prioritySpinner.selectedItemPosition) {
            0 -> null
            1 -> Priority.HIGH
            2 -> Priority.NORMAL
            3 -> Priority.LOW
            else -> null
        }
        
        // 完成状态
        val completionStatus = if (completionStatusSpinner.selectedItemPosition == 0) {
            null
        } else {
            CompletionStatus.values()[completionStatusSpinner.selectedItemPosition - 1]
        }
        
        // 加密状态
        val isEncrypted = when (encryptSpinner.selectedItemPosition) {
            0 -> null
            1 -> true
            2 -> false
            else -> null
        }
        
        val params = SearchParams(
            query = query,
            tags = tags,
            type = type,
            timeRange = timeRange,
            priority = priority,
            completionStatus = completionStatus,
            isEncrypted = isEncrypted
        )
        
        onSearchListener?.invoke(params)
        dismiss()
    }
    
    private fun resetSearch() {
        textSearchInput.text.clear()
        tagInput.text.clear()
        typeSpinner.setSelection(0)
        timeRangeSpinner.setSelection(0)
        prioritySpinner.setSelection(0)
        completionStatusSpinner.setSelection(0)
        encryptSpinner.setSelection(0)
        selectedStartDate = null
        selectedEndDate = null
        updateDateText(startDateText, null)
        updateDateText(endDateText, null)
    }
    
    fun setOnSearchListener(listener: (SearchParams) -> Unit) {
        onSearchListener = listener
    }
}























