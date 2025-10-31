package com.example.aifloatingball.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptCommunityItem
import java.util.regex.Pattern

/**
 * Prompt提取对话框
 * 用户可以选择使用Prompt进行AI对话、搜索或编辑
 * 支持填空功能：检测 {关键词} 或 {{关键词}} 格式的占位符，让用户填写
 */
class PromptExtractDialog(
    context: Context,
    private val promptItem: PromptCommunityItem,
    private val onUseForChat: (String) -> Unit,
    private val onUseForSearch: (String) -> Unit
) {
    private val dialog: AlertDialog
    private val placeholders = mutableMapOf<String, EditText>() // 占位符名称 -> 输入框
    
    init {
        val builder = AlertDialog.Builder(context)
            .setTitle(promptItem.title)
        
        // 创建自定义视图
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_prompt_extract, null)
        
        val descriptionText = view.findViewById<TextView>(R.id.prompt_description)
        val contentText = view.findViewById<TextView>(R.id.prompt_content)
        val editText = view.findViewById<EditText>(R.id.prompt_edit_input)
        val fillInContainer = view.findViewById<LinearLayout>(R.id.fill_in_container)
        
        descriptionText.text = promptItem.description
        contentText.text = promptItem.content
        
        // 检测占位符并创建输入框
        val placeholderPattern = Pattern.compile("\\{+([^}]+)\\}") // 匹配 {关键词} 或 {{关键词}}
        val matcher = placeholderPattern.matcher(promptItem.content)
        val uniquePlaceholders = mutableSetOf<String>()
        
        while (matcher.find()) {
            val placeholderText = matcher.group(1).trim()
            if (placeholderText.isNotEmpty()) {
                uniquePlaceholders.add(placeholderText)
            }
        }
        
        // 如果有占位符，显示填空输入框
        if (uniquePlaceholders.isNotEmpty()) {
            fillInContainer?.visibility = View.VISIBLE
            fillInContainer?.removeAllViews()
            
            uniquePlaceholders.forEach { placeholder ->
                val label = TextView(context).apply {
                    text = "填写：$placeholder"
                    textSize = 14f
                    setPadding(0, 8, 0, 4)
                    setTextColor(context.getColor(R.color.ai_assistant_text_secondary))
                }
                fillInContainer?.addView(label)
                
                val inputField = EditText(context).apply {
                    hint = "请输入$placeholder"
                    minHeight = (48 * context.resources.displayMetrics.density).toInt()
                    setPadding(16, 12, 16, 12)
                    background = context.getDrawable(android.R.drawable.edit_text)
                }
                fillInContainer?.addView(inputField)
                placeholders[placeholder] = inputField
            }
            
            // 设置初始内容（替换占位符为提示文本）
            editText.setText(promptItem.content)
        } else {
            fillInContainer?.visibility = View.GONE
            editText.setText(promptItem.content)
        }
        
        builder.setView(view)
            .setPositiveButton("AI对话") { _, _ ->
                val finalPrompt = replacePlaceholders(editText.text.toString().trim())
                if (finalPrompt.isNotEmpty()) {
                    onUseForChat(finalPrompt)
                } else {
                    Toast.makeText(context, "提示词不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("搜索") { _, _ ->
                val finalPrompt = replacePlaceholders(editText.text.toString().trim())
                if (finalPrompt.isNotEmpty()) {
                    onUseForSearch(finalPrompt)
                } else {
                    Toast.makeText(context, "提示词不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
        
        dialog = builder.create()
    }
    
    /**
     * 替换占位符为用户填写的内容
     */
    private fun replacePlaceholders(text: String): String {
        var result = text
        placeholders.forEach { (placeholder, inputField) ->
            val userInput = inputField.text.toString().trim()
            // 替换 {关键词} 和 {{关键词}} 格式
            result = result.replace(Regex("\\{+$placeholder\\}+"), userInput.ifEmpty { placeholder })
        }
        return result
    }
    
    fun show() {
        dialog.show()
    }
}

