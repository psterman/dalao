// 群聊删除和分组显示问题修复方案

/**
 * 问题分析：
 * 1. 群聊删除后重新出现的原因：showAllUserAIContacts方法重新从GroupChatManager加载群聊数据
 * 2. 分组显示混乱的原因：多个地方都在创建和管理分组，逻辑不一致
 * 
 * 修复方案：
 */

// 1. 修复showAllUserAIContacts方法
// 原问题：该方法重新从GroupChatManager加载群聊，导致已删除的群聊重新出现
// 解决方案：只从现有的allContacts中收集联系人，不重新加载

private fun showAllUserAIContacts() {
    try {
        // 收集所有分组中的联系人（AI和群聊），不重新从GroupChatManager加载
        val allContacts = mutableListOf<ChatContact>()

        this.allContacts.forEach { category ->
            if (category.name != "全部") {
                val validContacts = category.contacts.filter { contact ->
                    // 包含AI和群聊，排除提示性联系人
                    (contact.type == ContactType.AI || contact.type == ContactType.GROUP) &&
                    !contact.id.contains("hint") &&
                    !contact.id.contains("empty") &&
                    contact.name != "暂无AI助手" &&
                    contact.name != "AI助手分组为空"
                }
                allContacts.addAll(validContacts)
            }
        }
        
        Log.d(TAG, "从现有分组收集到 ${allContacts.size} 个联系人")

        // 按置顶状态和最后消息时间排序
        val sortedContacts = allContacts.distinctBy { it.id }.sortedWith(
            compareByDescending<ChatContact> { it.isPinned }
                .thenByDescending { it.lastMessageTime }
        )

        val displayCategories = if (sortedContacts.isNotEmpty()) {
            listOf(ContactCategory(
                name = "全部",
                contacts = sortedContacts.toMutableList(),
                isExpanded = true,
                isPinned = false
            ))
        } else {
            listOf(ContactCategory(
                name = "提示",
                contacts = mutableListOf(ChatContact(
                    id = "empty_hint",
                    name = "暂无联系人",
                    type = ContactType.AI,
                    description = "点击右上角+号添加AI助手或创建群聊",
                    isOnline = false,
                    lastMessage = "点击右上角+号添加联系人",
                    lastMessageTime = System.currentTimeMillis(),
                    unreadCount = 0,
                    avatar = "",
                    isPinned = false,
                    isMuted = false,
                    aiMembers = emptyList()
                )),
                isExpanded = true
            ))
        }

        chatContactAdapter?.updateContacts(displayCategories)
        Log.d(TAG, "显示全部联系人，共${sortedContacts.size}个")
    } catch (e: Exception) {
        Log.e(TAG, "显示全部联系人失败", e)
    }
}

// 2. 修复removeGroupChatConfiguration方法
// 添加更彻底的清理逻辑
private fun removeGroupChatConfiguration(contact: ChatContact) {
    try {
        // 1. 从GroupChatManager中删除群聊数据
        if (contact.groupId != null) {
            val deleted = groupChatManager.deleteGroupChat(contact.groupId!!)
            Log.d(TAG, "从GroupChatManager删除群聊: ${contact.groupId}, 结果: $deleted")
        }

        // 2. 从所有分组中移除该群聊联系人
        var contactRemoved = false
        val groupsToCheck = mutableListOf<String>()
        
        for (categoryIndex in allContacts.indices) {
            val category = allContacts[categoryIndex]
            val originalSize = category.contacts.size
            val updatedContacts = category.contacts.filter { it.id != contact.id }.toMutableList()
            
            if (updatedContacts.size != originalSize) {
                allContacts[categoryIndex] = category.copy(contacts = updatedContacts)
                contactRemoved = true
                Log.d(TAG, "从分组 ${category.name} 中移除群聊 ${contact.name}")

                // 检查是否需要移除空分组标签页
                if (updatedContacts.isEmpty() &&
                    category.name != "AI助手" &&
                    category.name != "全部" &&
                    category.name != "未分组") {
                    groupsToCheck.add(category.name)
                }
            }
        }

        if (!contactRemoved) {
            Log.w(TAG, "警告：未在任何分组中找到要删除的群聊 ${contact.name}")
        }

        // 3. 移除空分组的标签页
        groupsToCheck.forEach { groupName ->
            removeEmptyGroupTab(groupName)
            Log.d(TAG, "移除空分组标签页: $groupName")
        }

        // 4. 保存更改
        saveContacts()

        // 5. 强制刷新当前显示
        val chatTabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.chat_tab_layout)
        val currentTabPosition = chatTabLayout?.selectedTabPosition ?: 0
        val currentTab = chatTabLayout?.getTabAt(currentTabPosition)
        val tabText = currentTab?.text?.toString()
        
        // 根据当前标签页重新加载数据
        when (tabText) {
            "全部" -> showAllUserAIContacts()
            "AI助手" -> showAIAssistantGroup()
            else -> if (tabText != null && tabText != "+") {
                showCustomGroupContacts(tabText)
            }
        }

        // 6. 更新适配器
        chatContactAdapter?.updateContacts(allContacts)

        Log.d(TAG, "移除群聊配置成功: ${contact.name}")
        Toast.makeText(this, "✅ 群聊 ${contact.name} 已删除", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {
        Log.e(TAG, "移除群聊配置失败", e)
        Toast.makeText(this, "❌ 删除群聊失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// 3. 优化loadInitialContacts方法
// 避免重复加载群聊数据
private fun loadInitialContacts() {
    try {
        // 首先尝试从存储中恢复联系人数据
        val savedContacts = loadSavedContacts()
        if (savedContacts.isNotEmpty()) {
            allContacts = savedContacts.toMutableList()
            
            // 同步群聊数据：只添加新的群聊，不重复添加已存在的
            try {
                val groupChats = groupChatManager.getAllGroupChats()
                val existingGroupIds = allContacts.flatMap { it.contacts }
                    .filter { it.type == ContactType.GROUP }
                    .map { it.groupId }
                    .toSet()
                
                val newGroupChats = groupChats.filter { !existingGroupIds.contains(it.id) }
                
                if (newGroupChats.isNotEmpty()) {
                    val groupChatContacts = newGroupChats.map { groupChat ->
                        ChatContact(
                            id = groupChat.id,
                            name = groupChat.name,
                            avatar = groupChat.avatar,
                            type = ContactType.GROUP,
                            description = groupChat.description,
                            isOnline = true,
                            lastMessage = groupChat.lastMessage,
                            lastMessageTime = groupChat.lastMessageTime,
                            unreadCount = groupChat.unreadCount,
                            isPinned = groupChat.isPinned,
                            isMuted = groupChat.isMuted,
                            groupId = groupChat.id,
                            memberCount = groupChat.members.size,
                            aiMembers = groupChat.members.filter { it.type == MemberType.AI }.map { it.name }
                        )
                    }
                    
                    // 添加到第一个分组（通常是"全部"）
                    if (allContacts.isNotEmpty()) {
                        allContacts[0].contacts.addAll(groupChatContacts)
                    }
                    
                    Log.d(TAG, "同步了 ${newGroupChats.size} 个新群聊")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步群聊数据失败", e)
            }
            
            chatContactAdapter?.updateContacts(allContacts)
            Log.d(TAG, "从存储中恢复了联系人数据")
            return
        }

        // 如果没有保存的数据，则生成默认联系人
        generateDefaultContacts()

    } catch (e: Exception) {
        Log.e(TAG, "加载初始联系人数据失败", e)
        generateDefaultContacts()
    }
}

/**
 * 使用说明：
 * 1. 将上述修复代码替换SimpleModeActivity.kt中对应的方法
 * 2. 主要修复点：
 *    - showAllUserAIContacts不再重新加载群聊数据
 *    - removeGroupChatConfiguration添加更彻底的清理逻辑
 *    - loadInitialContacts避免重复加载群聊
 * 3. 这样可以确保删除的群聊不会重新出现，分组显示更加一致
 */