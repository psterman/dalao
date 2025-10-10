package com.example.aifloatingball.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.ChatHistoryAdapter
import com.example.aifloatingball.data.ChatDataManager
import com.example.aifloatingball.model.ChatHistoryQuestion
import com.example.aifloatingball.model.QuestionFilterType
import com.example.aifloatingball.model.ChatHistoryManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天记录对话框
 */
class ChatHistoryDialog(
    context: Context,
    private val contactName: String,
    private val onQuestionClick: (ChatHistoryQuestion) -> Unit
) : Dialog(context) {

    private lateinit var totalQuestionsText: TextView
    private lateinit var recentQuestionsText: TextView
    private lateinit var linkQuestionsText: TextView
    private lateinit var favoriteQuestionsText: TextView
    private lateinit var chipGroupFilters: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyLayout: LinearLayout
    private lateinit var closeButton: ImageButton
    
    private lateinit var adapter: ChatHistoryAdapter
    private lateinit var chatDataManager: ChatDataManager
    private lateinit var chatHistoryManager: ChatHistoryManager
    private var allQuestions: List<ChatHistoryQuestion> = emptyList()
    private var filteredQuestions: List<ChatHistoryQuestion> = emptyList()
    private val selectedFilters = mutableSetOf<QuestionFilterType>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_chat_history)
        
        initViews()
        setupRecyclerView()
        setupFilterChips()
        loadChatHistory()
        setupListeners()
    }

    private fun initViews() {
        totalQuestionsText = findViewById(R.id.tv_total_questions)
        recentQuestionsText = findViewById(R.id.tv_recent_questions)
        linkQuestionsText = findViewById(R.id.tv_link_questions)
        favoriteQuestionsText = findViewById(R.id.tv_favorite_questions)
        chipGroupFilters = findViewById(R.id.chip_group_filters)
        recyclerView = findViewById(R.id.rv_chat_history)
        emptyLayout = findViewById(R.id.layout_empty_history)
        closeButton = findViewById(R.id.btn_close_history)
        
        chatDataManager = ChatDataManager.getInstance(context)
        chatHistoryManager = ChatHistoryManager()
    }

    private fun setupRecyclerView() {
        adapter = ChatHistoryAdapter(
            onQuestionClick = { question ->
                onQuestionClick(question)
                dismiss()
            },
            onFavoriteClick = { question ->
                toggleFavorite(question)
            },
            onDeleteClick = { question ->
                toggleDelete(question)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun setupFilterChips() {
        // 添加筛选标签
        QuestionFilterType.values().forEach { filterType ->
            val chip = createFilterChip(filterType, filterType == QuestionFilterType.ALL)
            chipGroupFilters.addView(chip)
        }
    }

    private fun createFilterChip(filterType: QuestionFilterType, isSelected: Boolean): Chip {
        val chip = Chip(context)
        chip.text = filterType.displayName
        chip.isCheckable = true
        chip.isChecked = isSelected
        
        chip.setOnCheckedChangeListener { _, isChecked ->
            if (filterType == QuestionFilterType.ALL) {
                if (isChecked) {
                    // 选中"全部"时，取消其他选择
                    selectedFilters.clear()
                    selectedFilters.add(filterType)
                    updateChipStates()
                }
            } else {
                if (isChecked) {
                    selectedFilters.add(filterType)
                    // 如果选择了具体筛选，取消"全部"的选择
                    val allChip = chipGroupFilters.getChildAt(0) as Chip
                    allChip.isChecked = false
                } else {
                    selectedFilters.remove(filterType)
                    // 如果没有选中任何筛选，自动选中"全部"
                    if (selectedFilters.isEmpty()) {
                        val allChip = chipGroupFilters.getChildAt(0) as Chip
                        allChip.isChecked = true
                        selectedFilters.add(QuestionFilterType.ALL)
                    }
                }
            }
            filterQuestions()
        }
        
        return chip
    }

    private fun updateChipStates() {
        for (i in 1 until chipGroupFilters.childCount) {
            val chip = chipGroupFilters.getChildAt(i) as Chip
            chip.isChecked = false
        }
    }

    private fun loadChatHistory() {
        try {
            // 从ChatDataManager获取聊天记录
            val chatSessions = chatDataManager.getAllSessions()
            val questions = mutableListOf<ChatHistoryQuestion>()
            
            chatSessions.forEach { session ->
                session.messages.forEachIndexed { index, message ->
                    if (message.role == "user") {
                        val hasLink = chatHistoryManager.hasLink(message.content)
                        val question = ChatHistoryQuestion(
                            id = "${session.id}_$index",
                            content = message.content,
                            timestamp = message.timestamp,
                            messageIndex = index,
                            sessionId = session.id,
                            aiServiceType = "UNKNOWN", // ChatSession没有aiServiceType字段
                            hasLink = hasLink,
                            isDeleted = false, // 默认未删除
                            isFavorite = false // 默认未收藏
                        )
                        questions.add(question)
                    }
                }
            }
            
            allQuestions = questions.sortedByDescending { it.timestamp }
            filteredQuestions = allQuestions
            
            updateStatistics()
            adapter.updateQuestions(filteredQuestions)
            updateEmptyState()
            
        } catch (e: Exception) {
            android.util.Log.e("ChatHistoryDialog", "加载聊天记录失败", e)
        }
    }

    private fun filterQuestions() {
        filteredQuestions = if (selectedFilters.contains(QuestionFilterType.ALL)) {
            allQuestions
        } else {
            allQuestions.filter { question ->
                selectedFilters.any { filterType ->
                    chatHistoryManager.matchesFilter(question, filterType)
                }
            }
        }
        
        adapter.updateQuestions(filteredQuestions)
        updateEmptyState()
    }

    private fun toggleFavorite(question: ChatHistoryQuestion) {
        val updatedQuestion = question.copy(isFavorite = !question.isFavorite)
        val index = allQuestions.indexOfFirst { it.id == question.id }
        if (index != -1) {
            allQuestions = allQuestions.toMutableList().apply { set(index, updatedQuestion) }
            filterQuestions()
            updateStatistics()
        }
    }

    private fun toggleDelete(question: ChatHistoryQuestion) {
        val updatedQuestion = question.copy(isDeleted = !question.isDeleted)
        val index = allQuestions.indexOfFirst { it.id == question.id }
        if (index != -1) {
            allQuestions = allQuestions.toMutableList().apply { set(index, updatedQuestion) }
            filterQuestions()
            updateStatistics()
        }
    }

    private fun updateStatistics() {
        val totalQuestions = allQuestions.size
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val recentQuestions = allQuestions.count { it.timestamp >= sevenDaysAgo }
        val linkQuestions = allQuestions.count { it.hasLink }
        val deletedQuestions = allQuestions.count { it.isDeleted }
        val favoriteQuestions = allQuestions.count { it.isFavorite }
        
        totalQuestionsText.text = totalQuestions.toString()
        recentQuestionsText.text = recentQuestions.toString()
        linkQuestionsText.text = linkQuestions.toString()
        favoriteQuestionsText.text = favoriteQuestions.toString()
    }

    private fun updateEmptyState() {
        if (filteredQuestions.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        closeButton.setOnClickListener {
            dismiss()
        }
    }
}
