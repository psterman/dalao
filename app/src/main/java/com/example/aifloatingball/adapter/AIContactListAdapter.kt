package com.example.aifloatingball.adapter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.utils.BitmapUtils
import com.example.aifloatingball.utils.FaviconLoader
import com.example.aifloatingball.utils.PlatformIconLoader
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

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
            
            // 群聊：组合头像（2-9 宫格）
            if (contact.type == ContactType.GROUP || contact.aiMembers.isNotEmpty() || contactName.startsWith("群聊")) {
                setupGroupAvatar(contact)
            } else if (contactName == "临时专线") {
                // 临时专线使用Material风格的首字母L图标
                avatarImage.setImageResource(R.drawable.ic_temp_ai_bot)
                avatarImage.imageTintList = null
                avatarImage.clearColorFilter()
                avatarImage.background = null
                avatarImage.setPadding(0,0,0,0)
            } else {
                // 单联系人：使用PlatformIconLoader加载应用图标
                PlatformIconLoader.loadPlatformIcon(avatarImage, contactName, avatarImage.context)
            }

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

        // 组合群聊头像
        private fun setupGroupAvatar(contact: ChatContact) {
            val names = buildGroupMemberNames(contact)

            if (names.isEmpty()) {
                avatarImage.setImageResource(R.drawable.ic_group_chat)
                avatarImage.imageTintList = null
                avatarImage.clearColorFilter()
                avatarImage.background = null
                avatarImage.setPadding(0,0,0,0)
                return
            }

            val groupKey = com.example.aifloatingball.utils.GroupAvatarCache.makeGroupKey(names, 128)
            avatarImage.tag = groupKey
            com.example.aifloatingball.utils.GroupAvatarCache.getGroupAvatar(groupKey)?.let { cached ->
                avatarImage.imageTintList = null
                avatarImage.clearColorFilter()
                avatarImage.background = null
                avatarImage.setPadding(0,0,0,0)
                avatarImage.setImageBitmap(cached)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                val bitmaps = mutableListOf<Bitmap>()
                for (member in names.take(9)) {
                    val bmp = if (member == "临时专线") {
                        BitmapUtils.loadBitmapFromResource(avatarImage.context, R.drawable.ic_temp_ai_bot)
                    } else {
                        com.example.aifloatingball.utils.GroupAvatarCache.getMemberIcon(member)
                            ?: loadMemberBitmap(member)?.also {
                                com.example.aifloatingball.utils.GroupAvatarCache.putMemberIcon(member, it)
                            }
                    }
                    if (bmp != null) bitmaps.add(bmp)
                }

                if (bitmaps.isNotEmpty()) {
                    val combined = BitmapUtils.combineAvatarsGrid(bitmaps, 128)
                    com.example.aifloatingball.utils.GroupAvatarCache.putGroupAvatar(groupKey, combined)
                    withContext(Dispatchers.Main) {
                        if (avatarImage.tag == groupKey) {
                            avatarImage.imageTintList = null
                            avatarImage.clearColorFilter()
                            avatarImage.background = null
                            avatarImage.setPadding(0,0,0,0)
                            avatarImage.scaleType = ImageView.ScaleType.CENTER_CROP
                            avatarImage.setImageBitmap(combined)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (avatarImage.tag == groupKey) {
                            avatarImage.setImageResource(R.drawable.ic_group_chat)
                            avatarImage.imageTintList = null
                            avatarImage.clearColorFilter()
                            avatarImage.background = null
                            avatarImage.setPadding(0,0,0,0)
                        }
                    }
                }
            }
        }

        private fun buildGroupMemberNames(contact: ChatContact): List<String> {
            if (contact.aiMembers.isNotEmpty()) return contact.aiMembers
            // customData 的 ai_service_types 优先
            val typesStr = contact.customData["ai_service_types"]
            if (!typesStr.isNullOrBlank()) {
                val names = typesStr.split(",").mapNotNull { raw ->
                    val key = raw.trim().uppercase()
                    try {
                        val t = com.example.aifloatingball.manager.AIServiceType.valueOf(key)
                        com.example.aifloatingball.utils.AIServiceTypeUtils.getAIDisplayName(t)
                    } catch (_: Exception) { null }
                }
                if (names.isNotEmpty()) return names
            }
            // 退化：从标题解析：群聊（A, B, C）
            val title = contact.name
            val start = title.indexOf('（')
            val end = title.indexOf('）')
            if (start != -1 && end != -1 && end > start) {
                return title.substring(start + 1, end)
                    .split(',', '，')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            return emptyList()
        }

        private suspend fun loadMemberBitmap(name: String): Bitmap? {
            // 已安装应用优先
            try {
                val pm = avatarImage.context.packageManager
                val candidates: List<String>? = when {
                    name.contains("DeepSeek", ignoreCase = true) -> listOf(
                        "com.deepseek.chat", "ai.deepseek.app", "com.deepseek.app", "com.deepseek.deepchat"
                    )
                    name.contains("ChatGPT", ignoreCase = true) -> listOf("com.openai.chatgpt")
                    name.contains("Claude", ignoreCase = true) -> listOf("com.anthropic.claude")
                    name.contains("Gemini", ignoreCase = true) -> listOf("com.google.android.apps.gemini")
                    name.contains("Kimi", ignoreCase = true) || name.contains("月之暗面", ignoreCase = true) -> listOf("com.moonshot.kimichat", "com.moonshot.kimi")
                    name.contains("豆包", ignoreCase = true) -> listOf("com.larus.nova")
                    else -> null
                }
                val pkg = candidates?.firstOrNull { c ->
                    try { pm.getPackageInfo(c, 0); true } catch (_: Exception) { false }
                }
                if (pkg != null) {
                    val icon = pm.getApplicationIcon(pkg)
                    drawableToBitmap(icon)?.let { return it }
                }
            } catch (_: Exception) { }

            // 网络兜底（favicon）
            return try { FaviconLoader.getAIEngineBitmap(name) } catch (_: Exception) { null }
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap? {
            return try {
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 144
                    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 144
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            } catch (e: Exception) { null }
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
