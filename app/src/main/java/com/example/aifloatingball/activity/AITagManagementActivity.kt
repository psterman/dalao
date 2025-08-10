package com.example.aifloatingball.activity

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.AITagManager
import com.example.aifloatingball.model.AITag
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * AI标签管理界面
 * 允许用户创建、编辑、删除自定义标签
 */
class AITagManagementActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AITagManagementActivity"
        
        fun start(context: Context) {
            val intent = Intent(context, AITagManagementActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    private lateinit var tagManager: AITagManager
    private lateinit var tagAdapter: TagAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var addTagFab: FloatingActionButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_tag_management)
        
        initViews()
        initTagManager()
        setupRecyclerView()
        loadTags()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.tag_recycler_view)
        addTagFab = findViewById(R.id.add_tag_fab)
        
        // 设置标题
        supportActionBar?.title = "AI标签管理"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 添加标签按钮
        addTagFab.setOnClickListener {
            showCreateTagDialog()
        }
    }
    
    private fun initTagManager() {
        tagManager = AITagManager.getInstance(this)
    }
    
    private fun setupRecyclerView() {
        tagAdapter = TagAdapter(
            onTagClick = { tag -> showTagOptionsDialog(tag) },
            onTagEdit = { tag -> showEditTagDialog(tag) },
            onTagDelete = { tag -> showDeleteTagDialog(tag) }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AITagManagementActivity)
            adapter = tagAdapter
        }
    }
    
    private fun loadTags() {
        val tags = tagManager.getAllTags()
        tagAdapter.updateTags(tags)
        Log.d(TAG, "加载标签: ${tags.size}个")
    }
    
    private fun showCreateTagDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_tag, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.tag_name_input)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.tag_description_input)
        
        AlertDialog.Builder(this)
            .setTitle("创建新标签")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                
                if (name.isNotEmpty()) {
                    val newTag = tagManager.createTag(name, description)
                    loadTags()
                    Toast.makeText(this, "已创建标签：$name", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "创建新标签: $name")
                } else {
                    Toast.makeText(this, "请输入标签名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditTagDialog(tag: AITag) {
        if (tag.isDefault) {
            Toast.makeText(this, "默认标签不能编辑", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_tag, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.tag_name_input)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.tag_description_input)
        
        nameInput.setText(tag.name)
        descriptionInput.setText(tag.description)
        
        AlertDialog.Builder(this)
            .setTitle("编辑标签")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                
                if (name.isNotEmpty()) {
                    val success = tagManager.updateTag(tag.id, name, description, tag.color)
                    if (success) {
                        loadTags()
                        Toast.makeText(this, "标签已更新", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "更新标签: $name")
                    } else {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "请输入标签名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteTagDialog(tag: AITag) {
        if (tag.isDefault) {
            Toast.makeText(this, "默认标签不能删除", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("删除标签")
            .setMessage("确定要删除标签 \"${tag.name}\" 吗？\n删除后，该标签下的AI对象将不再分类。")
            .setPositiveButton("删除") { _, _ ->
                val success = tagManager.deleteTag(tag.id)
                if (success) {
                    loadTags()
                    Toast.makeText(this, "标签已删除", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "删除标签: ${tag.name}")
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showTagOptionsDialog(tag: AITag) {
        val options = mutableListOf<String>()
        
        if (!tag.isDefault) {
            options.add("编辑")
            options.add("删除")
        }
        
        options.add("查看AI对象")
        
        AlertDialog.Builder(this)
            .setTitle(tag.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "编辑" -> showEditTagDialog(tag)
                    "删除" -> showDeleteTagDialog(tag)
                    "查看AI对象" -> showTagAIsDialog(tag)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showTagAIsDialog(tag: AITag) {
        // 这里可以显示该标签下的AI对象列表
        Toast.makeText(this, "该标签下的AI对象功能开发中...", Toast.LENGTH_SHORT).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    /**
     * 标签适配器
     */
    private class TagAdapter(
        private val onTagClick: (AITag) -> Unit,
        private val onTagEdit: (AITag) -> Unit,
        private val onTagDelete: (AITag) -> Unit
    ) : RecyclerView.Adapter<TagAdapter.TagViewHolder>() {
        
        private var tags = listOf<AITag>()
        
        fun updateTags(newTags: List<AITag>) {
            tags = newTags
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_tag, parent, false)
            return TagViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            holder.bind(tags[position])
        }
        
        override fun getItemCount(): Int = tags.size
        
        inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tagName: TextView = itemView.findViewById(R.id.tag_name)
            private val tagDescription: TextView = itemView.findViewById(R.id.tag_description)
            private val tagColor: View = itemView.findViewById(R.id.tag_color_indicator)
            private val editButton: ImageView = itemView.findViewById(R.id.edit_tag_button)
            private val deleteButton: ImageView = itemView.findViewById(R.id.delete_tag_button)
            
            fun bind(tag: AITag) {
                tagName.text = tag.name
                tagDescription.text = tag.description.ifEmpty { "无描述" }
                
                // 设置标签颜色
                if (tag.color != 0) {
                    tagColor.setBackgroundColor(tag.color)
                } else {
                    tagColor.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.colorPrimary))
                }
                
                // 设置按钮可见性
                editButton.visibility = if (tag.isDefault) View.GONE else View.VISIBLE
                deleteButton.visibility = if (tag.isDefault) View.GONE else View.VISIBLE
                
                // 设置点击事件
                itemView.setOnClickListener { onTagClick(tag) }
                editButton.setOnClickListener { onTagEdit(tag) }
                deleteButton.setOnClickListener { onTagDelete(tag) }
            }
        }
    }
}
