# 群聊功能测试指南

## 测试环境准备

### 1. 确保API密钥配置
在测试群聊功能之前，请确保以下AI服务的API密钥已正确配置：
- DeepSeek API密钥
- ChatGPT API密钥（可选）
- Claude API密钥（可选）
- 其他AI服务API密钥（可选）

### 2. 检查依赖配置
确保项目依赖中包含：
- `kotlinx-coroutines-android`
- `kotlinx-coroutines-core`

## 基本功能测试

### 1. 创建群聊测试
```kotlin
// 在Activity或Fragment中测试
val groupChatManager = GroupChatManager.getInstance(this)
val groupChat = groupChatManager.createGroupChat(
    name = "测试群聊",
    description = "用于测试的AI助手群",
    aiMembers = listOf(AIServiceType.DEEPSEEK)
)

// 验证群聊创建成功
assert(groupChat.id.isNotEmpty())
assert(groupChat.members.size == 2) // 1个用户 + 1个AI
assert(groupChat.members.any { it.type == MemberType.USER })
assert(groupChat.members.any { it.type == MemberType.AI })
```

### 2. 发送消息测试
```kotlin
// 发送用户消息
val success = groupChatManager.sendUserMessage(groupChat.id, "你好，请介绍一下自己")
assert(success)

// 等待AI回复
// 注意：这是异步操作，需要等待一段时间
```

### 3. 获取群聊信息测试
```kotlin
// 获取所有群聊
val allGroups = groupChatManager.getAllGroupChats()
assert(allGroups.isNotEmpty())

// 获取特定群聊
val retrievedGroup = groupChatManager.getGroupChat(groupChat.id)
assert(retrievedGroup != null)
assert(retrievedGroup!!.name == "测试群聊")

// 获取群聊消息
val messages = groupChatManager.getGroupMessages(groupChat.id)
assert(messages.isNotEmpty())
```

## 高级功能测试

### 1. 多AI成员测试
```kotlin
// 创建包含多个AI的群聊
val multiAIGroup = groupChatManager.createGroupChat(
    name = "多AI群聊",
    description = "测试多个AI同时回复",
    aiMembers = listOf(
        AIServiceType.DEEPSEEK,
        AIServiceType.CHATGPT
    )
)

// 发送消息，观察多个AI的回复
groupChatManager.sendUserMessage(multiAIGroup.id, "请分别介绍一下你们各自的特点")
```

### 2. 群聊设置测试
```kotlin
// 测试群聊设置
val settings = GroupSettings(
    allowAllMembersReply = true,
    simultaneousReply = false, // 顺序回复
    replyDelay = 1000L, // 1秒延迟
    maxConcurrentReplies = 2
)

val updated = groupChatManager.updateGroupSettings(multiAIGroup.id, settings)
assert(updated)

// 验证设置生效
val updatedGroup = groupChatManager.getGroupChat(multiAIGroup.id)
assert(updatedGroup!!.settings.replyDelay == 1000L)
```

### 3. AI成员管理测试
```kotlin
// 添加新的AI成员
val added = groupChatManager.addAIMemberToGroup(
    multiAIGroup.id, 
    AIServiceType.CLAUDE
)
assert(added)

// 验证成员数量增加
val updatedGroup = groupChatManager.getGroupChat(multiAIGroup.id)
assert(updatedGroup!!.members.size == 4) // 1个用户 + 3个AI

// 移除AI成员
val removed = groupChatManager.removeAIMemberFromGroup(
    multiAIGroup.id, 
    AIServiceType.CLAUDE
)
assert(removed)
```

## 错误处理测试

### 1. API调用失败测试
```kotlin
// 使用无效的API密钥测试
// 这需要临时修改API密钥配置

// 发送消息，观察错误处理
groupChatManager.sendUserMessage(groupChat.id, "测试消息")

// 检查错误状态
val status = groupChatManager.getAIReplyStatus(groupChat.id)
val hasError = status.values.any { it.status == AIReplyStatus.ERROR }
assert(hasError)
```

### 2. 网络异常测试
```kotlin
// 在模拟器中关闭网络连接
// 发送消息，观察超时处理
groupChatManager.sendUserMessage(groupChat.id, "网络测试消息")

// 等待超时，检查状态
// 注意：这需要较长的等待时间
```

## 性能测试

### 1. 大量消息测试
```kotlin
// 发送多条消息
repeat(10) { index ->
    groupChatManager.sendUserMessage(
        groupChat.id, 
        "测试消息 $index"
    )
    delay(100) // 避免过快发送
}

// 检查消息数量
val messages = groupChatManager.getGroupMessages(groupChat.id)
assert(messages.size >= 10)
```

### 2. 并发测试
```kotlin
// 同时发送多条消息
val jobs = (1..5).map { index ->
    CoroutineScope(Dispatchers.IO).launch {
        groupChatManager.sendUserMessage(
            groupChat.id, 
            "并发消息 $index"
        )
    }
}

// 等待所有任务完成
jobs.forEach { it.join() }

// 验证所有消息都被处理
val messages = groupChatManager.getGroupMessages(groupChat.id)
assert(messages.size >= 15) // 5条并发消息 + 之前的消息
```

## 调试工具使用

### 1. 获取调试信息
```kotlin
// 获取群聊详细状态
val debugInfo = groupChatManager.getDebugInfo(groupChat.id)
Log.d("GroupChatTest", debugInfo)

// 输出示例：
// === 群聊调试信息 ===
// 群聊ID: xxx
// 群聊名称: 测试群聊
// 成员数量: 2
// AI成员数量: 1
// 消息数量: 5
// AI回复状态: 1
```

### 2. 数据验证
```kotlin
// 验证群聊数据完整性
val isValid = groupChatManager.validateGroupChatData(groupChat.id)
assert(isValid)
```

## 测试注意事项

### 1. 异步操作处理
- AI回复是异步的，需要等待
- 使用协程或回调处理异步结果
- 避免在测试中硬编码等待时间

### 2. 资源清理
- 测试完成后清理测试数据
- 调用`groupChatManager.cleanup()`释放资源
- 删除测试群聊：`groupChatManager.deleteGroupChat(groupId)`

### 3. 错误场景模拟
- 测试各种异常情况
- 验证错误处理逻辑
- 确保应用不会崩溃

### 4. 性能监控
- 监控内存使用情况
- 观察响应时间
- 检查是否有内存泄漏

## 常见问题排查

### 1. 编译错误
- 检查协程依赖是否正确配置
- 验证导入语句是否完整
- 确保Kotlin版本兼容

### 2. 运行时错误
- 检查API密钥配置
- 验证网络连接
- 查看日志输出

### 3. 功能异常
- 验证群聊数据是否正确保存
- 检查AI回复状态更新
- 确认消息流程是否完整

## 测试结果验证

### 成功标准
1. 群聊创建成功
2. 消息发送正常
3. AI回复正确
4. 错误处理有效
5. 性能表现良好

### 失败处理
1. 记录错误日志
2. 分析失败原因
3. 修复相关问题
4. 重新运行测试

通过以上测试，可以全面验证群聊功能的正确性和稳定性。
