# AI悬浮球 - 智能搜索助手

## 项目概述

AI悬浮球是一个Android智能搜索助手应用，提供三种核心显示模式：**悬浮球模式**、**灵动岛模式**和**简易模式**。应用集成了多种AI引擎和搜索引擎，支持语音识别、多窗口浮动WebView、应用内搜索等功能。

## 核心架构

### 三大核心服务

#### 1. SimpleModeService (简易模式服务)

**主要功能：**
- 全屏简易搜索界面，类似手机桌面launcher
- 12宫格AI引擎快速启动
- 应用内搜索图标集成（淘宝、抖音、小红书等20个应用）
- 语音搜索集成
- 最小化到边缘功能
- 搜索历史管理

**设计思路：**
- 采用全屏覆盖窗口，提供沉浸式搜索体验
- 宫格布局设计，支持AI引擎和应用搜索的快速切换
- 智能模式切换：可自动切换到DualFloatingWebViewService进行搜索
- 边缘最小化设计：支持拖拽到屏幕边缘最小化为小球

**核心组件：**
```kotlin
// 主要布局文件
- simple_mode_layout.xml: 全屏搜索界面
- 12个宫格项目支持AI引擎配置
- 底部Tab导航（首页、搜索、语音、个人）
- 顶部搜索栏和控制按钮
```

#### 2. DualFloatingWebViewService (双窗口浮动WebView服务)

**主要功能：**
- 多窗口浮动WebView（支持1-3个窗口）
- 搜索引擎管理和切换
- 窗口拖拽、缩放、贴边隐藏
- 文本选择和快捷操作
- 搜索历史记录
- 键盘自适应调整

**设计思路：**
- 采用WindowManager实现真正的系统级浮动窗口
- 多窗口架构：支持同时打开多个搜索引擎进行对比
- 智能窗口管理：自动保存窗口位置、大小状态
- 响应式设计：支持横竖屏切换和键盘弹出适配

**核心组件：**
```kotlin
// 主要管理器
- FloatingWindowManager: 浮动窗口生命周期管理
- WebViewManager: WebView实例管理和搜索执行
- TextSelectionManager: 文本选择和操作菜单
- CustomWebView: 自定义WebView组件
```

#### 3. DynamicIslandService (灵动岛服务)

**主要功能：**
- 仿iPhone灵动岛交互设计
- 紧凑态和展开态切换
- 搜索引擎卡槽配置
- 应用搜索图标显示
- 通知集成显示
- 助手身份选择

**设计思路：**
- 模仿iPhone灵动岛的交互模式
- 状态栏区域悬浮显示
- 智能展开收缩动画
- 支持拖拽配置搜索引擎

## UI设计风格

### 设计原则
- **Material Design 3**：遵循Google最新设计规范
- **深色/浅色主题**：支持系统主题跟随
- **圆角卡片设计**：大量使用MaterialCardView
- **毛玻璃效果**：部分界面采用半透明背景
- **动画流畅性**：所有交互都有平滑的过渡动画

### 颜色系统
```xml
<!-- 主要颜色 -->
<color name="colorPrimary">#4A90E2</color>
<color name="colorPrimaryDark">#357ABD</color>
<color name="colorAccent">#FF5722</color>

<!-- 浮动窗口颜色 -->
<color name="floating_window_background">#FFFFFF</color>
<color name="floating_text_primary">#212121</color>
<color name="floating_menu_text_secondary">#757575</color>
```

### 尺寸规范
```xml
<!-- 浮动窗口尺寸 -->
<dimen name="floating_window_min_width">200dp</dimen>
<dimen name="floating_window_min_height">300dp</dimen>
<dimen name="floating_window_corner_radius">16dp</dimen>

<!-- 灵动岛尺寸 -->
<dimen name="dynamic_island_compact_height">40dp</dimen>
<dimen name="dynamic_island_expanded_height">150dp</dimen>

<!-- 搜索引擎图标 -->
<dimen name="search_engine_icon_size">40dp</dimen>
```

### 动画效果
- **窗口展开/收缩**：使用AccelerateDecelerateInterpolator
- **边缘贴附**：弹簧动画效果
- **菜单显示**：淡入淡出 + 缩放动画
- **页面切换**：滑动过渡动画

## 设置选项规划

### 1. 通用设置
- **显示模式**：悬浮球/灵动岛/简易模式
- **主题模式**：跟随系统/浅色/深色
- **左手模式**：界面布局镜像翻转
- **剪贴板监听**：自动检测复制内容
- **自动粘贴**：搜索时自动填入剪贴板内容

### 2. 悬浮球设置
- **透明度调节**：0-100%可调
- **交互操作**：单击/长按/双击自定义
- **自动隐藏**：无操作时自动隐藏
- **贴边隐藏**：拖拽到边缘时自动隐藏

### 3. 搜索引擎设置
- **普通搜索引擎**：百度、Google、Bing等
- **AI搜索引擎**：ChatGPT、Claude、Gemini等50+引擎
- **搜索引擎分组**：自定义分组和排序
- **默认引擎**：设置默认搜索引擎

### 4. 灵动岛设置
- **交互操作**：单击/长按操作自定义
- **通知显示**：选择显示哪些应用通知
- **搜索引擎卡槽**：配置3个搜索引擎位置
- **助手身份**：选择AI助手的身份档案

### 5. AI助手设置
- **API配置**：ChatGPT、DeepSeek等API密钥
- **身份档案**：个人信息、兴趣爱好、健康状况等
- **回复格式**：简洁/详细/列表等格式偏好
- **拒绝话题**：设置不讨论的敏感话题

## 支持的AI引擎和搜索引擎

### AI引擎（50+）
**国际AI引擎：**
- ChatGPT、Claude、Gemini、Copilot
- Perplexity、Phind、Poe、You.com
- Brave Search、WolframAlpha、Groq

**国内AI引擎：**
- 文心一言、通义千问、讯飞星火、智谱清言
- 豆包、跃问、百小应、海螺、腾讯元宝
- 商量、天工AI、秘塔AI搜索、夸克AI
- 360AI搜索、百度AI、Kimi、DeepSeek

**开发者AI引擎：**
- DEVV、HuggingChat、Coze、Dify
- NotebookLM、Flowith、Monica

### 普通搜索引擎
- 百度、Google、Bing、搜狗、360搜索
- 必应中国、夸克、头条搜索
- 知乎、哔哩哔哩、豆瓣、微博

### 应用内搜索（20个应用）
- 购物：淘宝、京东、拼多多、天猫
- 社交：抖音、小红书、微博、知乎
- 视频：哔哩哔哩、爱奇艺、腾讯视频
- 生活：美团、饿了么、高德地图
- 其他：微信、QQ、支付宝等

---

## iOS版本开发指导

### 系统限制和解决方案

#### 1. 悬浮窗口限制
**Android特性：**
- 使用WindowManager实现系统级悬浮窗口
- 可在任何应用上方显示

**iOS解决方案：**
- **Picture-in-Picture (PiP)**：用于视频内容的悬浮显示
- **Widget**：主屏幕小组件，提供快速搜索入口
- **Shortcuts App**：创建快捷指令，通过Siri或控制中心触发
- **App Extensions**：Today Extension或Action Extension

#### 2. 后台运行限制
**Android特性：**
- 前台服务可长期运行
- 系统级窗口管理

**iOS解决方案：**
- **Background App Refresh**：有限的后台更新
- **Silent Push Notifications**：服务器推送触发
- **Background Processing**：短时间后台任务
- **Focus Modes**：集成系统专注模式

#### 3. 系统集成限制
**Android特性：**
- 可监听系统剪贴板
- 可拦截系统通知
- 可创建系统级覆盖窗口

**iOS解决方案：**
- **UIPasteboard**：读取剪贴板（需用户授权）
- **UserNotifications Framework**：处理本地通知
- **CallKit**：电话相关功能集成
- **SiriKit**：语音助手集成

### iOS开发架构建议

#### 1. 核心架构
```swift
// 主要组件
- SearchManager: 搜索引擎管理
- WebViewManager: WebView管理和搜索执行
- SettingsManager: 设置和配置管理
- VoiceManager: 语音识别和处理
- WidgetManager: 小组件管理
```

#### 2. UI适配要点

**灵动岛适配：**
```swift
// 检测设备是否支持Dynamic Island
if #available(iOS 16.1, *) {
    // iPhone 14 Pro系列的Dynamic Island适配
    // 使用ActivityKit创建Live Activities
}

// 状态栏适配
let statusBarHeight = UIApplication.shared.statusBarFrame.height
```

**多窗口支持：**
```swift
// iPad多窗口支持
if #available(iOS 13.0, *) {
    // Scene-based architecture
    // 支持多个搜索窗口同时显示
}
```

#### 3. 核心功能实现

**语音识别：**
```swift
import Speech

class VoiceRecognitionManager {
    private let speechRecognizer = SFSpeechRecognizer()
    private let audioEngine = AVAudioEngine()
    
    func startRecording() {
        // 实现语音识别逻辑
    }
}
```

**WebView管理：**
```swift
import WebKit

class WebViewManager: NSObject, WKNavigationDelegate {
    private var webViews: [WKWebView] = []
    
    func createWebView(for engine: SearchEngine) -> WKWebView {
        let webView = WKWebView()
        webView.navigationDelegate = self
        return webView
    }
}
```

**搜索引擎管理：**
```swift
struct SearchEngine {
    let name: String
    let url: String
    let searchURL: String
    let isAI: Bool
}

class SearchEngineManager {
    private var engines: [SearchEngine] = []
    
    func loadEngines() {
        // 加载搜索引擎配置
    }
}
```

#### 4. 权限处理

**麦克风权限：**
```swift
import AVFoundation

AVAudioSession.sharedInstance().requestRecordPermission { granted in
    if granted {
        // 允许录音
    }
}
```

**语音识别权限：**
```swift
import Speech

SFSpeechRecognizer.requestAuthorization { authStatus in
    switch authStatus {
    case .authorized:
        // 允许语音识别
    case .denied, .restricted, .notDetermined:
        // 处理权限拒绝
    @unknown default:
        break
    }
}
```

#### 5. 数据持久化

**Core Data配置：**
```swift
import CoreData

class DataManager {
    lazy var persistentContainer: NSPersistentContainer = {
        let container = NSPersistentContainer(name: "DataModel")
        container.loadPersistentStores { _, error in
            if let error = error {
                fatalError("Core Data error: \(error)")
            }
        }
        return container
    }()
}
```

**UserDefaults设置：**
```swift
class SettingsManager {
    private let userDefaults = UserDefaults.standard
    
    var defaultSearchEngine: String {
        get { userDefaults.string(forKey: "defaultSearchEngine") ?? "google" }
        set { userDefaults.set(newValue, forKey: "defaultSearchEngine") }
    }
}
```

### iOS UI设计要求

#### 1. 界面适配
- **iPhone适配**：支持所有iPhone尺寸（SE到Pro Max）
- **iPad适配**：支持Split View和Slide Over
- **Dynamic Island**：iPhone 14 Pro系列专属功能
- **刘海屏适配**：Safe Area布局

#### 2. 交互设计
- **手势识别**：滑动、长按、3D Touch/Haptic Touch
- **触觉反馈**：使用UIImpactFeedbackGenerator
- **语音交互**：集成Siri Shortcuts
- **键盘适配**：支持外接键盘快捷键

#### 3. 性能优化
- **内存管理**：避免内存泄漏，及时释放WebView
- **电池优化**：减少后台活动，优化网络请求
- **启动优化**：减少启动时间，延迟加载非必要组件

### 技术栈建议

#### 1. 开发框架
- **UIKit + SwiftUI**：混合开发，充分利用两者优势
- **Combine**：响应式编程，处理异步事件
- **Core Data**：数据持久化
- **CloudKit**：云端数据同步

#### 2. 网络和API
- **URLSession**：基础网络请求
- **Alamofire**：高级网络库（可选）
- **WebKit**：WebView组件
- **ChatGPT API**：AI对话功能

#### 3. UI组件
- **SnapKit**：Auto Layout约束库
- **Lottie**：动画效果
- **Kingfisher**：图片加载和缓存
- **Hero**：页面转场动画

### 开发注意事项

#### 1. App Store审核
- **避免私有API**：不使用未公开的系统API
- **内容审核**：确保搜索内容符合App Store指南
- **隐私政策**：明确说明数据收集和使用
- **功能描述**：准确描述应用功能

#### 2. 用户体验
- **无障碍支持**：VoiceOver、动态字体支持
- **多语言支持**：国际化和本地化
- **错误处理**：优雅的错误提示和恢复
- **离线功能**：部分功能支持离线使用

#### 3. 性能监控
- **Crashlytics**：崩溃报告收集
- **Analytics**：用户行为分析
- **Performance Monitoring**：性能监控
- **A/B Testing**：功能测试和优化

### 功能对比和实现策略

| Android功能 | iOS实现方案 | 实现难度 | 备注 |
|------------|------------|----------|------|
| 系统级悬浮窗口 | PiP + Widget | 困难 | 需要创新的交互方式 |
| 后台长期运行 | 后台刷新 + 推送 | 中等 | 功能有限制 |
| 剪贴板监听 | 主动读取 | 简单 | 需要用户授权 |
| 语音识别 | SFSpeechRecognizer | 简单 | 系统原生支持 |
| 多窗口WebView | WKWebView | 简单 | iPad支持更好 |
| 灵动岛功能 | Live Activities | 中等 | 仅限特定设备 |
| 应用内搜索 | URL Scheme | 中等 | 依赖第三方应用支持 |
| 设置管理 | UserDefaults | 简单 | 原生支持 |

### 开发里程碑

#### 阶段1：基础功能（4-6周）
- [ ] 搜索引擎管理
- [ ] 基础WebView功能
- [ ] 设置界面
- [ ] 语音识别

#### 阶段2：核心功能（6-8周）
- [ ] 多窗口支持
- [ ] AI引擎集成
- [ ] Widget小组件
- [ ] 数据同步

#### 阶段3：高级功能（4-6周）
- [ ] 灵动岛适配
- [ ] Siri集成
- [ ] 性能优化
- [ ] 用户体验优化

#### 阶段4：发布准备（2-3周）
- [ ] 测试和调试
- [ ] App Store准备
- [ ] 文档和帮助
- [ ] 营销材料

### 总结

iOS版本的开发需要在保持核心功能的同时，充分利用iOS系统的特性和限制。重点关注：

1. **创新交互方式**：用Widget、Shortcuts等替代Android的悬浮窗口
2. **系统集成**：充分利用Siri、Spotlight、Control Center等系统功能
3. **用户体验**：遵循iOS设计规范，提供原生的用户体验
4. **性能优化**：在系统限制下最大化应用性能和功能

通过合理的架构设计和功能适配，可以在iOS平台上实现一个功能丰富、用户体验优秀的智能搜索助手应用。 