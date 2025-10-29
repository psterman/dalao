package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.utils.PlatformIconLoader
import com.google.android.material.button.MaterialButton

class AIContactListAdapter(
    private val onContactClick: (ChatContact) -> Unit,
    private val onApiKeyClick: (ChatContact) -> Unit,
    private val onContactLongClick: (ChatContact) -> Boolean
) : RecyclerView.Adapter<AIContactListAdapter.AIContactViewHolder>() {

    private var contacts = mutableListOf<ChatContact>()

    fun updateContacts(newContacts: List<ChatContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AIContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_contact_list, parent, false)
        return AIContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: AIContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount(): Int = contacts.size

    inner class AIContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarImage: ImageView = itemView.findViewById(R.id.avatar_image)
        private val nameText: TextView = itemView.findViewById(R.id.name_text)
        private val groupTagText: TextView = itemView.findViewById(R.id.group_tag_text)
        private val statusText: TextView = itemView.findViewById(R.id.status_text)
        private val apiKeyButton: MaterialButton = itemView.findViewById(R.id.api_key_button)
        private val onlineIndicator: View = itemView.findViewById(R.id.online_indicator)

        fun bind(contact: ChatContact) {
            nameText.text = contact.name

            // 设置AI头像
            val contactName = contact.name
            val contactNameLower = contact.name.lowercase()
            
            // 临时专线使用Material风格的首字母L图标
            if (contactName == "临时专线") {
                avatarImage.setImageResource(R.drawable.ic_temp_ai_letter_l)
                avatarImage.clearColorFilter()
                return
            }
            
            // 使用PlatformIconLoader加载AI应用图标，与软件tab保持一致
            PlatformIconLoader.loadPlatformIcon(avatarImage, contactName, avatarImage.context)

            // 设置AI分组标签 - 始终显示
            val groupTag = getAIGroupTag(contact.name)
            if (!groupTag.isNullOrEmpty()) {
                groupTagText.text = groupTag
                groupTagText.visibility = View.VISIBLE
            } else {
                // 如果没有特定分组，显示默认的"AI助手"标签
                groupTagText.text = "AI助手"
                groupTagText.visibility = View.VISIBLE
            }

            // 显示最后对话预览
            val lastMessage = contact.lastMessage
            if (!lastMessage.isNullOrBlank()) {
                // 显示最后一句话的预览，限制长度
                val previewText = if (lastMessage.length > 50) {
                    lastMessage.substring(0, 50) + "..."
                } else {
                    lastMessage
                }
                statusText.text = previewText
                statusText.setTextColor(itemView.context.getColor(R.color.simple_mode_text_secondary_light))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.simple_mode_success_light))
            } else {
                // 没有对话记录时显示默认提示
                statusText.text = "开始新对话"
                statusText.setTextColor(itemView.context.getColor(R.color.simple_mode_text_secondary_light))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.simple_mode_text_secondary_light))
            }
            
            // 隐藏API配置按钮，改为通过点击联系人进行配置
            apiKeyButton.visibility = View.GONE

            // 设置点击事件
            itemView.setOnClickListener {
                onContactClick(contact)
            }

            itemView.setOnLongClickListener {
                onContactLongClick(contact)
            }

            apiKeyButton.setOnClickListener {
                onApiKeyClick(contact)
            }
        }

        
        /**
         * 获取AI分组标签
         */
        private fun getAIGroupTag(aiName: String?): String? {
            return when (aiName?.lowercase()) {
                "chatgpt", "claude", "deepseek" -> "编程助手"
                "文心一言", "智谱ai" -> "写作助手"
                "通义千问", "讯飞星火" -> "翻译助手"
                "gemini", "kimi" -> "AI助手"
                else -> null
            }
        }
    }
}
