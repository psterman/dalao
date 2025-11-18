package com.example.aifloatingball.viewer

import android.content.Intent
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
 * 笔记管理Activity
 */
class NoteManagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "NoteManagerActivity"
        const val EXTRA_FILE_PATH = "file_path"
        const val RESULT_NOTE_SELECTED = 102
        const val RESULT_NOTE_PAGE = "note_page"
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: NoteAdapter
    private lateinit var dataManager: ReaderDataManager
    private var filePath: String = ""
    private var notes: List<Note> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_manager)
        
        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        dataManager = ReaderDataManager(this)
        
        initViews()
        loadNotes()
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
        
        adapter = NoteAdapter(
            notes = notes,
            onItemClick = { note ->
                // 显示笔记详情
                showNoteDetailDialog(note)
            },
            onItemLongClick = { note ->
                showNoteOptionsDialog(note)
                true
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadNotes() {
        notes = dataManager.getNotes(filePath).sortedByDescending { it.timestamp }
        adapter.updateNotes(notes)
        toolbar.title = "我的笔记 (${notes.size})"
        
        if (notes.isEmpty()) {
            findViewById<TextView>(R.id.emptyText)?.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.emptyText)?.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showNoteDetailDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("笔记详情")
            .setMessage("原文：${note.text}\n\n笔记：${if (note.noteContent.isNotEmpty()) note.noteContent else "（无内容）"}")
            .setPositiveButton("编辑") { _, _ ->
                showEditNoteDialog(note)
            }
            .setNeutralButton("跳转") { _, _ ->
                val resultIntent = Intent().apply {
                    putExtra(RESULT_NOTE_PAGE, note.pageIndex)
                }
                setResult(RESULT_NOTE_SELECTED, resultIntent)
                finish()
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showNoteOptionsDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("笔记操作")
            .setItems(arrayOf("跳转", "编辑", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        // 跳转
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_NOTE_PAGE, note.pageIndex)
                        }
                        setResult(RESULT_NOTE_SELECTED, resultIntent)
                        finish()
                    }
                    1 -> {
                        // 编辑
                        showEditNoteDialog(note)
                    }
                    2 -> {
                        // 删除
                        showDeleteNoteDialog(note)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditNoteDialog(note: Note) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_note, null)
        val originalText = dialogView.findViewById<TextView>(R.id.originalText)
        val noteInput = dialogView.findViewById<android.widget.EditText>(R.id.noteInput)
        
        originalText.text = "原文：${note.text}"
        noteInput.setText(note.noteContent)
        noteInput.hint = "输入笔记内容..."
        
        AlertDialog.Builder(this)
            .setTitle("编辑笔记")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val updatedNote = note.copy(noteContent = noteInput.text.toString())
                dataManager.updateNote(updatedNote)
                loadNotes()
                Toast.makeText(this, "笔记已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteNoteDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("删除笔记")
            .setMessage("确定要删除这条笔记吗？\n\n原文：${note.text.take(50)}...")
            .setPositiveButton("删除") { _, _ ->
                dataManager.deleteNote(note.id)
                loadNotes()
                Toast.makeText(this, "已删除笔记", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteAllDialog() {
        if (notes.isEmpty()) {
            Toast.makeText(this, "没有可删除的笔记", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("删除所有笔记")
            .setMessage("确定要删除所有 ${notes.size} 条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                notes.forEach { note ->
                    dataManager.deleteNote(note.id)
                }
                loadNotes()
                Toast.makeText(this, "已删除所有笔记", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 笔记列表适配器
     */
    class NoteAdapter(
        private var notes: List<Note>,
        private val onItemClick: (Note) -> Unit,
        private val onItemLongClick: (Note) -> Unit
    ) : RecyclerView.Adapter<NoteAdapter.ViewHolder>() {
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val originalText: TextView = itemView.findViewById(R.id.originalText)
            val noteContent: TextView = itemView.findViewById(R.id.noteContent)
            val pageInfo: TextView = itemView.findViewById(R.id.notePageInfo)
            val timeInfo: TextView = itemView.findViewById(R.id.noteTimeInfo)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_note, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = notes[position]
            
            holder.originalText.text = note.text
            holder.noteContent.text = if (note.noteContent.isNotEmpty()) {
                note.noteContent
            } else {
                "（无笔记内容）"
            }
            holder.pageInfo.text = "第 ${note.pageIndex + 1} 页"
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.timeInfo.text = dateFormat.format(Date(note.timestamp))
            
            holder.itemView.setOnClickListener {
                onItemClick(note)
            }
            
            holder.itemView.setOnLongClickListener {
                onItemLongClick(note)
                true
            }
            
            holder.deleteButton.setOnClickListener {
                onItemLongClick(note)
            }
        }
        
        override fun getItemCount(): Int = notes.size
        
        fun updateNotes(newNotes: List<Note>) {
            notes = newNotes
            notifyDataSetChanged()
        }
    }
}

