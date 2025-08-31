# SimpleModeActivity 重构指南

## 重构目标
将 SimpleModeActivity 重构为使用 UnifiedGroupChatManager，实现事件驱动的数据同步，消除群聊数据管理混乱问题。

## 重构步骤

### 1. 导入新的管理器

```kotlin
import com.example.aifloatingball.manager.UnifiedGroupChatManager
import com.example.aifloatingball.manager.GroupChatDataChangeListener
import com.example.aifloatingball.manager.GroupChatEventType
```

### 2. 实现数据变更监听器接口

```kotlin
class SimpleModeActivity : AppCompatActivity(), GroupChatDataChangeListener {
    
    private lateinit var unifiedGroupChatManager: UnifiedGroupChatManager
    
    // ... 其他属性
}
```

### 3. 初始化统一管理器

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_simple_mode)
    
    // 初始化统一群聊管理器
    unifiedGroupChatManager = UnifiedGroupChatManager.getInstance(this)
    unifiedGroupChatManager.addDataChangeListener(this)
    
    // 其他初始化代码...
    
    // 加载初始联系人（重构后的版本）
    loadInitialContacts()
}
```

### 4. 重构 loadInitialContacts 方法

**原有问题：**
- `loadInitialContacts` 和 `generateDefaultContacts` 都从 `GroupChatManager` 加载群聊
- 导致数据重复和不一致

**重构后的解决方案：**

```kotlin
private fun loadInitialContacts() {
    Log.d(TAG, "开始加载初始联系人")
    
    // 清空现有联系人
    allContacts.clear()
    
    // 1. 从统一管理器加载群聊联系人（单一数据源）
    val groupChatContacts = unifiedGroupChatManager.getGroupChatContacts()
    allContacts.addAll(groupChatContacts)
    Log.d(TAG, "从统一管理器加载群聊联系人: ${groupChatContacts.size} 个")
    
    // 2. 加载AI联系人
    val aiContacts = generateAIContacts()
    allContacts.addAll(aiContacts)
    Log.d(TAG, "加载AI联系人: ${aiContacts.size} 个")
    
    // 3. 加载其他类型联系人
    // ... 其他联系人加载逻辑
    
    Log.d(TAG, "初始联系人加载完成，总计: ${allContacts.size} 个")
    
    // 刷新显示
    refreshCurrentTabDisplay()
}
```

### 5. 移除 generateDefaultContacts 中的重复逻辑

**原有问题：**
```kotlin
// 错误的重复加载
private fun generateDefaultContacts() {
    // ... 其他联系人
    
    // 重复加载群聊 - 需要移除
    val groupChats = groupChatManager.getAllGroupChats()
    groupChats.forEach { groupChat ->
        // 重复添加群聊联系人
    }
}
```

**重构后的解决方案：**
```kotlin
private fun generateDefaultContacts() {
    // 只生成非群聊的默认联系人
    // 群聊联系人由 loadInitialContacts 统一从 UnifiedGroupChatManager 加载
    
    // 生成AI联系人
    generateAIContacts()
    
    // 生成其他默认联系人
    // ... 其他联系人生成逻辑
    
    // 注意：不再在这里加载群聊联系人
}
```

### 6. 实现事件监听器方法

```kotlin
// 群聊创建事件
override fun onGroupChatCreated(groupChat: GroupChat) {
    Log.d(TAG, "收到群聊创建事件: ${groupChat.name}")
    
    val contact = ChatContact(
        id = "group_${groupChat.id}",
        name = groupChat.name,
        type = ContactType.GROUP_CHAT,
        groupId = groupChat.id,
        description = groupChat.description ?: "群聊",
        avatarResId = R.drawable.ic_group_chat
    )
    
    // 添加到联系人列表
    allContacts.add(contact)
    
    // 自动刷新显示
    refreshCurrentTabDisplay()
}

// 群聊删除事件
override fun onGroupChatDeleted(groupId: String) {
    Log.d(TAG, "收到群聊删除事件: $groupId")
    
    // 从联系人列表中移除
    val removedCount = allContacts.removeAll { it.groupId == groupId }
    Log.d(TAG, "移除了 $removedCount 个群聊联系人")
    
    // 自动刷新显示
    refreshCurrentTabDisplay()
}

// 群聊更新事件
override fun onGroupChatUpdated(groupChat: GroupChat) {
    Log.d(TAG, "收到群聊更新事件: ${groupChat.name}")
    
    // 查找并更新对应的联系人
    val index = allContacts.indexOfFirst { it.groupId == groupChat.id }
    if (index != -1) {
        val updatedContact = ChatContact(
            id = "group_${groupChat.id}",
            name = groupChat.name,
            type = ContactType.GROUP_CHAT,
            groupId = groupChat.id,
            description = groupChat.description ?: "群聊",
            avatarResId = R.drawable.ic_group_chat
        )
        allContacts[index] = updatedContact
        
        // 自动刷新显示
        refreshCurrentTabDisplay()
    }
}

// 群聊重新加载事件
override fun onGroupChatsReloaded(groupChats: List<GroupChat>) {
    Log.d(TAG, "收到群聊重新加载事件: ${groupChats.size} 个群聊")
    
    // 移除所有现有的群聊联系人
    allContacts.removeAll { it.type == ContactType.GROUP_CHAT }
    
    // 添加新的群聊联系人
    val newGroupChatContacts = groupChats.map { groupChat ->
        ChatContact(
            id = "group_${groupChat.id}",
            name = groupChat.name,
            type = ContactType.GROUP_CHAT,
            groupId = groupChat.id,
            description = groupChat.description ?: "群聊",
            avatarResId = R.drawable.ic_group_chat
        )
    }
    allContacts.addAll(newGroupChatContacts)
    
    // 自动刷新显示
    refreshCurrentTabDisplay()
}
```

### 7. 重构删除操作

**原有问题：**
```kotlin
// 原有的删除方法可能只删除了部分数据
private fun removeGroupChatConfiguration(groupId: String) {
    // 可能只删除了 allContacts 中的数据
    // 但没有删除 GroupChatManager 中的数据
}
```

**重构后的解决方案：**
```kotlin
private fun removeGroupChatConfiguration(groupId: String) {
    Log.d(TAG, "删除群聊配置: $groupId")
    
    // 使用统一管理器删除（会自动触发事件通知）
    val success = unifiedGroupChatManager.deleteGroupChat(groupId)
    
    if (success) {
        Log.d(TAG, "群聊删除成功: $groupId")
        // 事件监听器会自动处理界面更新，无需手动刷新
    } else {
        Log.w(TAG, "群聊删除失败: $groupId")
        // 可以显示错误提示
    }
}
```

### 8. 优化刷新逻辑

**原有问题：**
- `refreshCurrentTabDisplay` 可能导致数据不同步
- 多次调用可能导致性能问题

**重构后的解决方案：**
```kotlin
private fun refreshCurrentTabDisplay() {
    Log.d(TAG, "刷新当前标签页显示")
    
    // 由于使用了事件驱动机制，数据已经是最新的
    // 只需要更新UI显示即可
    
    when (currentTab) {
        "all" -> showAllUserAIContacts()
        "ai" -> showAIContacts()
        "group" -> showGroupChatContacts()
        // ... 其他标签页
    }
}

private fun showGroupChatContacts() {
    // 直接从 allContacts 中筛选群聊联系人
    val groupContacts = allContacts.filter { it.type == ContactType.GROUP_CHAT }
    
    // 更新适配器
    contactAdapter.updateContacts(groupContacts)
    
    Log.d(TAG, "显示群聊联系人: ${groupContacts.size} 个")
}
```

### 9. 资源清理

```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // 移除监听器，避免内存泄漏
    unifiedGroupChatManager.removeDataChangeListener(this)
    
    Log.d(TAG, "SimpleModeActivity 资源清理完成")
}
```

## 重构效果

### 解决的问题
1. **消除数据重复**：单一数据源确保群聊数据唯一性
2. **自动同步**：事件驱动机制确保界面与数据实时同步
3. **简化逻辑**：清晰的数据流向，减少复杂的同步逻辑
4. **提高可靠性**：统一的数据管理减少数据不一致的风险

### 关键改进
1. **单一数据源**：所有群聊数据操作通过 `UnifiedGroupChatManager`
2. **事件驱动**：数据变更自动通知界面更新
3. **职责分离**：数据管理与界面显示职责清晰分离
4. **错误处理**：完善的错误处理和日志记录

## 测试要点

1. **创建群聊**：验证新创建的群聊能立即在界面显示
2. **删除群聊**：验证删除后群聊从界面消失且数据完全清理
3. **更新群聊**：验证群聊信息更新后界面同步更新
4. **重启应用**：验证重启后群聊数据正确加载
5. **并发操作**：验证多个操作同时进行时数据一致性

## 注意事项

1. **渐进式迁移**：可以先保留原有的 `GroupChatManager`，逐步迁移到 `UnifiedGroupChatManager`
2. **数据迁移**：需要考虑从旧数据格式迁移到新数据格式
3. **向后兼容**：确保重构不影响现有功能
4. **性能优化**：事件通知机制要避免频繁刷新导致的性能问题