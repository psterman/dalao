package com.example.aifloatingball.viewer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.*

/**
 * 划线管理Activity
 */
class HighlightManagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "HighlightManagerActivity"
        const val EXTRA_FILE_PATH = "file_path"
        const val RESULT_HIGHLIGHT_SELECTED = 101
        const val RESULT_HIGHLIGHT_PAGE = "highlight_page"
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: HighlightAdapter
    private lateinit var dataManager: ReaderDataManager
    private var filePath: String = ""
    private var highlights: List<Highlight> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_highlight_manager)
        
        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        dataManager = ReaderDataManager(this)
        
        initViews()
        loadHighlights()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_delete_all -> {
                    showDeleteAllDialog()
                    true
                }
                else -> false
            }
        }
        
        adapter = HighlightAdapter(
            highlights = highlights,
            onItemClick = { highlight ->
                // 返回选中的划线页码
                val resultIntent = Intent().apply {
                    putExtra(RESULT_HIGHLIGHT_PAGE, highlight.pageIndex)
                }
                setResult(RESULT_HIGHLIGHT_SELECTED, resultIntent)
                finish()
            },
            onItemLongClick = { highlight ->
                showHighlightOptionsDialog(highlight)
                true
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadHighlights() {
        highlights = dataManager.getHighlights(filePath).sortedByDescending { it.timestamp }
        adapter.updateHighlights(highlights)
        toolbar.title = "我的划线 (${highlights.size})"
        
        if (highlights.isEmpty()) {
            findViewById<TextView>(R.id.emptyText)?.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.emptyText)?.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showHighlightOptionsDialog(highlight: Highlight) {
        AlertDialog.Builder(this)
            .setTitle("划线操作")
            .setItems(arrayOf("跳转", "更改颜色", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        // 跳转
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_HIGHLIGHT_PAGE, highlight.pageIndex)
                        }
                        setResult(RESULT_HIGHLIGHT_SELECTED, resultIntent)
                        finish()
                    }
                    1 -> {
                        // 更改颜色
                        showColorPickerDialog(highlight)
                    }
                    2 -> {
                        // 删除
                        showDeleteHighlightDialog(highlight)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showColorPickerDialog(highlight: Highlight) {
        val colors = arrayOf(
            "黄色 (#FFEB3B)",
            "绿色 (#4CAF50)",
            "蓝色 (#2196F3)",
            "红色 (#F44336)",
            "紫色 (#9C27B0)"
        )
        val colorValues = arrayOf(
            "#FFEB3B",
            "#4CAF50",
            "#2196F3",
            "#F44336",
            "#9C27B0"
        )
        
        val currentIndex = colorValues.indexOf(highlight.color).takeIf { it >= 0 } ?: 0
        
        AlertDialog.Builder(this)
            .setTitle("选择颜色")
            .setSingleChoiceItems(colors, currentIndex) { dialog, which ->
                val updatedHighlight = highlight.copy(color = colorValues[which])
                dataManager.deleteHighlight(highlight.id)
                dataManager.addHighlight(updatedHighlight)
                loadHighlights()
                dialog.dismiss()
                Toast.makeText(this, "颜色已更改", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteHighlightDialog(highlight: Highlight) {
        AlertDialog.Builder(this)
            .setTitle("删除划线")
            .setMessage("确定要删除这条划线吗？\n\n${highlight.text.take(50)}...")
            .setPositiveButton("删除") { _, _ ->
                dataManager.deleteHighlight(highlight.id)
                loadHighlights()
                Toast.makeText(this, "已删除划线", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteAllDialog() {
        if (highlights.isEmpty()) {
            Toast.makeText(this, "没有可删除的划线", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("删除所有划线")
            .setMessage("确定要删除所有 ${highlights.size} 条划线吗？")
            .setPositiveButton("删除") { _, _ ->
                highlights.forEach { highlight ->
                    dataManager.deleteHighlight(highlight.id)
                }
                loadHighlights()
                Toast.makeText(this, "已删除所有划线", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 划线列表适配器
     */
    class HighlightAdapter(
        private var highlights: List<Highlight>,
        private val onItemClick: (Highlight) -> Unit,
        private val onItemLongClick: (Highlight) -> Unit
    ) : RecyclerView.Adapter<HighlightAdapter.ViewHolder>() {
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.highlightText)
            val pageInfo: TextView = itemView.findViewById(R.id.highlightPageInfo)
            val timeInfo: TextView = itemView.findViewById(R.id.highlightTimeInfo)
            val colorIndicator: View = itemView.findViewById(R.id.colorIndicator)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_highlight, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val highlight = highlights[position]
            
            holder.textView.text = highlight.text
            holder.pageInfo.text = "第 ${highlight.pageIndex + 1} 页"
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.timeInfo.text = dateFormat.format(Date(highlight.timestamp))
            
            // 设置颜色指示器
            try {
                val color = Color.parseColor(highlight.color)
                holder.colorIndicator.setBackgroundColor(color)
            } catch (e: Exception) {
                holder.colorIndicator.setBackgroundColor(Color.parseColor("#FFEB3B"))
            }
            
            holder.itemView.setOnClickListener {
                onItemClick(highlight)
            }
            
            holder.itemView.setOnLongClickListener {
                onItemLongClick(highlight)
                true
            }
            
            holder.deleteButton.setOnClickListener {
                onItemLongClick(highlight)
            }
        }
        
        override fun getItemCount(): Int = highlights.size
        
        fun updateHighlights(newHighlights: List<Highlight>) {
            highlights = newHighlights
            notifyDataSetChanged()
        }
    }
}

