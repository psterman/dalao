# AI服务类型映射一致性分析

## 灵动岛中的AI服务映射

### getAIContactId方法 (DynamicIslandService.kt)
```kotlin
val aiName = when (serviceType) {
    AIServiceType.DEEPSEEK -> "DeepSeek"
    AIServiceType.CHATGPT -> "ChatGPT"
    AIServiceType.CLAUDE -> "Claude"
    AIServiceType.GEMINI -> "Gemini"
    AIServiceType.ZHIPU_AI -> "智谱AI"
    AIServiceType.WENXIN -> "文心一言"
    AIServiceType.QIANWEN -> "通义千问"
    AIServiceType.XINGHUO -> "讯飞星火"
    AIServiceType.KIMI -> "Kimi"
    else -> serviceType.name
}
```

### 生成的会话ID
- DeepSeek: `"ai_deepseek"`
- ChatGPT: `"ai_chatgpt"`
- Claude: `"ai_claude"`
- Gemini: `"ai_gemini"`
- 智谱AI: `"ai_智谱ai"`
- 文心一言: `"ai_文心一言"`
- 通义千问: `"ai_通义千问"`
- 讯飞星火: `"ai_讯飞星火"`
- Kimi: `"ai_kimi"`

## 简易模式中的AI服务映射

### ChatActivity.getAIServiceType方法
```kotlin
return when (contact.name.lowercase()) {
    "chatgpt", "gpt" -> AIServiceType.CHATGPT
    "claude" -> AIServiceType.CLAUDE
    "gemini" -> AIServiceType.GEMINI
    "文心一言", "wenxin" -> AIServiceType.WENXIN
    "deepseek" -> AIServiceType.DEEPSEEK
    "通义千问", "qianwen" -> AIServiceType.QIANWEN
    "讯飞星火", "xinghuo" -> AIServiceType.XINGHUO
    "kimi" -> AIServiceType.KIMI
    "智谱ai", "智谱清言", "zhipu", "glm" -> AIServiceType.ZHIPU_AI
    else -> null
}
```

### SimpleModeActivity.getAIServiceType方法
```kotlin
return when (contact.name.lowercase()) {
    "chatgpt", "gpt" -> AIServiceType.CHATGPT
    "claude" -> AIServiceType.CLAUDE
    "gemini" -> AIServiceType.GEMINI
    "文心一言", "wenxin" -> AIServiceType.WENXIN
    "deepseek" -> AIServiceType.DEEPSEEK
    "通义千问", "qianwen" -> AIServiceType.QIANWEN
    "讯飞星火", "xinghuo" -> AIServiceType.XINGHUO
    "kimi" -> AIServiceType.KIMI
    "智谱ai", "智谱清言", "zhipu", "glm" -> AIServiceType.ZHIPU_AI
    else -> null
}
```

## 映射一致性分析

### ✅ 完全匹配的AI服务
1. **DeepSeek**: 
   - 灵动岛: `"DeepSeek"` → `"ai_deepseek"`
   - 简易模式: `"deepseek"` → `AIServiceType.DEEPSEEK`
   - 状态: ✅ 匹配

2. **Kimi**: 
   - 灵动岛: `"Kimi"` → `"ai_kimi"`
   - 简易模式: `"kimi"` → `AIServiceType.KIMI`
   - 状态: ✅ 匹配

3. **智谱AI**: 
   - 灵动岛: `"智谱AI"` → `"ai_智谱ai"`
   - 简易模式: `"智谱ai"` → `AIServiceType.ZHIPU_AI`
   - 状态: ✅ 匹配

### ⚠️ 需要验证的AI服务
4. **ChatGPT**: 
   - 灵动岛: `"ChatGPT"` → `"ai_chatgpt"`
   - 简易模式: `"chatgpt", "gpt"` → `AIServiceType.CHATGPT`
   - 状态: ⚠️ 需要验证

5. **Claude**: 
   - 灵动岛: `"Claude"` → `"ai_claude"`
   - 简易模式: `"claude"` → `AIServiceType.CLAUDE`
   - 状态: ⚠️ 需要验证

6. **Gemini**: 
   - 灵动岛: `"Gemini"` → `"ai_gemini"`
   - 简易模式: `"gemini"` → `AIServiceType.GEMINI`
   - 状态: ⚠️ 需要验证

## 潜在问题

### 问题1: 大小写敏感性
- 灵动岛生成的ID使用小写: `"ai_deepseek"`
- 简易模式查找时也使用小写: `"deepseek"`
- 这应该是匹配的

### 问题2: 中文字符处理
- 智谱AI: 灵动岛生成 `"ai_智谱ai"`，简易模式查找 `"智谱ai"`
- 这应该是匹配的

### 问题3: 空格处理
- 灵动岛使用 `replace(" ", "_")` 处理空格
- 简易模式直接使用小写，没有空格处理
- 这应该是匹配的

## 建议的测试步骤

1. **测试DeepSeek同步**
2. **测试Kimi同步**
3. **测试智谱AI同步**
4. **测试ChatGPT同步**
5. **测试Claude同步**
6. **测试Gemini同步**

## 结论

从代码分析来看，所有AI服务的映射逻辑应该是一致的，但需要实际测试来验证。

