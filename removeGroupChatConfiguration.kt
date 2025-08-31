/**
     * 移除群聊配置
     */
    private fun removeGroupChatConfiguration(contact: ChatContact) {
        try {
            // 从GroupChatManager中删除群聊数据
            if (contact.groupId != null) {
                groupChatManager.deleteGroupChat(contact.groupId!!)
                Log.d(TAG, "从GroupChatManager删除群聊: ${contact.groupId}")
            }

            // 从所有分组中移除该群聊联系人
            val groupsToCheck = mutableListOf<String>()
            for (categoryIndex in allContacts.indices) {
                val category = allContacts[categoryIndex]
                val updatedContacts = category.contacts.filter { it.id != contact.id }.toMutableList()
                if (updatedContacts.size != category.contacts.size) {
                    allContacts[categoryIndex] = category.copy(contacts = updatedContacts)

                    // 检查是否需要移除空分组标签页
                    if (updatedContacts.isEmpty() &&
                        category.name != "AI助手" &&
                        category.name != "全部" &&
                        category.name != "未分组") {
                        groupsToCheck.add(category.name)
                    }
                }
            }

            // 移除空分组的标签页
            groupsToCheck.forEach { groupName ->
                removeEmptyGroupTab(groupName)
            }

            // 保存更改
            saveContacts()

            // 切换到"全部"标签页
            switchToTabIfExists("全部")

            // 刷新显示
            refreshCurrentTabDisplay()

            // 更新适配器
            chatContactAdapter?.updateContacts(allContacts)

            Log.d(TAG, "移除群聊配置成功: ${contact.name}")
            Toast.makeText(this, "✅ 群聊 ${contact.name} 已删除", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "移除群聊配置失败", e)
            Toast.makeText(this, "❌ 删除群聊失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }