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
        private val descriptionText: TextView = itemView.findViewById(R.id.description_text)
        private val statusText: TextView = itemView.findViewById(R.id.status_text)
        private val apiKeyButton: MaterialButton = itemView.findViewById(R.id.api_key_button)
        private val onlineIndicator: View = itemView.findViewById(R.id.online_indicator)

        fun bind(contact: ChatContact) {
            nameText.text = contact.name
            descriptionText.text = contact.description
            
            // 设置头像
            when (contact.name.lowercase()) {
                "deepseek" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "chatgpt" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "claude" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "gemini" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "智谱ai" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "文心一言" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "通义千问" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "讯飞星火" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                "kimi" -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
                else -> avatarImage.setImageResource(R.drawable.ic_ai_ball)
            }

            // 设置状态
            val isConfigured = contact.customData["is_configured"] == "true"
            if (isConfigured) {
                statusText.text = "API已配置"
                statusText.setTextColor(itemView.context.getColor(R.color.success_text_color))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.online_color))
                apiKeyButton.text = "修改配置"
                apiKeyButton.setBackgroundColor(itemView.context.getColor(R.color.primary_color))
            } else {
                statusText.text = "未配置API"
                statusText.setTextColor(itemView.context.getColor(R.color.warning_text_color))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.offline_color))
                apiKeyButton.text = "配置API"
                apiKeyButton.setBackgroundColor(itemView.context.getColor(R.color.accent_color))
            }

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
    }
}
