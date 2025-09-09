# 广播机制调试测试

## 问题分析

简易模式中的DeepSeek对话没有同步到AI联系人列表，可能的原因：

1. **广播发送失败** - `ChatActivity` 中的 `notifySimpleModeUpdate` 方法没有正确发送广播
2. **广播接收失败** - `SimpleModeActivity` 中的广播接收器没有正确接收广播
3. **联系人ID不匹配** - `ChatActivity` 和 `SimpleModeActivity` 中使用的联系人ID格式不一致
4. **数据更新失败** - `updateContactFromBroadcast` 方法没有找到匹配的联系人

## 调试步骤

### 步骤1：检查广播发送

在 `ChatActivity` 的 `notifySimpleModeUpdate` 方法中添加更详细的日志：

```kotlin
private fun notifySimpleModeUpdate(contact: ChatContact, lastMessage: String) {
    try {
        Log.d(TAG, "准备发送广播通知简易模式更新: ${contact.name} (${contact.id})")
        
        val intent = Intent("com.example.aifloatingball.AI_MESSAGE_UPDATED").apply {
            putExtra("contact_id", contact.id)
            putExtra("contact_name", contact.name)
            putExtra("last_message", lastMessage)
            putExtra("last_message_time", System.currentTimeMillis())
        }
        
        Log.d(TAG, "广播Intent内容: action=${intent.action}, contact_id=${intent.getStringExtra("contact_id")}, contact_name=${intent.getStringExtra("contact_name")}")
        
        sendBroadcast(intent)
        Log.d(TAG, "已发送广播通知简易模式更新: ${contact.name}")
    } catch (e: Exception) {
        Log.e(TAG, "发送广播通知失败", e)
    }
}
```

### 步骤2：检查广播接收

在 `SimpleModeActivity` 的 `aiMessageUpdateReceiver` 中添加更详细的日志：

```kotlin
private val aiMessageUpdateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            Log.d(TAG, "收到广播: action=${intent?.action}")
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

### 步骤3：检查联系人ID匹配

在 `updateContactFromBroadcast` 方法中添加联系人ID对比日志：

```kotlin
private fun updateContactFromBroadcast(contactId: String, contactName: String, lastMessage: String, lastMessageTime: Long) {
    try {
        Log.d(TAG, "开始更新联系人数据: $contactName ($contactId)")
        
        // 打印所有可用的联系人ID
        Log.d(TAG, "当前allContacts中的联系人ID:")
        allContacts.forEach { category ->
            category.contacts.forEach { contact ->
                Log.d(TAG, "  - ${contact.name} (${contact.id})")
            }
        }
        
        // 在allContacts中找到对应的联系人并更新
        var found = false
        for (i in allContacts.indices) {
            val category = allContacts[i]
            val contactIndex = category.contacts.indexOfFirst { it.id == contactId }
            if (contactIndex != -1) {
                Log.d(TAG, "找到匹配的联系人: ${category.contacts[contactIndex].name} (${category.contacts[contactIndex].id})")
                found = true
                // ... 更新逻辑
                break
            }
        }
        
        if (!found) {
            Log.w(TAG, "未找到匹配的联系人: $contactName ($contactId)")
        }
    } catch (e: Exception) {
        Log.e(TAG, "更新联系人数据失败", e)
    }
}
```

## 测试方法

1. **启动应用**，进入简易模式
2. **点击DeepSeek联系人**，进入对话界面
3. **发送消息**："测试消息"
4. **等待AI回复**完成
5. **查看日志**，确认以下日志出现：
   ```
   ChatActivity: 准备发送广播通知简易模式更新: DeepSeek (ai_deepseek)
   ChatActivity: 广播Intent内容: action=com.example.aifloatingball.AI_MESSAGE_UPDATED, contact_id=ai_deepseek, contact_name=DeepSeek
   ChatActivity: 已发送广播通知简易模式更新: DeepSeek
   SimpleModeActivity: 收到广播: action=com.example.aifloatingball.AI_MESSAGE_UPDATED
   SimpleModeActivity: 收到AI消息更新广播: DeepSeek (ai_deepseek) - 测试消息...
   SimpleModeActivity: 当前allContacts中的联系人ID:
   SimpleModeActivity:   - DeepSeek (ai_deepseek)
   SimpleModeActivity: 找到匹配的联系人: DeepSeek (ai_deepseek)
   SimpleModeActivity: 已从广播更新联系人 DeepSeek 的最后消息: 测试消息...
   ```

## 可能的问题和解决方案

### 问题1：广播发送失败
**症状**：没有看到"已发送广播通知简易模式更新"的日志
**解决方案**：检查 `sendBroadcast` 是否成功执行

### 问题2：广播接收失败
**症状**：没有看到"收到广播"的日志
**解决方案**：检查广播接收器是否正确注册

### 问题3：联系人ID不匹配
**症状**：看到"未找到匹配的联系人"的日志
**解决方案**：检查联系人ID生成逻辑是否一致

### 问题4：数据更新失败
**症状**：看到"找到匹配的联系人"但没有更新
**解决方案**：检查 `updateContactFromBroadcast` 方法的更新逻辑
