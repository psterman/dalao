package com.example.aifloatingball.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.voice.VoiceTextTagManager
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

/**
 * 语音文本Fragment
 * 显示保存的语音转化文本列表，支持查看和编辑
 */
class VoiceTextFragment : AIAssistantCenterFragment() {
    
    private lateinit var voiceTextRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var voiceTextTagManager: VoiceTextTagManager
    private lateinit var adapter: VoiceTextAdapter
    private val voiceTexts = mutableListOf<VoiceTextTagManager.VoiceTextInfo>()
    private var voiceTextUpdateReceiver: BroadcastReceiver? = null
    
    companion object {
        private const val TAG = "VoiceTextFragment"
    }
    
    override fun getLayoutResId(): Int = R.layout.fragment_voice_text
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        voiceTextTagManager = VoiceTextTagManager(requireContext())
        
        // 初始化视图
        voiceTextRecyclerView = view.findViewById(R.id.voice_text_recycler_view)
        emptyStateText = view.findViewById(R.id.empty_state_text)
        
        // 设置RecyclerView
        adapter = VoiceTextAdapter(voiceTexts) { textInfo ->
            // 点击项时显示编辑对话框
            showEditDialog(textInfo)
        }
        voiceTextRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        voiceTextRecyclerView.adapter = adapter
        
        // 加载语音文本列表
        loadVoiceTexts()
        
        // 设置广播接收器，监听语音文本更新
        setupVoiceTextUpdateReceiver()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次显示时重新加载，确保显示最新数据
        loadVoiceTexts()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 注销广播接收器
        voiceTextUpdateReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "注销广播接收器失败", e)
            }
        }
        voiceTextUpdateReceiver = null
    }
    
    /**
     * 设置语音文本更新广播接收器
     */
    private fun setupVoiceTextUpdateReceiver() {
        voiceTextUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.aifloatingball.COLLECTION_UPDATED") {
                    val collectionTypeName = intent.getStringExtra("collection_type")
                    val action = intent.getStringExtra("action")
                    
                    Log.d(TAG, "收到收藏更新广播: type=$collectionTypeName, action=$action")
                    
                    // 如果是语音转文本类型的更新，刷新列表
                    if (collectionTypeName == "VOICE_TO_TEXT" && action == "add") {
                        Log.d(TAG, "检测到语音转文本新增，刷新列表")
                        loadVoiceTexts()
                    }
                }
            }
        }
        
        try {
            val filter = IntentFilter("com.example.aifloatingball.COLLECTION_UPDATED")
            requireContext().registerReceiver(voiceTextUpdateReceiver, filter)
            Log.d(TAG, "语音文本更新广播接收器已注册")
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败", e)
        }
    }
    
    /**
     * 加载语音文本列表
     */
    private fun loadVoiceTexts() {
        try {
            val texts = voiceTextTagManager.getAllTexts()
            voiceTexts.clear()
            voiceTexts.addAll(texts.sortedByDescending { it.createdAt })
            adapter.notifyDataSetChanged()
            
            // 更新空状态显示
            emptyStateText.visibility = if (voiceTexts.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            Log.d(TAG, "加载了 ${voiceTexts.size} 条语音文本")
        } catch (e: Exception) {
            Log.e(TAG, "加载语音文本列表失败", e)
        }
    }
    
    /**
     * 显示编辑对话框
     */
    private fun showEditDialog(textInfo: VoiceTextTagManager.VoiceTextInfo) {
        try {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_voice_text, null)
            
            val textInput = dialogView.findViewById<TextInputEditText>(R.id.voice_text_input)
            textInput.setText(textInfo.text)
            
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("编辑语音文本")
                .setView(dialogView)
                .setPositiveButton("保存") { _, _ ->
                    val newText = textInput.text.toString().trim()
                    if (newText.isNotEmpty() && newText != textInfo.text) {
                        // 更新文本（删除旧的，添加新的）
                        voiceTextTagManager.deleteText(textInfo)
                        voiceTextTagManager.saveTextToTag(newText)
                        loadVoiceTexts()
                        android.widget.Toast.makeText(requireContext(), "文本已更新", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .setNeutralButton("删除") { _, _ ->
                    // 删除文本
                    voiceTextTagManager.deleteText(textInfo)
                    loadVoiceTexts()
                    android.widget.Toast.makeText(requireContext(), "文本已删除", android.widget.Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "显示编辑对话框失败", e)
        }
    }
    
    /**
     * 语音文本适配器
     */
    private class VoiceTextAdapter(
        private val texts: List<VoiceTextTagManager.VoiceTextInfo>,
        private val onItemClick: (VoiceTextTagManager.VoiceTextInfo) -> Unit
    ) : RecyclerView.Adapter<VoiceTextAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_voice_text, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val textInfo = texts[position]
            holder.bind(textInfo)
            holder.itemView.setOnClickListener {
                onItemClick(textInfo)
            }
        }
        
        override fun getItemCount(): Int = texts.size
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(R.id.voice_text_content)
            private val timeView: TextView = itemView.findViewById(R.id.voice_text_time)
            
            fun bind(textInfo: VoiceTextTagManager.VoiceTextInfo) {
                textView.text = textInfo.text
                
                // 格式化时间
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val dateStr = dateFormat.format(Date(textInfo.createdAt))
                timeView.text = dateStr
            }
        }
    }
}


