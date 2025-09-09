# 简易模式DeepSeek对话同步调试指南

## 问题分析

从截图可以看到，DeepSeek联系人列表显示的还是"湖南消防发布的警示信息"相关的内容，而不是用户在简易模式中发送的"1"和AI回复。这说明简易模式中的对话没有正确同步到AI联系人列表。

## 调试步骤

### 1. 检查广播机制是否正常工作

在 `ChatActivity.kt` 的 `updateContactLastMessage` 方法中添加更详细的日志：

```kotlin
private fun updateContactLastMessage(contact: ChatContact, lastMessage: String) {
    try {
        Log.d(TAG, "开始更新联系人最后消息: ${contact.name} - ${lastMessage.take(50)}...")
        
        // 加载当前保存的联系人数据
        val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(KEY_SAVED_CONTACTS, null)
        if (json != null) {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<ContactCategory>>() {}.type
            val categories: List<ContactCategory> = gson.fromJson(json, type)

            // 查找并更新对应的联系人
            val updatedCategories = categories.map { category ->
                val updatedContacts = category.contacts.map { savedContact ->
                    if (savedContact.id == contact.id) {
                        Log.d(TAG, "找到匹配的联系人: ${savedContact.name} -> ${contact.name}")
                        savedContact.copy(
                            lastMessage = lastMessage,
                            lastMessageTime = System.currentTimeMillis()
                        )
                    } else {
                        savedContact
                    }
                }.toMutableList()
                category.copy(contacts = updatedContacts)
            }

            // 保存更新后的数据
            val updatedJson = gson.toJson(updatedCategories)
            prefs.edit().putString(KEY_SAVED_CONTACTS, updatedJson).apply()
            Log.d(TAG, "联系人最后消息已更新: ${contact.name}")
            
            // 发送广播通知简易模式更新数据
            notifySimpleModeUpdate(contact, lastMessage)
        } else {
            Log.w(TAG, "未找到保存的联系人数据")
        }
    } catch (e: Exception) {
        Log.e(TAG, "更新联系人最后消息失败", e)
    }
}
```

### 2. 检查广播接收器是否正常工作

在 `SimpleModeActivity.kt` 的 `aiMessageUpdateReceiver` 中添加更详细的日志：

```kotlin
private val aiMessageUpdateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            Log.d(TAG, "收到广播: ${intent?.action}")
            if (intent?.action == "com.example.aifloatingball.AI_MESSAGE_UPDATED") {
                val contactId = intent.getStringExtra("contact_id") ?: return
                val contactName = intent.getStringExtra("contact_name") ?: return
                val lastMessage = intent.getStringExtra("last_message") ?: return
                val lastMessageTime = intent.getLongExtra("last_message_time", System.currentTimeMillis())
                
                Log.d(TAG, "收到AI消息更新广播: $contactName ($contactId) - ${lastMessage.take(50)}...")
                
                // 更新联系人数据
                updateContactFromBroadcast(contactId, contactName, lastMessage, lastMessageTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理AI消息更新广播失败", e)
        }
    }
}
```

### 3. 检查联系人ID是否匹配

在 `updateContactFromBroadcast` 方法中添加日志：

```kotlin
private fun updateContactFromBroadcast(contactId: String, contactName: String, lastMessage: String, lastMessageTime: Long) {
    try {
        Log.d(TAG, "开始更新联系人数据: $contactName ($contactId)")
        
        // 在allContacts中找到对应的联系人并更新
        for (i in allContacts.indices) {
            val category = allContacts[i]
            val contactIndex = category.contacts.indexOfFirst { it.id == contactId }
            if (contactIndex != -1) {
                Log.d(TAG, "找到匹配的联系人: ${category.contacts[contactIndex].name} -> $contactName")
                
                val mutableContacts = category.contacts.toMutableList()
                val updatedContact = mutableContacts[contactIndex].copy(
                    lastMessage = lastMessage.take(50) + if (lastMessage.length > 50) "..." else "",
                    lastMessageTime = lastMessageTime
                )
                mutableContacts[contactIndex] = updatedContact
                allContacts[i] = category.copy(contacts = mutableContacts)
                
                // 通知适配器更新
                chatContactAdapter?.updateContacts(allContacts)
                Log.d(TAG, "联系人数据已更新: $contactName")
                return
            }
        }
        
        Log.w(TAG, "未找到匹配的联系人: $contactName ($contactId)")
    } catch (e: Exception) {
        Log.e(TAG, "更新联系人数据失败", e)
    }
}
```

## 可能的问题

### 1. 联系人ID不匹配
- `ChatActivity` 中使用的 `contact.id` 可能与 `SimpleModeActivity` 中的 `contactId` 不匹配
- 需要检查ID生成逻辑是否一致

### 2. 广播发送失败
- 广播可能没有正确发送
- 需要检查 `sendBroadcast` 是否成功

### 3. 广播接收失败
- 广播接收器可能没有正确注册
- 需要检查 `registerBroadcastReceiver` 是否被调用

### 4. 数据更新失败
- `updateContactFromBroadcast` 方法可能没有找到匹配的联系人
- 需要检查 `allContacts` 中的联系人ID格式

## 测试方法

1. **启动应用**，进入简易模式
2. **点击DeepSeek联系人**，进入对话界面
3. **发送消息**："测试消息"
4. **等待AI回复**完成
5. **查看日志**，确认广播发送和接收是否正常
6. **返回简易模式首页**，检查联系人列表是否更新

## 预期日志输出

如果一切正常，应该看到以下日志：

```
ChatActivity: 开始更新联系人最后消息: DeepSeek - 测试消息...
ChatActivity: 找到匹配的联系人: DeepSeek -> DeepSeek
ChatActivity: 联系人最后消息已更新: DeepSeek
ChatActivity: 已发送广播通知简易模式更新: DeepSeek
SimpleModeActivity: 收到广播: com.example.aifloatingball.AI_MESSAGE_UPDATED
SimpleModeActivity: 收到AI消息更新广播: DeepSeek (ai_deepseek) - 测试消息...
SimpleModeActivity: 开始更新联系人数据: DeepSeek (ai_deepseek)
SimpleModeActivity: 找到匹配的联系人: DeepSeek -> DeepSeek
SimpleModeActivity: 联系人数据已更新: DeepSeek
```

如果某个步骤的日志没有出现，说明该步骤有问题，需要进一步调试。
