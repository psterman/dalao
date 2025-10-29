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
import com.example.aifloatingball.model.ContactCategory
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.utils.FaviconLoader
import com.example.aifloatingball.utils.PlatformIconLoader
import com.example.aifloatingball.utils.BitmapUtils
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天联系人适配器
 */
class ChatContactAdapter(
    private var contactCategories: List<ContactCategory> = emptyList(),
    private val onContactClick: (ChatContact) -> Unit = {},
    private val onContactLongClick: (ChatContact) -> Boolean = { false },
    private val onContactDoubleClick: (ChatContact) -> Boolean = { false },
    private val onCategoryLongClick: (ContactCategory) -> Boolean = { false },
    private val getContactGroup: (ChatContact) -> String? = { null }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY_HEADER = 0
        private const val TYPE_CONTACT = 1
    }

    private var filteredCategories: List<ContactCategory> = contactCategories
    private var searchQuery: String = ""

    /**
     * 更新联系人数据
     */
    fun updateContacts(categories: List<ContactCategory>) {
        this.contactCategories = categories
        filterContacts(searchQuery)
    }

    /**
     * 搜索联系人
     */
    fun searchContacts(query: String) {
        searchQuery = query
        filterContacts(query)
    }

    /**
     * 过滤联系人
     */
    private fun filterContacts(query: String) {
        if (query.isEmpty()) {
            filteredCategories = contactCategories
        } else {
            filteredCategories = contactCategories.map { category ->
                val filteredContacts = category.contacts.filter { contact ->
                    contact.name.contains(query, ignoreCase = true) ||
                    contact.description?.contains(query, ignoreCase = true) == true
                }
                category.copy(contacts = filteredContacts.toMutableList())
            }.filter { it.contacts.isNotEmpty() }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        var currentPosition = 0
        for (category in filteredCategories) {
            if (currentPosition == position) {
                return TYPE_CATEGORY_HEADER
            }
            currentPosition++
            
            if (category.isExpanded) {
                for (contact in category.contacts) {
                    if (currentPosition == position) {
                        return TYPE_CONTACT
                    }
                    currentPosition++
                }
            }
        }
        return TYPE_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CATEGORY_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_contact_category_header, parent, false)
                CategoryHeaderViewHolder(view, onCategoryLongClick)
            }
            TYPE_CONTACT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_contact, parent, false)
                ContactViewHolder(view, onContactClick, onContactLongClick, onContactDoubleClick, getContactGroup)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryHeaderViewHolder -> {
                val category = getCategoryAtPosition(position)
                holder.bind(category)
            }
            is ContactViewHolder -> {
                val contact = getContactAtPosition(position)
                holder.bind(contact)
            }
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        for (category in filteredCategories) {
            count++ // 分类标题
            if (category.isExpanded) {
                count += category.contacts.size
            }
        }
        return count
    }

    private fun getCategoryAtPosition(position: Int): ContactCategory {
        var currentPosition = 0
        for (category in filteredCategories) {
            if (currentPosition == position) {
                return category
            }
            currentPosition++
            if (category.isExpanded) {
                currentPosition += category.contacts.size
            }
        }
        throw IndexOutOfBoundsException("Position $position not found")
    }

    private fun getContactAtPosition(position: Int): ChatContact {
        var currentPosition = 0
        for (category in filteredCategories) {
            currentPosition++ // 跳过分类标题
            if (category.isExpanded) {
                for (contact in category.contacts) {
                    if (currentPosition == position) {
                        return contact
                    }
                    currentPosition++
                }
            }
        }
        throw IndexOutOfBoundsException("Position $position not found")
    }

    /**
     * 分类标题ViewHolder
     */
    class CategoryHeaderViewHolder(
        itemView: View,
        private val onCategoryLongClick: (ContactCategory) -> Boolean
    ) : RecyclerView.ViewHolder(itemView) {
        private val categoryNameText: TextView = itemView.findViewById(R.id.category_name_text)
        private val contactCountText: TextView = itemView.findViewById(R.id.contact_count_text)
        private var currentCategory: ContactCategory? = null

        init {
            // 设置长按监听
            itemView.setOnLongClickListener {
                currentCategory?.let { onCategoryLongClick(it) } ?: false
            }
        }

        fun bind(category: ContactCategory) {
            currentCategory = category
            
            // 隐藏分组标题，不显示分组名称
            itemView.visibility = View.GONE
            itemView.layoutParams = itemView.layoutParams?.apply {
                height = 0
            }
        }
    }

    /**
     * 联系人ViewHolder
     */
    class ContactViewHolder(
        itemView: View,
        private val onContactClick: (ChatContact) -> Unit,
        private val onContactLongClick: (ChatContact) -> Boolean,
        private val onContactDoubleClick: (ChatContact) -> Boolean,
        private val getContactGroup: (ChatContact) -> String?
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val contactCard: MaterialCardView = itemView.findViewById(R.id.contact_card)
        private val avatarImage: ImageView = itemView.findViewById(R.id.contact_avatar)
        private val nameText: TextView = itemView.findViewById(R.id.contact_name)
        private val descriptionText: TextView = itemView.findViewById(R.id.contact_description)
        private val groupTagText: TextView = itemView.findViewById(R.id.contact_group_tag)
        private val lastMessageText: TextView = itemView.findViewById(R.id.contact_last_message)
        private val timeText: TextView = itemView.findViewById(R.id.contact_time)
        private val unreadCountText: TextView = itemView.findViewById(R.id.contact_unread_count)
        private val onlineIndicator: View = itemView.findViewById(R.id.contact_online_indicator)
        private val pinnedIndicator: ImageView = itemView.findViewById(R.id.contact_pinned_indicator)
        private val mutedIndicator: ImageView = itemView.findViewById(R.id.contact_muted_indicator)

        private var currentContact: ChatContact? = null

        init {
            var lastClickTime = 0L
            val doubleClickTimeThreshold = 300L // 300毫秒内的两次点击视为双击
            
            contactCard.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < doubleClickTimeThreshold) {
                    // 双击事件
                    currentContact?.let { onContactDoubleClick(it) }
                } else {
                    // 单击事件
                    currentContact?.let { onContactClick(it) }
                }
                lastClickTime = currentTime
            }
            
            contactCard.setOnLongClickListener {
                currentContact?.let { contact ->
                    android.util.Log.d("ChatContactAdapter", "长按联系人: ${contact.name}")
                    onContactLongClick(contact)
                } ?: false
            }
        }

        fun bind(contact: ChatContact) {
            currentContact = contact
            
            // 设置基本信息
            nameText.text = contact.name
            descriptionText.text = contact.description ?: ""

            // 设置分组标签（只在"全部"标签页显示）
            val groupName = getContactGroup(contact)
            if (groupName != null && groupName != "未分组") {
                groupTagText.text = groupName
                groupTagText.visibility = View.VISIBLE
            } else {
                groupTagText.visibility = View.GONE
            }
            
            // 设置头像
            when (contact.type) {
                ContactType.AI -> {
                    val contactName = contact.name
                    
                    // 临时专线使用Material风格的首字母L图标
                    if (contactName == "临时专线") {
                        avatarImage.setImageResource(R.drawable.ic_temp_ai_bot)
                        avatarImage.imageTintList = null
                        avatarImage.clearColorFilter()
                        avatarImage.background = null
                        avatarImage.setPadding(0,0,0,0)
                    } else {
                        // 使用PlatformIconLoader加载AI应用图标，与软件tab保持一致
                        PlatformIconLoader.loadPlatformIcon(avatarImage, contactName, avatarImage.context)
                    }
                }
                ContactType.GROUP -> {
                    // 群聊：组合显示多个AI成员的头像
                    setupGroupAvatar(contact, avatarImage)
                }
            }
            
            // 设置最后消息
            if (contact.lastMessage != null) {
                lastMessageText.text = contact.lastMessage
                lastMessageText.visibility = View.VISIBLE
                
                // 设置时间
                if (contact.lastMessageTime > 0) {
                    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    timeText.text = dateFormat.format(Date(contact.lastMessageTime))
                    timeText.visibility = View.VISIBLE
                } else {
                    timeText.visibility = View.GONE
                }
            } else {
                lastMessageText.visibility = View.GONE
                timeText.visibility = View.GONE
            }
            
            // 设置未读消息数
            if (contact.unreadCount > 0) {
                unreadCountText.text = if (contact.unreadCount > 99) "99+" else contact.unreadCount.toString()
                unreadCountText.visibility = View.VISIBLE
            } else {
                unreadCountText.visibility = View.GONE
            }
            
            // 设置在线状态
            onlineIndicator.visibility = if (contact.isOnline) View.VISIBLE else View.GONE
            
            // 设置置顶状态
            pinnedIndicator.visibility = if (contact.isPinned) View.VISIBLE else View.GONE
            
            // 设置静音状态
            mutedIndicator.visibility = if (contact.isMuted) View.VISIBLE else View.GONE
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 144
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 144
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    } catch (e: Exception) {
        null
    }
}

private fun setupGroupAvatar(contact: com.example.aifloatingball.model.ChatContact, avatarImage: ImageView) {
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

    // 先读缓存，避免重复合成
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
            val cachedMember = com.example.aifloatingball.utils.GroupAvatarCache.getMemberIcon(member)
            val bmp = cachedMember ?: if (member == "临时专线") {
                BitmapUtils.loadBitmapFromResource(avatarImage.context, R.drawable.ic_temp_ai_bot)
            } else {
                loadAIMemberBitmap(member, avatarImage)
            }
            if (bmp != null) {
                bitmaps.add(bmp)
                if (cachedMember == null) com.example.aifloatingball.utils.GroupAvatarCache.putMemberIcon(member, bmp)
            }
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

private suspend fun loadAIMemberBitmap(memberName: String, imageView: ImageView): Bitmap? {
    // 1) 优先尝试从已安装应用读取图标
    try {
        val pm = imageView.context.packageManager
        val candidates: List<String>? = when {
            memberName.contains("DeepSeek", ignoreCase = true) -> listOf(
                "com.deepseek.chat", "ai.deepseek.app", "com.deepseek.app", "com.deepseek.deepchat"
            )
            memberName.contains("ChatGPT", ignoreCase = true) -> listOf("com.openai.chatgpt")
            memberName.contains("Claude", ignoreCase = true) -> listOf("com.anthropic.claude")
            memberName.contains("Gemini", ignoreCase = true) -> listOf("com.google.android.apps.gemini")
            memberName.contains("Kimi", ignoreCase = true) || memberName.contains("月之暗面", ignoreCase = true) -> listOf("com.moonshot.kimichat", "com.moonshot.kimi")
            memberName.contains("豆包", ignoreCase = true) -> listOf("com.larus.nova")
            else -> null
        }
        val installed = candidates?.firstOrNull { pkg ->
            try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
        }
        if (installed != null) {
            val appIcon = pm.getApplicationIcon(installed)
            drawableToBitmap(appIcon)?.let { return it }
        }
    } catch (_: Exception) { }

    // 2) 网络来源：使用 FaviconLoader 的 AI 引擎图标获取
    return try {
        FaviconLoader.getAIEngineBitmap(memberName)
    } catch (_: Exception) {
        null
    }
}

private fun buildGroupMemberNames(contact: com.example.aifloatingball.model.ChatContact): List<String> {
    // 1) 优先用显式成员
    if (!contact.aiMembers.isNullOrEmpty()) return contact.aiMembers

    // 2) 尝试从 customData 的 ai_service_types 恢复
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

    // 3) 从标题解析：群聊（A, B, C）
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
