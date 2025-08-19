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

            // 设置真实的AI图标
            when (contact.name.lowercase()) {
                "deepseek" -> avatarImage.setImageResource(R.drawable.ic_deepseek)
                "chatgpt" -> avatarImage.setImageResource(R.drawable.ic_chatgpt)
                "claude" -> avatarImage.setImageResource(R.drawable.ic_claude)
                "gemini" -> avatarImage.setImageResource(R.drawable.ic_gemini)
                "智谱ai" -> avatarImage.setImageResource(R.drawable.ic_zhipu)
                "文心一言" -> avatarImage.setImageResource(R.drawable.ic_wenxin)
                "通义千问" -> avatarImage.setImageResource(R.drawable.ic_qianwen)
                "讯飞星火" -> avatarImage.setImageResource(R.drawable.ic_xinghuo)
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

            // 设置状态
            val isConfigured = contact.customData["is_configured"] == "true"
            if (isConfigured) {
                statusText.text = "API已配置"
                statusText.setTextColor(itemView.context.getColor(R.color.simple_mode_success_light))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.simple_mode_success_light))
                apiKeyButton.text = "修改配置"
                apiKeyButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    itemView.context.getColor(R.color.simple_mode_primary_light)
                )
            } else {
                statusText.text = "未配置API"
                statusText.setTextColor(itemView.context.getColor(R.color.simple_mode_warning_light))
                onlineIndicator.setBackgroundColor(itemView.context.getColor(R.color.simple_mode_warning_light))
                apiKeyButton.text = "配置API"
                apiKeyButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    itemView.context.getColor(R.color.simple_mode_accent_light)
                )
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

        /**
         * 获取AI分组标签
         */
        private fun getAIGroupTag(aiName: String): String? {
            return when (aiName.lowercase()) {
                "chatgpt", "claude", "deepseek" -> "编程助手"
                "文心一言", "智谱ai" -> "写作助手"
                "通义千问", "讯飞星火" -> "翻译助手"
                "gemini", "kimi" -> "AI助手"
                else -> null
            }
        }
    }
}
