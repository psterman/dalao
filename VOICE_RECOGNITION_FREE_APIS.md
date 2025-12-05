# 免费语音识别API升级方案

## 🎯 方案概述

为了提供更好的语音识别效果，我们支持多个免费的语音识别API。这些API相比系统自带的SpeechRecognizer，具有以下优势：

1. **识别准确率更高**：特别是中文识别
2. **支持实时流式识别**：可以逐词显示识别结果
3. **免费额度充足**：满足个人和小规模应用需求

## 📋 免费API对比

| API | 免费额度 | 中文识别 | 实时流式 | 配置难度 | 推荐度 |
|-----|---------|---------|---------|---------|--------|
| **SpeechRecognizer** | 无限 | ⭐⭐⭐ | ⭐⭐ | 无需配置 | ⭐⭐⭐⭐⭐ |
| **百度语音识别** | 每天5万次 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 简单 | ⭐⭐⭐⭐⭐ |
| **讯飞语音识别** | 每天500次 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 中等 | ⭐⭐⭐⭐ |
| **Google Cloud** | 每月60分钟 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 简单 | ⭐⭐⭐ |

## 🚀 快速开始

### 方案1：百度语音识别API（推荐）

**优势**：
- 免费额度最大（每天5万次）
- 中文识别效果好
- 支持实时流式识别
- 配置简单

**获取API Key步骤**：
1. 访问 [百度智能云](https://cloud.baidu.com/)
2. 注册/登录账号
3. 进入"产品服务" → "语音技术" → "短语音识别"
4. 创建应用，获取 `API Key` 和 `Secret Key`
5. 在应用设置中配置这两个密钥

**免费额度**：
- 每天5万次调用
- 单次最长60秒
- 完全免费，无需付费

### 方案2：讯飞语音识别API

**优势**：
- 中文识别准确率极高
- 支持实时流式识别
- 对中文方言支持好

**获取API Key步骤**：
1. 访问 [讯飞开放平台](https://www.xfyun.cn/)
2. 注册/登录账号
3. 进入"控制台" → "我的应用" → "创建新应用"
4. 选择"语音听写"服务
5. 获取 `AppID`、`API Key`、`API Secret`
6. 在应用设置中配置这三个参数

**免费额度**：
- 每天500次调用
- 单次最长60秒
- 适合个人使用

### 方案3：Google Cloud Speech-to-Text

**优势**：
- 识别准确率高
- 支持多语言
- 支持实时流式识别

**获取API Key步骤**：
1. 访问 [Google Cloud Console](https://console.cloud.google.com/)
2. 创建项目
3. 启用 "Speech-to-Text API"
4. 创建API密钥
5. 在应用设置中配置API Key

**免费额度**：
- 每月60分钟免费
- 超出后按使用量付费

## 🔧 配置方法

### 在应用中配置API密钥

1. 打开应用设置
2. 进入"语音识别设置"
3. 选择要使用的API（推荐百度）
4. 输入对应的API密钥
5. 保存配置

### 代码中使用

```kotlin
// 创建增强的语音识别器
val recognizer = EnhancedVoiceRecognizer(context)

// 设置回调
recognizer.setCallback(object : EnhancedVoiceRecognizer.RecognitionCallback {
    override fun onPartialResult(text: String) {
        // 实时显示部分识别结果（逐词显示）
        textView.text = text
    }
    
    override fun onFinalResult(text: String) {
        // 最终识别结果
        Log.d(TAG, "识别结果: $text")
    }
    
    override fun onError(error: String) {
        // 识别错误
        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    }
    
    override fun onStart() {
        // 识别开始
    }
    
    override fun onEnd() {
        // 识别结束
    }
})

// 开始识别（自动选择最佳方案）
recognizer.startRecognition(useCloudAPI = true)

// 停止识别
recognizer.stopRecognition()

// 释放资源
recognizer.release()
```

## 💡 使用建议

### 推荐策略

1. **默认使用SpeechRecognizer**（无需配置，兼容性好）
2. **配置百度API作为备选**（免费额度大，识别效果好）
3. **自动切换**：SpeechRecognizer失败时自动使用云API

### 最佳实践

- **个人使用**：配置百度API（每天5万次足够）
- **小团队使用**：配置多个API，自动切换
- **追求准确率**：优先使用讯飞API（虽然额度小，但准确率高）

## 📊 识别效果对比

| 场景 | SpeechRecognizer | 百度API | 讯飞API | Google Cloud |
|------|-----------------|---------|---------|--------------|
| 标准普通话 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 方言识别 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 嘈杂环境 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 实时流式 | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

## ⚠️ 注意事项

1. **网络要求**：云API需要网络连接
2. **隐私考虑**：语音数据会发送到服务器
3. **免费额度**：注意不要超出免费额度
4. **API密钥安全**：不要将API密钥提交到公开代码库

## 🔄 自动切换逻辑

应用会自动按以下优先级选择识别方案：

1. **已配置云API** → 使用云API（百度 > 讯飞 > Google Cloud）
2. **未配置云API** → 使用SpeechRecognizer
3. **SpeechRecognizer失败** → 自动切换到云API（如果已配置）
4. **所有方案失败** → 提示用户手动输入

## 📝 总结

- **SpeechRecognizer**：免费、无需配置、兼容性好，但识别效果一般
- **百度API**：免费额度大、中文识别好、推荐配置
- **讯飞API**：准确率高、但免费额度小
- **Google Cloud**：多语言支持好、但需要网络

**推荐方案**：配置百度API作为主要备选，SpeechRecognizer作为基础方案。










