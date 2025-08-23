package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.ChatContact
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
            when (contact.name.lowercase()) {
                "chatgpt" -> avatarImage.setImageResource(R.drawable.ic_chatgpt)
                "claude" -> avatarImage.setImageResource(R.drawable.ic_claude)
                "deepseek" -> avatarImage.setImageResource(R.drawable.ic_deepseek)
                "文心一言" -> avatarImage.setImageResource(R.drawable.ic_wenxin)
                "智谱ai", "智谱AI".lowercase() -> avatarImage.setImageResource(R.drawable.ic_zhipu)
                "通义千问" -> avatarImage.setImageResource(R.drawable.ic_qianwen)
                "讯飞星火" -> avatarImage.setImageResource(R.drawable.ic_xinghuo)
                "gemini" -> avatarImage.setImageResource(R.drawable.ic_gemini)
                "kimi" -> avatarImage.setImageResource(R.drawable.ic_kimi)
                else -> avatarImage.setImageResource(R.drawable.ic_default_ai)
            }

            // 设置AI分组标签
            val groupTag = getAIGroupTag(contact.name)
            if (groupTag != null) {
                groupTagText.text = groupTag
                groupTagText.visibility = View.VISIBLE
            } else {
                groupTagText.visibility = View.GONE
            }

            // 设置最后对话预览
            val isConfigured = contact.customData["is_configured"] == "true"
            if (isConfigured) {
                // 显示最后对话预览，如果没有则显示默认提示
                statusText.text = contact.lastMessage ?: "开始新对话"
                statusText.setTextColor(itemView.context.getColor(R.color.simple_mode_text_secondary_light))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.simple_mode_success_light))
            } else {
                statusText.text = "点击配置API密钥"
                statusText.setTextColor(itemView.context.getColor(R.color.simple_mode_warning_light))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.simple_mode_warning_light))
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
        private fun getAIGroupTag(aiName: String): String? {
            return when (aiName.lowercase()) {
                "chatgpt", "claude", "deepseek" -> "编程助手"
                "文心一言", "智谱ai", "智谱AI".lowercase() -> "写作助手"
                "通义千问", "讯飞星火" -> "翻译助手"
                "gemini", "kimi" -> "AI助手"
                else -> null
            }
        }
    }
}
