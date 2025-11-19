# AI搜索引擎设置适用范围说明

## 📋 问题回答

**设置中的"AI对话平台"、"AI搜索引擎"、"API对话"不仅仅是针对悬浮球模式调整，而是全局设置，影响所有使用AI引擎的模式。**

---

## 🔍 设置分类说明

在"AI搜索引擎管理"中，AI引擎被分为三个分类：

### 1. AI对话平台
- **定义**: 非API模式且非搜索引擎的AI对话平台
- **示例**: ChatGPT、Claude、Gemini、文心一言、通义千问、豆包、Kimi等
- **特点**: 网页版AI对话平台，通过WebView访问

### 2. AI搜索引擎
- **定义**: 非API模式且是搜索引擎的AI
- **示例**: Perplexity、Phind、天工AI、秘塔AI搜索、夸克AI、360AI搜索等
- **特点**: 具有搜索功能的AI平台

### 3. API对话
- **定义**: 使用API进行对话的AI引擎（isChatMode = true）
- **示例**: ChatGPT (API)、DeepSeek (API)、ChatGPT (Custom)、Claude (Custom)等
- **特点**: 需要配置API密钥，使用本地HTML界面

---

## 🌐 适用范围分析

### ✅ 全局设置

这些设置通过 `SettingsManager.getEnabledAIEngines()` 统一管理，是**全局设置**，影响以下所有模式：

#### 1. 悬浮球模式 (FloatingWindowService)
```kotlin
// 位置: FloatingWindowService.kt
val enabledAIEngineNames = settingsManager.getEnabledAIEngines()
val aiSearchEngines = AISearchEngine.DEFAULT_AI_ENGINES
    .filter { it.name in enabledAIEngineNames }
```
- **影响**: 悬浮球面板中显示的AI引擎列表
- **使用场景**: 点击悬浮球 → 打开搜索面板 → 显示AI搜索引擎

#### 2. 灵动岛模式 (DynamicIslandService)
```kotlin
// 位置: DynamicIslandService.kt (多处使用)
val enabledAIEngineNames = settingsManager.getEnabledAIEngines()
val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES
    .filter { enabledAIEngineNames.contains(it.name) }
```
- **影响**: 
  - 灵动岛搜索面板中的AI引擎列表
  - AI服务选择器中的可用引擎
  - 判断引擎是否为AI引擎（显示AI提示信息）
- **使用场景**: 
  - 点击灵动岛 → 打开搜索面板 → 显示AI搜索引擎
  - 复制文本 → 选择AI服务 → 显示已启用的AI引擎

#### 3. 双窗口服务 (DualFloatingWebViewService)
```kotlin
// 位置: DualFloatingWebViewService.kt
// 通过engine_key参数使用AI引擎
// 引擎是否启用由全局设置决定
```
- **影响**: 双窗口搜索时使用的AI引擎
- **使用场景**: 悬浮球或灵动岛启动双窗口搜索时

#### 4. 悬浮窗口管理器 (FloatingWindowManager)
```kotlin
// 位置: FloatingWindowManager.kt
val enabledAIEngineKeys = settingsManager.getEnabledAIEngines()
```
- **影响**: 全局AI搜索引擎栏的显示
- **使用场景**: 多窗口浏览器中的AI引擎选择

#### 5. 简易模式 (SimpleModeActivity)
- **影响**: 虽然代码中没有直接调用，但通过DualFloatingWebViewService间接使用
- **使用场景**: 简易模式中启动AI搜索时

#### 6. 桌面小组件 (Widget)
```kotlin
// 位置: CustomizableWidgetProvider.java
// 通过AI引擎设置判断是否为AI引擎
```
- **影响**: 小组件中AI引擎的显示和点击行为
- **使用场景**: 桌面小组件点击AI引擎图标时

---

## 📊 设置影响范围对比表

| 模式 | 是否使用AI引擎设置 | 使用位置 | 影响内容 |
|------|------------------|---------|---------|
| **悬浮球模式** | ✅ 是 | FloatingWindowService | 搜索面板中的AI引擎列表 |
| **灵动岛模式** | ✅ 是 | DynamicIslandService | 搜索面板、AI服务选择器 |
| **双窗口服务** | ✅ 是 | DualFloatingWebViewService | 双窗口搜索使用的引擎 |
| **简易模式** | ✅ 是（间接） | SimpleModeActivity | 通过双窗口服务使用 |
| **桌面小组件** | ✅ 是 | CustomizableWidgetProvider | AI引擎的显示和点击 |
| **多窗口浏览器** | ✅ 是 | FloatingWindowManager | 全局AI搜索引擎栏 |

---

## 🔧 设置存储位置

所有AI引擎的启用/禁用状态统一存储在：
- **存储键**: `enabled_ai_engines` (Set<String>)
- **管理类**: `SettingsManager`
- **方法**: 
  - `getEnabledAIEngines()` - 获取已启用的AI引擎
  - `saveEnabledAIEngines()` - 保存已启用的AI引擎

---

## 💡 使用建议

### 1. 统一管理
- ✅ **优点**: 一次设置，所有模式生效，方便管理
- ✅ **优点**: 避免在不同模式中重复配置
- ✅ **优点**: 设置一致，用户体验统一

### 2. 分类管理
- **AI对话平台**: 适合需要对话交互的场景
- **AI搜索引擎**: 适合需要搜索功能的场景
- **API对话**: 适合需要本地化、隐私保护的场景

### 3. 模式特定需求
如果某个模式需要不同的AI引擎配置，可以考虑：
- 在模式内部添加额外的过滤逻辑
- 使用模式特定的设置（但当前代码中未实现）

---

## 📝 总结

**结论**: 设置中的"AI对话平台"、"AI搜索引擎"、"API对话"是**全局设置**，不仅仅针对悬浮球模式，而是影响所有使用AI引擎的模式，包括：

1. ✅ 悬浮球模式
2. ✅ 灵动岛模式
3. ✅ 双窗口服务
4. ✅ 简易模式（间接）
5. ✅ 桌面小组件
6. ✅ 多窗口浏览器

**优势**: 
- 一次配置，全局生效
- 统一管理，避免重复设置
- 用户体验一致

**建议**: 
- 根据使用需求启用相应的AI引擎
- 不同分类的引擎可以同时启用
- 如果某个模式需要特殊配置，可以在该模式内部添加过滤逻辑






