# AI引擎激活和使用指南

## 概述

AI浮动球应用现在支持多个AI引擎的自定义对话页面，每个AI引擎都有独立的聊天环境和数据存储。

## 支持的AI引擎

### 1. 自定义HTML版本（推荐）
这些版本提供了优化的用户界面和完整的功能：

- **DeepSeek (API)** - `file:///android_asset/deepseek_chat.html`
- **ChatGPT (Custom)** - `file:///android_asset/chatgpt_chat.html`
- **Claude (Custom)** - `file:///android_asset/claude_chat.html`
- **通义千问 (Custom)** - `file:///android_asset/qianwen_chat.html`
- **智谱AI (Custom)** - `file:///android_asset/zhipu_chat.html`

### 2. 网页版本
直接访问官方网站：

- **ChatGPT (Web)** - `https://chat.openai.com/`
- **Claude (Web)** - `https://claude.ai/`
- **Gemini** - `https://gemini.google.com/`

## 激活方式

### 方法1：通过AI引擎设置
1. 打开应用设置
2. 进入"AI引擎管理"
3. 启用所需的AI引擎
4. 保存设置

### 方法2：通过DualFloatingWebViewService调用
```kotlin
// 启动ChatGPT自定义页面
val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
    putExtra("search_query", "你好")
    putExtra("engine_key", "chatgpt (custom)")
    putExtra("search_source", "用户输入")
}
context.startService(intent)

// 启动Claude自定义页面
val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
    putExtra("search_query", "Hello")
    putExtra("engine_key", "claude (custom)")
    putExtra("search_source", "用户输入")
}
context.startService(intent)
```

### 方法3：通过引擎键直接调用
支持的引擎键（不区分大小写）：

- `"deepseek (api)"` 或 `"deepseek_api"`
- `"chatgpt (custom)"` 或 `"chatgpt_custom"`
- `"claude (custom)"` 或 `"claude_custom"`
- `"通义千问 (custom)"` 或 `"qianwen_custom"`
- `"智谱ai (custom)"` 或 `"zhipu_custom"`

## API配置

### 必需的API密钥设置
每个自定义AI引擎都需要配置相应的API密钥：

#### ChatGPT
```kotlin
settingsManager.putString("chatgpt_api_key", "your-openai-api-key")
settingsManager.putString("chatgpt_api_url", "https://api.openai.com/v1/chat/completions")
```

#### Claude
```kotlin
settingsManager.putString("claude_api_key", "your-anthropic-api-key")
settingsManager.putString("claude_api_url", "https://api.anthropic.com/v1/messages")
```

#### 通义千问
```kotlin
settingsManager.putString("qianwen_api_key", "your-dashscope-api-key")
settingsManager.putString("qianwen_api_url", "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation")
```

#### 智谱AI
```kotlin
settingsManager.putString("zhipu_ai_api_key", "your-zhipu-api-key")
settingsManager.putString("zhipu_ai_api_url", "https://api.zhipu.ai/v1/chat/completions")
```

#### DeepSeek
```kotlin
settingsManager.putString("deepseek_api_key", "your-deepseek-api-key")
settingsManager.putString("deepseek_api_url", "https://api.deepseek.com/v1/chat/completions")
```

## 数据独立性

### 聊天记录分离
每个AI引擎的聊天记录完全独立：

- **DeepSeek**: `chat_sessions_deepseek`
- **ChatGPT**: `chat_sessions_chatgpt`
- **Claude**: `chat_sessions_claude`
- **通义千问**: `chat_sessions_qianwen`
- **智谱AI**: `chat_sessions_zhipu_ai`

### 收藏消息分离
收藏的消息也按AI引擎分别存储：

- **DeepSeek**: `favorite_messages_deepseek`
- **ChatGPT**: `favorite_messages_chatgpt`
- **Claude**: `favorite_messages_claude`
- **通义千问**: `favorite_messages_qianwen`
- **智谱AI**: `favorite_messages_zhipu_ai`

### 当前会话跟踪
每个AI引擎都有独立的当前会话ID：

- **DeepSeek**: `current_session_id_deepseek`
- **ChatGPT**: `current_session_id_chatgpt`
- **Claude**: `current_session_id_claude`
- **通义千问**: `current_session_id_qianwen`
- **智谱AI**: `current_session_id_zhipu_ai`

## 界面特色

### ChatGPT (Custom)
- **主题色**: 绿色 (#10A37F)
- **图标**: 🤖
- **特色**: OpenAI风格的界面设计

### Claude (Custom)
- **主题色**: 橙色 (#D97706)
- **图标**: 🧠
- **特色**: Anthropic风格的界面设计

### 通义千问 (Custom)
- **主题色**: 蓝色 (#1890FF)
- **图标**: 🌟
- **特色**: 阿里云风格的界面设计

### 智谱AI (Custom)
- **主题色**: 紫色 (#722ED1)
- **图标**: 🧩
- **特色**: 智谱风格的界面设计

### DeepSeek (API)
- **主题色**: 深蓝色 (#1E3A8A)
- **图标**: 🔮
- **特色**: DeepSeek风格的界面设计

## 功能特性

### 共同特性
所有自定义AI引擎页面都支持：

✅ **流式对话** - 实时显示AI回复
✅ **Markdown渲染** - 支持代码高亮、表格、列表等
✅ **聊天历史** - 自动保存和加载聊天记录
✅ **新对话** - 一键开始新的对话会话
✅ **响应式设计** - 适配不同屏幕尺寸
✅ **暗色模式** - 自动检测系统主题
✅ **错误处理** - 友好的错误提示和重试机制
✅ **API配置检查** - 自动检测API密钥配置状态

### 高级特性
- **数据持久化** - 聊天记录永久保存
- **会话管理** - 支持多个对话会话
- **跨模式共享** - 与简易模式共享聊天数据
- **安全存储** - API密钥安全存储

## 故障排除

### 常见问题

#### 1. AI引擎不显示
**解决方案**:
- 检查AI引擎是否在设置中启用
- 确认引擎键拼写正确
- 重启应用

#### 2. API调用失败
**解决方案**:
- 检查API密钥是否正确配置
- 验证API URL是否正确
- 检查网络连接

#### 3. 聊天记录丢失
**解决方案**:
- 检查存储权限
- 确认没有清除应用数据
- 查看对应AI引擎的数据存储

#### 4. 页面加载失败
**解决方案**:
- 检查HTML文件是否存在于assets目录
- 确认WebView权限
- 重新安装应用

## 开发者接口

### AIPageConfigManager
```kotlin
val configManager = AIPageConfigManager(context)

// 获取配置
val config = configManager.getConfigByKey("chatgpt (custom)")

// 验证API配置
val isValid = configManager.validateApiConfig("ChatGPT (Custom)")
```

### AndroidChatInterface
```kotlin
val chatInterface = AndroidChatInterface(
    context = context,
    webViewCallback = callback,
    aiServiceType = AIServiceType.CHATGPT
)
```

### ChatDataManager
```kotlin
val dataManager = ChatDataManager.getInstance(context)

// 为特定AI引擎开始新对话
val sessionId = dataManager.startNewChat(AIServiceType.CHATGPT)

// 添加消息
dataManager.addMessage(sessionId, "user", "Hello", AIServiceType.CHATGPT)

// 获取消息
val messages = dataManager.getMessages(sessionId, AIServiceType.CHATGPT)
```

## 更新日志

### v1.0.0
- ✅ 添加ChatGPT自定义页面
- ✅ 添加Claude自定义页面
- ✅ 添加通义千问自定义页面
- ✅ 添加智谱AI自定义页面
- ✅ 实现数据独立存储
- ✅ 支持多AI引擎切换
- ✅ 优化用户界面体验

---

**注意**: 使用自定义AI引擎需要相应的API密钥。请确保在使用前正确配置API密钥和端点URL。
