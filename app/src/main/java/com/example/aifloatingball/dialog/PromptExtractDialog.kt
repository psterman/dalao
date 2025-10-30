package com.example.aifloatingball.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptCommunityItem

/**
 * Prompt提取对话框
 * 用户可以选择使用Prompt进行AI对话、搜索或编辑
 */
class PromptExtractDialog(
    context: Context,
    private val promptItem: PromptCommunityItem,
    private val onUseForChat: (String) -> Unit,
    private val onUseForSearch: (String) -> Unit
) {
    private val dialog: AlertDialog
    
    init {
        val builder = AlertDialog.Builder(context)
            .setTitle(promptItem.title)
        
        // 创建自定义视图
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_prompt_extract, null)
        
        val descriptionText = view.findViewById<TextView>(R.id.prompt_description)
        val contentText = view.findViewById<TextView>(R.id.prompt_content)
        val editText = view.findViewById<EditText>(R.id.prompt_edit_input)
        
        descriptionText.text = promptItem.description
        contentText.text = promptItem.content
        editText.setText(promptItem.content)
        
        builder.setView(view)
            .setPositiveButton("AI对话") { _, _ ->
                val prompt = editText.text.toString().trim()
                if (prompt.isNotEmpty()) {
                    onUseForChat(prompt)
                } else {
                    Toast.makeText(context, "提示词不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("搜索") { _, _ ->
                val prompt = editText.text.toString().trim()
                if (prompt.isNotEmpty()) {
                    onUseForSearch(prompt)
                } else {
                    Toast.makeText(context, "提示词不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
        
        dialog = builder.create()
    }
    
    fun show() {
        dialog.show()
    }
}

